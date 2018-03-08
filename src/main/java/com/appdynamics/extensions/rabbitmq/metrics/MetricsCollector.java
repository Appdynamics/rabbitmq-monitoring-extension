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
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.appdynamics.extensions.util.AssertUtils.assertNotNull;

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

    public void setMetricsCollectorUtil(MetricsCollectorUtil metricsCollectorUtil) {
        this.metricsCollectorUtil = metricsCollectorUtil;
    }

    public MetricsCollector(Stat stat, MonitorConfiguration configuration, InstanceInfo instanceInfo, MetricWriteHelper metricWriteHelper,
                             String overviewMetricFlag, MetricDataParser dataParser, QueueGroup[] queueGroups)
    {
        this.stat = stat;
        this.configuration = configuration;
        this.instanceInfo = instanceInfo;
        this.metricWriteHelper = metricWriteHelper;
        this.overviewMetricFlag = overviewMetricFlag;
        this.dataParser = dataParser;
        this.queueGroups = queueGroups;
    }


    public void run() {

        String endpoint = stat.getUrl();
        String label = stat.getAlias();
        assertNotNull(label, "The label attribute cannot be empty for stat " + stat);
        String url = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path(endpoint).build();
        logger.info("Fetching the RabbitMQ Stats from the URL {}", url);
        nodeDataJson = metricsCollectorUtil.getJson(this.configuration.getHttpClient(), url);

        metrics.addAll(dataParser.parseNodeData(stat, nodeDataJson));

        for(Stat childStat: stat.getStats()){

            if(childStat.getUrl() != null) {

                url = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path(childStat.getUrl()).build();
                logger.info("Fetching the RabbitMQ Stats for stat child: " + childStat.getAlias() + " from the URL {}" + url );

                ArrayNode json = null;
                if(childStat.getAlias().equalsIgnoreCase("Clusters")) {
                    logger.debug("Overview metric flag: " + overviewMetricFlag);
                    if( overviewMetricFlag.equalsIgnoreCase("true")) {
                        json = metricsCollectorUtil.getOptionalJson(this.configuration.getHttpClient(), url, ArrayNode.class);
                    }
                }else{
                    json = metricsCollectorUtil.getJson(this.configuration.getHttpClient(), url);
                }

                if(childStat.getAlias().equalsIgnoreCase("Queues")){
                    QueueMetricParser queueParser = new QueueMetricParser(childStat, configuration, dataParser.getMetricPrefix(), queueGroups);
                    metrics.addAll(queueParser.parseQueueData(json, nodeDataJson));
                }else if(childStat.getAlias().equalsIgnoreCase("Channels")){
                    ChannelMetricParser channelParser = new ChannelMetricParser(childStat, dataParser.getMetricPrefix());
                    metrics.addAll(channelParser.parseChannelData(json, nodeDataJson));
                }else if(childStat.getAlias().equalsIgnoreCase("Clusters")){
                    OverviewMetricParser overviewParser = new OverviewMetricParser(childStat, dataParser.getMetricPrefix());
                    metrics.addAll(overviewParser.parseOverviewData(json, nodeDataJson));
                }
            }
        }

        if (metrics != null && metrics.size() > 0) {
            logger.debug("Printing Node, Queue, Channel & Overview metrics: " + metrics.size());
            metricWriteHelper.transformAndPrintMetrics(metrics);
        }
    }

}
