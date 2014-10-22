/*
 *  Copyright (c) 2012-2014 Malhar, Inc.
 *  All Rights Reserved.
 */

package com.datatorrent.contrib.goldengate.app;

import java.util.Properties;

import org.apache.hadoop.conf.Configuration;

import com.datatorrent.lib.io.ConsoleOutputOperator;

import com.datatorrent.contrib.goldengate.GoldenGateQueryProcessor;
import com.datatorrent.contrib.goldengate.KafkaJsonEncoder;
import com.datatorrent.contrib.goldengate.lib.KafkaInput;
import com.datatorrent.contrib.goldengate.lib.OracleDBOutputOperator;
import com.datatorrent.contrib.kafka.KafkaSinglePortOutputOperator;
import com.datatorrent.contrib.kafka.KafkaSinglePortStringInputOperator;

import com.datatorrent.api.DAG;
import com.datatorrent.api.StreamingApplication;
import com.datatorrent.api.annotation.ApplicationAnnotation;

@ApplicationAnnotation(name="GoldenGateDemo")
public class GoldenGateApp implements StreamingApplication
{
  @Override
  public void populateDAG(DAG dag, Configuration conf)
  {
    KafkaInput kafkaInput = new KafkaInput();
    dag.addOperator("kafkaInput", kafkaInput);

    ////

    OracleDBOutputOperator db = new OracleDBOutputOperator();
    dag.addOperator("oracledb", db);

    ////

    ConsoleOutputOperator console = new ConsoleOutputOperator();
    dag.addOperator("console", console);

    ////

    dag.addStream("display", kafkaInput.outputPort, console.input);
    dag.addStream("inputtodb", kafkaInput.employeePort, db.input);

    ////

    KafkaSinglePortStringInputOperator queryInput = dag.addOperator("QueryInput", KafkaSinglePortStringInputOperator.class);

    //

    GoldenGateQueryProcessor queryProcessor = dag.addOperator("QueryProcessor", GoldenGateQueryProcessor.class);

    //

    KafkaSinglePortOutputOperator<Object, Object> queryOutput = dag.addOperator("QueryResult", new KafkaSinglePortOutputOperator<Object, Object>());

    Properties configProperties = new Properties();
    configProperties.setProperty("serializer.class", KafkaJsonEncoder.class.getName());
    configProperties.setProperty("metadata.broker.list", "node25.morado.com:9092");
    queryOutput.setConfigProperties(configProperties);

    dag.addStream("queries", queryInput.outputPort, queryProcessor.queryInput);
    dag.addStream("results", queryProcessor.queryOutput, queryOutput.inputPort);
  }
}