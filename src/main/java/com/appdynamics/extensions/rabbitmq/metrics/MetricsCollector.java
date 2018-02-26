/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class MetricsCollector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private MonitorConfiguration configuration;

    private InstanceInfo instanceInfo;

    private MetricWriteHelper metricWriteHelper;

    private List<Metric> metrics;

    private List<Map<String, List<Map<String, String>>>> metricsFromConfig;

    private MetricsCollectorUtil metricsCollectorUtil = new MetricsCollectorUtil();

    private String overviewMetricFlag;

    private MetricDataParser dataParser;

    public void setMetricsCollectorUtil(MetricsCollectorUtil metricsCollectorUtil) {
        this.metricsCollectorUtil = metricsCollectorUtil;
    }

    public MetricsCollector(MonitorConfiguration configuration, InstanceInfo instanceInfo, MetricWriteHelper metricWriteHelper,
                            List<Metric> metrics, List<Map<String, List<Map<String, String>>>> metricsFromConfig, String overviewMetricFlag, MetricDataParser dataParser)
    {
        this.configuration = configuration;
        this.instanceInfo = instanceInfo;
        this.metricWriteHelper = metricWriteHelper;
        this.metrics = metrics;
        this.metricsFromConfig = metricsFromConfig;
        this.overviewMetricFlag = overviewMetricFlag;
        this.dataParser = dataParser;
    }


    public void run() {

        String nodeUrl = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path("/api/nodes").build();
        ArrayNode nodes = metricsCollectorUtil.getJson(this.configuration.getHttpClient(), nodeUrl);

        String channelUrl = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path("/api/channels").build();
        ArrayNode channels = metricsCollectorUtil.getJson(this.configuration.getHttpClient(), channelUrl);

        String apiUrl = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path("/api/queues").build();
        ArrayNode queues = metricsCollectorUtil.getJson(this.configuration.getHttpClient(), apiUrl);

        metricsCollectorUtil.populateMetricsMap(dataParser.getAllMetricsFromConfig(), metricsFromConfig);
        metrics.addAll(dataParser.process(nodes, channels, queues));

        if(overviewMetricFlag.equalsIgnoreCase("true")) {

            String overviewUrl = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path("/api/overview").build();
            JsonNode overview = metricsCollectorUtil.getOptionalJson(this.configuration.getHttpClient(), overviewUrl, JsonNode.class);
            metrics.addAll(dataParser.parseOverviewData(overview, nodes));
        }

        if (metrics != null && metrics.size() > 0) {
            logger.debug("Printing Node, Queue, Channel & Overview metrics: " + metrics.size());
            metricWriteHelper.transformAndPrintMetrics(metrics);
        }
    }
}
