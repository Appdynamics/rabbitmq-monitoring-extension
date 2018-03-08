/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.config.input.MetricConfig;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OverviewMetricParser {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OverviewMetricParser.class);

    private Stat stat;

    private String metricPrefix;

    private MetricsCollectorUtil util = new MetricsCollectorUtil();

    public OverviewMetricParser(Stat stat, String metricPrefix) {
        this.stat = stat;
        this.metricPrefix = metricPrefix;
    }

    protected List<Metric> parseOverviewData(JsonNode overview, ArrayNode nodes) {

        List<Metric> metrics = new ArrayList<Metric>();

        if (overview != null) {
            JsonNode clusterNode = overview.get("cluster_name");
            //In some older versions, the node name is different
            if (clusterNode == null) {
                clusterNode = overview.get("node");
            }
            if (clusterNode != null) {
                String clusterName = clusterNode.getTextValue();
                String prefix = "Clusters|" + clusterName + "|";
                //Queue Totals


                for(Stat childStat: stat.getStats()){

                    metrics.addAll(report(overview.get(childStat.getUrl()), childStat.getMetricConfig(), metricPrefix + prefix + childStat.getAlias(), true));
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
                    metrics.add(new Metric("Running", String.valueOf(runningCount), nodePrefix + "Running"));

                    if (runningCount < nodes.size()) {
                        metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ZERO), metricPrefix + prefix + "Cluster Health"));

                    } else {
                        metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ONE), metricPrefix + prefix + "Cluster Health"));

                    }
                } else{
                    // If there are no nodes running
                    metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ZERO), metricPrefix +prefix + "Cluster Health"));

                }
                metrics.add(new Metric("Availability", String.valueOf(BigInteger.ONE), metricPrefix + "Availability"));

            }
        } else {
            metrics.add(new Metric("Availability", String.valueOf(BigInteger.ZERO), metricPrefix + "Availability"));
        }
        return metrics;
    }

    private List<Metric> report(JsonNode node, MetricConfig[] fields, String metricPrefix, boolean useDictionary) {
        List<Metric> metrics = new ArrayList<Metric>();
        if (node != null && fields != null) {
            ObjectMapper oMapper = new ObjectMapper();

            for (MetricConfig field : fields) {
                Map<String, String> propertiesMap = oMapper.convertValue(field, Map.class);
                JsonNode valueNode = node.get(field.getAttr());
                if (valueNode != null) {
                    metrics.add(new Metric(field.getAlias(), String.valueOf(valueNode.getIntValue()), metricPrefix + field.getAlias(), propertiesMap));

                }
            }
        } else {
            logger.debug("Not reporting the " + metricPrefix + " since the node is null");
        }
        return metrics;
    }
}
