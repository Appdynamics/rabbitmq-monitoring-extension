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
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FederationMetricsCollector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FederationMetricsCollector.class);

    private MonitorConfiguration configuration;

    private InstanceInfo instanceInfo;

    private MetricWriteHelper metricWriteHelper;

    private List<Metric> metrics;

    private MetricsCollectorUtil metricsCollectorUtil = new MetricsCollectorUtil();

    private MetricDataParser dataParser;

    public FederationMetricsCollector(MonitorConfiguration configuration, InstanceInfo instanceInfo, MetricWriteHelper metricWriteHelper, List<Metric> metrics, MetricDataParser dataParser){

        this.configuration = configuration;
        this.instanceInfo = instanceInfo;
        this.metricWriteHelper = metricWriteHelper;
        this.metrics = metrics;
        this.dataParser = dataParser;
    }

    public void run() {

        String federationLinkUrl = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path("/api/federation-links").build();
        ArrayNode federationLinks = metricsCollectorUtil.getOptionalJson(this.configuration.getHttpClient(), federationLinkUrl, ArrayNode.class);
        metrics.addAll(dataParser.parseFederationData(federationLinks));

        if (metrics != null && metrics.size() > 0) {
            logger.debug("Printing federation metrics list of size " + metrics.size());
            metricWriteHelper.transformAndPrintMetrics(metrics);
        }
    }
}
