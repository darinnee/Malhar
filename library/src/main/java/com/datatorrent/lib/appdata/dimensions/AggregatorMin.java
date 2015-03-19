/*
 *  Copyright (c) 2012-2015 Malhar, Inc.
 *  All Rights Reserved.
 */

package com.datatorrent.lib.appdata.dimensions;

import com.datatorrent.lib.appdata.gpo.GPOMutable;

/**
 *
 * @author Timothy Farkas: tim@datatorrent.com
 */
public class AggregatorMin implements DimensionsAggregator<GenericAggregateEvent>
{
  public AggregatorMin()
  {
  }

  @Override
  public void aggregate(GenericAggregateEvent dest, GenericAggregateEvent src)
  {
    aggregate(dest.getAggregates(), src.getAggregates());

    GPOMutable destGPO = dest.getAggregates();
    GPOMutable srcGPO = src.getAggregates();

    for(String field: destGPO.getFieldDescriptor().getFields().getFields()) {
      Object destObj = destGPO.getField(field);
      Object srcObj = srcGPO.getField(field);

      if(!srcObj.getClass().equals(destObj)) {
        throw new UnsupportedOperationException("Cannot aggregate different types.");
      }
      else if(srcObj instanceof Byte) {
        Byte srcObjTemp = (Byte) srcObj;
        Byte destObjTemp = (Byte) destObj;

        Byte res = srcObjTemp < destObjTemp? srcObjTemp: destObjTemp;
        destGPO.setField(field, res);
      }
      else if(srcObj instanceof Short) {
        Short srcObjTemp = (Short) srcObj;
        Short destObjTemp = (Short) destObj;

        Short res = srcObjTemp < destObjTemp? srcObjTemp: destObjTemp;
        destGPO.setField(field, res);
      }
      else if(srcObj instanceof Integer) {
        Integer srcObjTemp = (Integer) srcObj;
        Integer destObjTemp = (Integer) destObj;

        Integer res = srcObjTemp < destObjTemp? srcObjTemp: destObjTemp;
        destGPO.setField(field, res);
      }
      else if(srcObj instanceof Long) {
        Long srcObjTemp = (Long) srcObj;
        Long destObjTemp = (Long) destObj;

        Long res = srcObjTemp < destObjTemp? srcObjTemp: destObjTemp;
        destGPO.setField(field, res);
      }
      else if(srcObj instanceof Float) {
        Float srcObjTemp = (Float) srcObj;
        Float destObjTemp = (Float) destObj;

        Float res = srcObjTemp < destObjTemp? srcObjTemp: destObjTemp;
        destGPO.setField(field, res);
      }
      else if(srcObj instanceof Double) {
        Double srcObjTemp = (Double) srcObj;
        Double destObjTemp = (Double) destObj;

        Double res = srcObjTemp < destObjTemp? srcObjTemp: destObjTemp;
        destGPO.setField(field, res);
      }
      else {
        throw new UnsupportedOperationException("Sum is not supported on object of type: " +
                                                srcObj.getClass());
      }
    }
  }

  private void aggregate(GPOMutable dest, GPOMutable src)
  {
  }
}
