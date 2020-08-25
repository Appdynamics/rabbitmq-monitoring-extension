/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.config.input.MetricConfig;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OverviewMetricParser {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(OverviewMetricParser.class);

    private Stat stat;

    private String metricPrefix;


    private MetricsCollectorUtil util = new MetricsCollectorUtil();

    public OverviewMetricParser(Stat stat, String metricPrefix) {
        this.stat = stat;
        this.metricPrefix = metricPrefix;
    }

    protected List<Metric> parseOverviewData(JsonNode overview, ArrayNode nodes, ObjectMapper oMapper) {

        List<Metric> metrics = new ArrayList<Metric>();
        if (overview != null) {
            JsonNode clusterNode = overview.get("cluster_name");
            //In some older versions, the node name is different
            if (clusterNode == null) {
                clusterNode = overview.get("node");
            }
            if (clusterNode != null) {
                String clusterName = clusterNode.textValue();
                String prefix = "Clusters|" + clusterName + "|";

                //Queue Totals
                for(Stat childStat: stat.getStats()){
                    metrics.addAll(report(overview.get(childStat.getUrl()), childStat.getMetricConfig(), metricPrefix + prefix + childStat.getAlias(), oMapper));
                }

                //Total Nodes
                String nodePrefix = prefix + "Nodes|";
                if (nodes != null) {
                    Metric metric = new Metric("Total", String.valueOf(nodes.size()), metricPrefix + nodePrefix + "Total");
                    metrics.add(metric);
                    int runningCount = 0;
                    for (JsonNode node : nodes) {
                        Boolean running = util.getBooleanValue("running", node);
                        if (running != null && running) {
                            runningCount++;
                        }
                    }
                    metrics.add(new Metric("Running", String.valueOf(runningCount), metricPrefix + nodePrefix + "Running"));

                    if (runningCount < nodes.size()) {
                        metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ZERO), metricPrefix + prefix + "Cluster Health"));

                    } else {
                        metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ONE), metricPrefix + prefix + "Cluster Health"));

                    }
                } else{
                    // If there are no nodes running
                    metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ZERO), metricPrefix +prefix + "Cluster Health"));

                }
            }
        }
        return metrics;
    }

    private List<Metric> report(JsonNode node, MetricConfig[] fields, String metricPrefix, ObjectMapper oMapper) {
        List<Metric> metrics = new ArrayList<Metric>();
        if (node != null && fields != null) {

            for (MetricConfig field : fields) {
                Map<String, String> propertiesMap = oMapper.convertValue(field, Map.class);
                JsonNode valueNode = node.get(field.getAttr());
                if (valueNode != null) {
                    metrics.add(new Metric(field.getAlias(), String.valueOf(valueNode.intValue()), metricPrefix + field.getAlias(), propertiesMap));

                }
            }
        } else {
            logger.debug("Not reporting the " + metricPrefix + " since the node is null");
        }
        return metrics;
    }
}
