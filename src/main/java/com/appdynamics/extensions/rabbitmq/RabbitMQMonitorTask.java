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
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.instance.Instances;
import com.appdynamics.extensions.rabbitmq.metrics.FederationMetricsCollector;
import com.appdynamics.extensions.rabbitmq.metrics.MetricDataParser;
import com.appdynamics.extensions.rabbitmq.metrics.MetricsCollector;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.google.common.collect.Lists;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashMap;
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

    private Map<String, List<Map<String, String>>> allMetricsFromConfig;

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
        allMetricsFromConfig = new HashMap<String, List<Map<String, String>>>();
    }


    public void run() {
        try {

            MetricDataParser dataParser = new MetricDataParser(allMetricsFromConfig, metricPrefix + " | ", queueGroups, excludeQueueRegex);

            MetricsCollector metricsCollectorTask = new MetricsCollector(configuration, instanceInfo, metricWriter,metrics,
                                                                            metricsFromConfig, endpointFlagsMap.get("overview"), dataParser);

            configuration.getExecutorService().execute("MetricCollectorTask",metricsCollectorTask);

            if(endpointFlagsMap.get("federationPlugin").equalsIgnoreCase("true")) {

                FederationMetricsCollector fedMetricCollectorTask = new FederationMetricsCollector(configuration, instanceInfo, metricWriter, metrics, dataParser);
                configuration.getExecutorService().execute("FederationMetricsCollector",fedMetricCollectorTask);

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


    protected ArrayNode getJson(CloseableHttpClient client, String url) {
        HttpGet get = new HttpGet(url);
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode json = null;
        try {
            json = mapper.readValue(EntityUtils.toString(client.execute(get).getEntity()),ArrayNode.class);
        }  catch (Exception e) {
            logger.error("Error while fetching the " + url + " data, returning " + json, e);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("The url " + url + " responded with a json {}" + json);
        }
        return json;
    }

    protected  <T> T getOptionalJson(CloseableHttpClient client, String url, Class<T> clazz) {
        try {
            HttpGet get = new HttpGet(url);
            ObjectMapper mapper = new ObjectMapper();
            T json = mapper.readValue(EntityUtils.toString(client.execute(get).getEntity()),clazz);
            if (logger.isDebugEnabled()) {
                logger.debug("The url " + url + " responded with a json " + json);
            }
            return json;
        } catch (Exception ex) {
            logger.error("Error while fetching the '/api/federation-links' data, returning NULL", ex);
            return null;
        }
    }

}
