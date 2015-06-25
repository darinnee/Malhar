/*
 * Copyright (c) 2015 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datatorrent.lib.dimensions;

import com.datatorrent.api.Context.OperatorContext;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.api.Operator;
import com.datatorrent.api.Sink;
import com.datatorrent.lib.appdata.gpo.GPOUtils;
import com.datatorrent.lib.appdata.gpo.GPOUtils.IndexSubset;
import com.datatorrent.lib.appdata.schemas.DimensionalConfigurationSchema;
import com.datatorrent.lib.appdata.schemas.FieldsDescriptor;
import com.datatorrent.lib.dimensions.DimensionsEvent.Aggregate;
import com.datatorrent.lib.dimensions.DimensionsEvent.InputEvent;
import com.datatorrent.lib.dimensions.aggregator.AggregatorRegistry;
import com.datatorrent.lib.dimensions.aggregator.IncrementalAggregator;
import com.datatorrent.lib.statistics.DimensionsComputation;
import com.datatorrent.lib.statistics.DimensionsComputationUnifierImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.Serializable;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class GenericDimensionsComputationSingleSchema<EVENT> implements Operator
{
  /**
   * The default schema ID.
   */
  public static final int DEFAULT_SCHEMA_ID = 1;

  /**
   * This holds the JSON which defines the {@link DimensionalConfigurationSchema} to be used by this operator.
   */
  @NotNull
  private String configurationSchemaJSON;
  /**
   * The {@link DimensionalConfigurationSchema} to be used by this operator.
   */
  protected transient DimensionalConfigurationSchema configurationSchema;
  /**
   * The schemaID applied to {@link DimensionsEvent}s generated by this operator.
   */
  private int schemaID = DEFAULT_SCHEMA_ID;

  private DimensionsComputation<InputEvent, Aggregate> dimensionsComputation;

  /**
   * The {@link AggregatorRegistry} to use for this dimensions computation operator.
   */
  private AggregatorRegistry aggregatorRegistry = AggregatorRegistry.DEFAULT_AGGREGATOR_REGISTRY;

  private DimensionsComputationUnifierImpl<InputEvent, Aggregate> unifier;

  protected InputEvent inputEvent;

  /**
   * The output port for the aggregates.
   */
  public final transient DefaultOutputPort<Aggregate> output = new DefaultOutputPort<Aggregate>() {
    @Override
    public Unifier<Aggregate> getUnifier()
    {
      unifier.setAggregators(createAggregators());
      return unifier;
    }
  };

  /**
   * The input port which receives events to perform dimensions computation on.
   */
  public transient final DefaultInputPort<EVENT> input = new DefaultInputPort<EVENT>() {
    @Override
    public void process(EVENT tuple)
    {
      processInputEvent(tuple);
    }
  };

  public GenericDimensionsComputationSingleSchema()
  {
  }

  @Override
  @SuppressWarnings({"unchecked","rawtypes"})
  public void setup(OperatorContext context)
  {
    aggregatorRegistry.setup();

    configurationSchema =
    new DimensionalConfigurationSchema(configurationSchemaJSON,
                                       aggregatorRegistry);

    IncrementalAggregator[] aggregatorArray = createAggregators();

    dimensionsComputation = new DimensionsComputation<InputEvent, Aggregate>();
    dimensionsComputation.setUseAggregatesAsKeys(true);
    dimensionsComputation.setAggregators(aggregatorArray);

    Sink<Aggregate> sink = new Sink<Aggregate>() {

      @Override
      public void put(Aggregate tuple)
      {
        output.emit(tuple);
      }

      @Override
      public int getCount(boolean reset)
      {
        return 0;
      }
    };

    dimensionsComputation.output.setSink((Sink) sink);
    dimensionsComputation.setup(context);
  }

  private IncrementalAggregator[] createAggregators() throws RuntimeException
  {
    //Num incremental aggregators
    int numIncrementalAggregators = 0;

    FieldsDescriptor masterKeyFieldsDescriptor = configurationSchema.getKeyDescriptorWithTime();
    List<FieldsDescriptor> keyFieldsDescriptors = configurationSchema.getDimensionsDescriptorIDToKeyDescriptor();

    for(int dimensionsDescriptorID = 0;
        dimensionsDescriptorID < configurationSchema.getDimensionsDescriptorIDToAggregatorIDs().size();
        dimensionsDescriptorID++) {
      IntArrayList aggIDList = configurationSchema.getDimensionsDescriptorIDToAggregatorIDs().get(dimensionsDescriptorID);
      numIncrementalAggregators += aggIDList.size();
    }

    IncrementalAggregator[] aggregatorArray = new IncrementalAggregator[numIncrementalAggregators];
    int incrementalAggregatorIndex = 0;

    for(int dimensionsDescriptorID = 0;
        dimensionsDescriptorID < keyFieldsDescriptors.size();
        dimensionsDescriptorID++) {
      //Create the conversion context for the conversion.
      FieldsDescriptor keyFieldsDescriptor = keyFieldsDescriptors.get(dimensionsDescriptorID);
      Int2ObjectMap<FieldsDescriptor> map = configurationSchema.getDimensionsDescriptorIDToAggregatorIDToInputAggregatorDescriptor().get(dimensionsDescriptorID);
      IntArrayList aggIDList = configurationSchema.getDimensionsDescriptorIDToAggregatorIDs().get(dimensionsDescriptorID);
      DimensionsDescriptor dd = configurationSchema.getDimensionsDescriptorIDToDimensionsDescriptor().get(dimensionsDescriptorID);

      for(int aggIDIndex = 0;
          aggIDIndex < aggIDList.size();
          aggIDIndex++, incrementalAggregatorIndex++) {
        int aggID = aggIDList.get(aggIDIndex);

        DimensionsConversionContext conversionContext = new DimensionsConversionContext();
        IndexSubset indexSubsetKey = GPOUtils.computeSubIndices(keyFieldsDescriptor, masterKeyFieldsDescriptor);
        IndexSubset indexSubsetAggregate = GPOUtils.computeSubIndices(this.configurationSchema.getDimensionsDescriptorIDToAggregatorIDToInputAggregatorDescriptor().get
                                                                              (dimensionsDescriptorID).get(aggID),
                                                                      this.configurationSchema.getInputValuesDescriptor());

        indexSubsetKey.dd = dd;
        conversionContext.schemaID = schemaID;
        conversionContext.dimensionsDescriptorID = dimensionsDescriptorID;
        conversionContext.aggregatorID = aggID;

        conversionContext.dd = dd;
        conversionContext.keyDescriptor = keyFieldsDescriptor;
        conversionContext.aggregateDescriptor = map.get(aggID);
        conversionContext.inputTimestampIndex =
                masterKeyFieldsDescriptor.getTypeToFields().get(DimensionsDescriptor.DIMENSION_TIME_TYPE).indexOf(DimensionsDescriptor.DIMENSION_TIME);
        conversionContext.outputTimebucketIndex =
                keyFieldsDescriptor.getTypeToFields().get(DimensionsDescriptor.DIMENSION_TIME_BUCKET_TYPE).indexOf(DimensionsDescriptor.DIMENSION_TIME_BUCKET);
        conversionContext.outputTimestampIndex =
                keyFieldsDescriptor.getTypeToFields().get(DimensionsDescriptor.DIMENSION_TIME_TYPE).indexOf(DimensionsDescriptor.DIMENSION_TIME);

        IncrementalAggregator aggregator;

        try {
          aggregator = this.aggregatorRegistry.getIncrementalAggregatorIDToAggregator().get(aggID).getClass().newInstance();
        }
        catch(InstantiationException ex) {
          throw new RuntimeException(ex);
        }
        catch(IllegalAccessException ex) {
          throw new RuntimeException(ex);
        }

        aggregator.setDimensionsConversionContext(conversionContext);

        aggregator.setIndexSubsetKeys(indexSubsetKey);
        aggregator.setIndexSubsetAggregates(indexSubsetAggregate);

        aggregatorArray[incrementalAggregatorIndex] = aggregator;
      }
    }

    return aggregatorArray;
  }

  @Override
  public void beginWindow(long windowId)
  {
    dimensionsComputation.beginWindow(windowId);
  }

  @Override
  public void endWindow()
  {
    dimensionsComputation.endWindow();
  }

  @Override
  public void teardown()
  {
    dimensionsComputation.teardown();
  }

  public void processInputEvent(EVENT event) {
    convert(inputEvent, event);
    dimensionsComputation.data.put(inputEvent);
  }

  public abstract void convert(InputEvent inputEvent, EVENT event);

  /**
   * @return the aggregatorRegistry
   */
  public AggregatorRegistry getAggregatorRegistry()
  {
    return aggregatorRegistry;
  }

  /**
   * @param aggregatorRegistry the aggregatorRegistry to set
   */
  public void setAggregatorRegistry(AggregatorRegistry aggregatorRegistry)
  {
    this.aggregatorRegistry = aggregatorRegistry;
  }

  /**
   * @return the configurationSchemaJSON
   */
  public String getConfigurationSchemaJSON()
  {
    return configurationSchemaJSON;
  }

  /**
   * @param configurationSchemaJSON the configurationSchemaJSON to set
   */
  public void setConfigurationSchemaJSON(String configurationSchemaJSON)
  {
    this.configurationSchemaJSON = configurationSchemaJSON;
  }

  /**
   * @return the unifier
   */
  public DimensionsComputationUnifierImpl<InputEvent, Aggregate> getUnifier()
  {
    return unifier;
  }

  /**
   * @param unifier the unifier to set
   */
  public void setUnifier(DimensionsComputationUnifierImpl<InputEvent, Aggregate> unifier)
  {
    this.unifier = unifier;
  }

  public static class DimensionsConversionContext implements Serializable
  {
    private static final long serialVersionUID = 201506151157L;

    public int schemaID;
    public int dimensionsDescriptorID;
    public int aggregatorID;
    /**
     * The {@link DimensionsDescriptor} corresponding to the given dimension descriptor id.
     */
    public DimensionsDescriptor dd;
    /**
     * The {@link FieldsDescriptor} for the aggregate of a new {@link InputEvent}.
     */
    public FieldsDescriptor aggregateDescriptor;
    public FieldsDescriptor keyDescriptor;

    public int inputTimestampIndex;
    public int outputTimestampIndex;
    public int outputTimebucketIndex;

    /**
     * Constructor for creating conversion context.
     */
    public DimensionsConversionContext()
    {
      //Do nothing.
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(GenericDimensionsComputationSingleSchema.class);
}
