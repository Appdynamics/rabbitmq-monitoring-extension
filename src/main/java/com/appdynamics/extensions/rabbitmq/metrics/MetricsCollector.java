/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;


public class MetricsCollector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private Stat stat;

    private MonitorConfiguration configuration;

    private InstanceInfo instanceInfo;

    private MetricWriteHelper metricWriteHelper;

    private String overviewMetricFlag;

    private List<Metric> metrics = new ArrayList<Metric>();

    private MetricsCollectorUtil metricsCollectorUtil = new MetricsCollectorUtil();

    private MetricDataParser dataParser;

    private QueueGroup[] queueGroups;

    private ArrayNode nodeDataJson;

    private Phaser phaser;


    private static ObjectMapper objectMapper = new ObjectMapper();

    public List<Metric> getMetrics() {
        return metrics;
    }

    public void setMetricsCollectorUtil(MetricsCollectorUtil metricsCollectorUtil) {
        this.metricsCollectorUtil = metricsCollectorUtil;
    }

    public MetricsCollector(Stat stat, MonitorConfiguration configuration, InstanceInfo instanceInfo, MetricWriteHelper metricWriteHelper,
                             String overviewMetricFlag, MetricDataParser dataParser, QueueGroup[] queueGroups, Phaser phaser)
    {
        this.stat = stat;
        this.configuration = configuration;
        this.instanceInfo = instanceInfo;
        this.metricWriteHelper = metricWriteHelper;
        this.overviewMetricFlag = overviewMetricFlag;
        this.dataParser = dataParser;
        this.queueGroups = queueGroups;
        this.phaser = phaser;
    }


    public void run() {

        try {
            String endpoint = stat.getUrl();
            String url = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path(endpoint).build();
            logger.info("Fetching the RabbitMQ Stats from the URL {}", url);
            nodeDataJson = HttpClientUtils.getResponseAsJson(this.configuration.getHttpClient(), url, ArrayNode.class);
            metrics.addAll(dataParser.parseNodeData(stat, nodeDataJson, objectMapper));

            for (Stat childStat : stat.getStats()) {
                if (childStat.getUrl() != null) {

                    url = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path(childStat.getUrl()).build();
                    logger.info("Fetching the RabbitMQ Stats for stat child: " + childStat.getAlias() + " from the URL {}" + url);

                    JsonNode json = null;
                    if (childStat.getAlias().equalsIgnoreCase("Clusters")) {
                        logger.debug("Overview metric flag: " + overviewMetricFlag);
                        if (overviewMetricFlag.equalsIgnoreCase("true")) {
                            json = HttpClientUtils.getResponseAsJson(this.configuration.getHttpClient(), url, JsonNode.class);
                        }
                    } else {
                        json = HttpClientUtils.getResponseAsJson(this.configuration.getHttpClient(), url, ArrayNode.class);
                    }

                    if (childStat.getAlias().equalsIgnoreCase("Queues")) {
                        QueueMetricParser queueParser = new QueueMetricParser(childStat, configuration, dataParser.getMetricPrefix(), queueGroups);
                        metrics.addAll(queueParser.parseQueueData((ArrayNode) json, nodeDataJson, objectMapper));
                    } else if (childStat.getAlias().equalsIgnoreCase("Channels")) {
                        ChannelMetricParser channelParser = new ChannelMetricParser(childStat, dataParser.getMetricPrefix());
                        metrics.addAll(channelParser.parseChannelData((ArrayNode) json, nodeDataJson, objectMapper));
                    } else if (childStat.getAlias().equalsIgnoreCase("Clusters")) {
                        OverviewMetricParser overviewParser = new OverviewMetricParser(childStat, dataParser.getMetricPrefix());
                        metrics.addAll(overviewParser.parseOverviewData(json, nodeDataJson, objectMapper));
                    }
                }
            }
            metrics.add(new Metric("Heart Beat", String.valueOf(BigInteger.ONE), dataParser.getMetricPrefix() + instanceInfo.getDisplayName() + "|Heart Beat"));
        }
        catch(Exception e){
            logger.error("MetricsCollector error: " + e.getMessage());
            metrics.add(new Metric("Heart Beat", String.valueOf(BigInteger.ZERO), dataParser.getMetricPrefix() + instanceInfo.getDisplayName() + "|Heart Beat"));
        }finally {
            logger.debug("MetricsCollector Phaser arrived for {}", instanceInfo.getDisplayName());
            phaser.arriveAndDeregister();
        }

        if (metrics != null && metrics.size() > 0) {
            logger.debug("Printing Node, Queue, Channel & Overview metrics: " + metrics.size());
            metricWriteHelper.transformAndPrintMetrics(metrics);
        }
    }

}
