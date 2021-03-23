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
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.metrics.MetricDataParser;
import com.appdynamics.extensions.rabbitmq.metrics.MetricsCollector;
import com.appdynamics.extensions.rabbitmq.metrics.OptionalMetricsCollector;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.appdynamics.extensions.util.StringUtils;
import com.appdynamics.extensions.util.YmlUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.Phaser;

public class RabbitMQMonitorTask implements AMonitorTaskRunnable{

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(RabbitMQMonitorTask.class);

    private MonitorContextConfiguration configuration;

    private String displayName;

    /* server properties */
    private InstanceInfo instanceInfo;

    /* a facade to report metrics to the machine agent.*/
    private MetricWriteHelper metricWriter;

    private QueueGroup[] queueGroups;

    private String metricPrefix;


    private Map<String, String> endpointFlagsMap;



    RabbitMQMonitorTask(TasksExecutionServiceProvider serviceProvider, MonitorContextConfiguration configuration, InstanceInfo instanceInfo, QueueGroup[] queueGroups) {
        this.configuration = configuration;
        this.instanceInfo = instanceInfo;
        this.metricWriter = serviceProvider.getMetricWriteHelper();
        this.metricPrefix = configuration.getMetricPrefix() + "|" + instanceInfo.getDisplayName();
        this.endpointFlagsMap = (Map<String, String>) configuration.getConfigYml().get("endpointFlags");
        this.displayName = instanceInfo.getDisplayName();
        this.queueGroups = queueGroups;

    }


    public void run() {
        try {
            Phaser phaser = new Phaser();
            phaser.register();

            Stat.Stats metricConfig = (Stat.Stats) configuration.getMetricsXml();

            Map nodeFilters = (Map) YmlUtils.getNestedObject(configuration.getConfigYml(), "filter", "nodes");

            MetricDataParser dataParser = new MetricDataParser(metricPrefix + "|", configuration, nodeFilters);

            for(Stat stat: metricConfig.getStats()) {
                if(StringUtils.hasText(stat.getAlias()) && stat.getAlias().equalsIgnoreCase("Nodes")) {
                    phaser.register();

                    Map queueFilters = (Map) YmlUtils.getNestedObject(configuration.getConfigYml(), "filter", "queues");
                    MetricsCollector metricsCollectorTask = new MetricsCollector(stat, configuration.getContext(), instanceInfo, metricWriter,
                             endpointFlagsMap.get("overview"), dataParser, queueGroups, queueFilters, nodeFilters, phaser);
                    configuration.getContext().getExecutorService().execute("MetricCollectorTask", metricsCollectorTask);
                    logger.debug("Registering MetricCollectorTask phaser for {}", displayName);
                }else {
                    phaser.register();
                    OptionalMetricsCollector optionalMetricsCollectorTask = new OptionalMetricsCollector(stat, configuration.getContext(), instanceInfo, metricWriter, dataParser, endpointFlagsMap.get("federationPlugin"), phaser);
                    configuration.getContext().getExecutorService().execute("OptionalMetricsCollector", optionalMetricsCollectorTask);
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
