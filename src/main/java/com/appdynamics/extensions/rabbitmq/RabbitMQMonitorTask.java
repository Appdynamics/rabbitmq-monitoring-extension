/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.metrics.MetricDataParser;
import com.appdynamics.extensions.rabbitmq.metrics.MetricsCollector;
import com.appdynamics.extensions.rabbitmq.metrics.OptionalMetricsCollector;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.appdynamics.extensions.util.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Phaser;

public class RabbitMQMonitorTask implements AMonitorTaskRunnable{

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RabbitMQMonitorTask.class);

    private MonitorConfiguration configuration;

    private String displayName;

    /* server properties */
    private InstanceInfo instanceInfo;

    /* a facade to report metrics to the machine agent.*/
    private MetricWriteHelper metricWriter;

    private QueueGroup[] queueGroups;

    private String metricPrefix;


    private Map<String, String> endpointFlagsMap;



    RabbitMQMonitorTask(TasksExecutionServiceProvider serviceProvider, InstanceInfo instanceInfo, QueueGroup[] queueGroups) {
        this.configuration = serviceProvider.getMonitorConfiguration();
        this.instanceInfo = instanceInfo;
        this.metricWriter = serviceProvider.getMetricWriteHelper();
        this.metricPrefix = configuration.getMetricPrefix();
        this.endpointFlagsMap = (Map<String, String>) configuration.getConfigYml().get("endpointFlags");
        this.displayName = instanceInfo.getDisplayName();
        this.queueGroups = queueGroups;

    }


    public void run() {
        try {
            Phaser phaser = new Phaser();

            Stat.Stats metricConfig = (Stat.Stats) configuration.getMetricsXmlConfiguration();

            MetricDataParser dataParser = new MetricDataParser(metricPrefix + "|", configuration);

            for(Stat stat: metricConfig.getStats()) {
                if(StringUtils.hasText(stat.getAlias()) && stat.getAlias().equalsIgnoreCase("Nodes")) {
                    phaser.register();
                    MetricsCollector metricsCollectorTask = new MetricsCollector(stat, configuration, instanceInfo, metricWriter,
                             endpointFlagsMap.get("overview"), dataParser, queueGroups, phaser);
                    configuration.getExecutorService().execute("MetricCollectorTask", metricsCollectorTask);
                    logger.debug("Registering MetricCollectorTask phaser for {}", displayName);
                }else {
                    phaser.register();
                    OptionalMetricsCollector optionalMetricsCollectorTask = new OptionalMetricsCollector(stat, configuration, instanceInfo, metricWriter, dataParser, endpointFlagsMap.get("federationPlugin"), phaser);
                    configuration.getExecutorService().execute("OptionalMetricsCollector", optionalMetricsCollectorTask);
                    logger.debug("Registering OptionalMetricCollectorTask phaser for {}", displayName);
                }
            }
            //Wait for all tasks to finish
            phaser.arriveAndAwaitAdvance();
            logger.info("Completed the RabbitMQ Metric Monitoring task");
        } catch (Exception e) {
            logger.error("Unexpected error while running the RabbitMQ Monitor", e);
        }

    }

    public void onTaskComplete() {
        logger.info("All tasks for server {} finished", displayName);
    }

}
