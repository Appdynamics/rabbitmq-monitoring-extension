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
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;

public class OptionalMetricsCollector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(OptionalMetricsCollector.class);

    private Stat stat;

    private MonitorConfiguration configuration;

    private InstanceInfo instanceInfo;

    private MetricWriteHelper metricWriteHelper;

    private List<Metric> metrics = new ArrayList<Metric>();

    private MetricsCollectorUtil metricsCollectorUtil = new MetricsCollectorUtil();

    private MetricDataParser dataParser;

    private String federationFlag;

    private Phaser phaser;

    public List<Metric> getMetrics() {
        return metrics;
    }

    public void setMetricsCollectorUtil(MetricsCollectorUtil metricsCollectorUtil) {
        this.metricsCollectorUtil = metricsCollectorUtil;
    }

    public OptionalMetricsCollector(Stat stat, MonitorConfiguration configuration, InstanceInfo instanceInfo, MetricWriteHelper metricWriteHelper, MetricDataParser dataParser, String federationFlag, Phaser phaser){

        this.stat = stat;
        this.configuration = configuration;
        this.instanceInfo = instanceInfo;
        this.metricWriteHelper = metricWriteHelper;
        this.dataParser = dataParser;
        this.federationFlag = federationFlag;
        this.phaser = phaser;
    }

    public void run() {

        try {
            phaser.register();
            String url = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path(stat.getUrl()).build();
            logger.debug("Running Optional Metrics Collection task for url: " + url);

            ArrayNode optionalJson = HttpClientUtils.getResponseAsJson(this.configuration.getHttpClient(), url, ArrayNode.class);
            if (stat.getAlias().equalsIgnoreCase("FederationLinks") && federationFlag.equalsIgnoreCase("true")) {
                metrics.addAll(dataParser.parseFederationData(optionalJson));
            } else {
                metrics.addAll(dataParser.parseAdditionalData(stat, optionalJson));
            }
        }catch(Exception e){
            logger.error("OptionalMetricsCollector error: " + e.getMessage());
        }finally {
            logger.debug("MetircsCollector Phaser arrived for {}", instanceInfo.getDisplayName());
            phaser.arriveAndDeregister();
        }

        if (metrics != null && metrics.size() > 0) {
            logger.debug("Printing optional metrics list of size " + metrics.size());
            metricWriteHelper.transformAndPrintMetrics(metrics);
        }
    }
}
