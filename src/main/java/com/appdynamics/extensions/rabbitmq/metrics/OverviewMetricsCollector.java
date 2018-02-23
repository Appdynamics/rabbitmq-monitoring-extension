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
import org.slf4j.LoggerFactory;

import java.util.List;
/*
public class OverviewMetricsCollector implements Runnable {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OverviewMetricsCollector.class);

    private MonitorConfiguration configuration;

    private InstanceInfo instanceInfo;

    private MetricWriteHelper metricWriteHelper;

    private List<Metric> metrics;

    private MetricsCollectorUtil metricsCollectorUtil = new MetricsCollectorUtil();

    private MetricDataParser dataParser;

    public OverviewMetricsCollector(MonitorConfiguration configuration, InstanceInfo instanceInfo, MetricWriteHelper metricWriteHelper, List<Metric> metrics, MetricDataParser dataParser)
    {
        this.configuration = configuration;
        this.instanceInfo = instanceInfo;
        this.metricWriteHelper = metricWriteHelper;
        this.metrics = metrics;
        this.dataParser = dataParser;
    }

    public void run() {

        String overviewUrl = UrlBuilder.builder(metricsCollectorUtil.getUrlParametersMap(instanceInfo)).path("/api/overview").build();
        JsonNode overview = metricsCollectorUtil.getOptionalJson(this.configuration.getHttpClient(), overviewUrl, JsonNode.class);
        metrics.addAll(dataParser.parseOverviewData(overview));

        if (metrics != null && metrics.size() > 0) {
            logger.debug("Printing overview metrics list of size " + metrics.size());
            metricWriteHelper.transformAndPrintMetrics(metrics);
        }
    }
}*/
