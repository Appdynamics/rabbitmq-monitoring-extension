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
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.instance.Instances;
import com.appdynamics.extensions.rabbitmq.metrics.OptionalMetricsCollector;
import com.appdynamics.extensions.rabbitmq.metrics.MetricDataParser;
import com.appdynamics.extensions.rabbitmq.metrics.MetricsCollector;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.appdynamics.extensions.util.StringUtils;
import com.google.common.collect.Lists;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

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

    private String excludeQueueRegex;

    private List<Map<String, List<Map<String, String>>>> metricsFromConfig;

    private List<Metric> metrics;

    private Map<String, String> endpointFlagsMap;

    public void setMetrics(List<Metric> metrics) {
        this.metrics = metrics;
    }


    RabbitMQMonitorTask(TasksExecutionServiceProvider serviceProvider, InstanceInfo instanceInfo, Instances instances) {
        this.configuration = serviceProvider.getMonitorConfiguration();
        this.instanceInfo = instanceInfo;
        this.metricWriter = serviceProvider.getMetricWriteHelper();
        this.metricPrefix = configuration.getMetricPrefix();
        this.excludeQueueRegex = instances.getExcludeQueueRegex();
        this.metricsFromConfig = (List<Map<String, List<Map<String, String>>>>) configuration.getConfigYml().get("metrics");
        this.endpointFlagsMap = (Map<String, String>) configuration.getConfigYml().get("endpointFlags");
        this.displayName = instanceInfo.getDisplayName();
        this.metrics = Lists.newArrayList();
        this.queueGroups = instances.getQueueGroups();
    }


    public void run() {
        try {

            Stat.Stats metricConfig = (Stat.Stats) configuration.getMetricsXmlConfiguration();

            MetricDataParser dataParser = new MetricDataParser(metricPrefix + "|", configuration);

            for(Stat stat: metricConfig.getStats()) {

                if(StringUtils.hasText(stat.getAlias()) && stat.getAlias().equalsIgnoreCase("Nodes")) {
                    MetricsCollector metricsCollectorTask = new MetricsCollector(stat, configuration, instanceInfo, metricWriter,
                             endpointFlagsMap.get("overview"), dataParser, queueGroups);


                    configuration.getExecutorService().execute("MetricCollectorTask", metricsCollectorTask);
                }else {
                    logger.debug("Stat: " +stat.getAlias());
                    OptionalMetricsCollector optionalMetricsCollectorTask = new OptionalMetricsCollector(stat, configuration, instanceInfo, metricWriter, dataParser);
                    configuration.getExecutorService().execute("OptionalMetricsCollector", optionalMetricsCollectorTask);
                }
            }

            logger.info("Completed the RabbitMQ Metric Monitoring task");
        } catch (Exception e) {
            metrics.add(new Metric("Availability", String.valueOf(BigInteger.ZERO), metricPrefix + "Availability"));
            logger.error("Unexpected error while running the RabbitMQ Monitor", e);
        }

    }

    public void onTaskComplete() {
        logger.info("All tasks for server {} finished", displayName);
    }

}
