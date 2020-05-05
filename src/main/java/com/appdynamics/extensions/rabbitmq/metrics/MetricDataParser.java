/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.config.input.MetricConfig;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MetricDataParser {

    private static final org.slf4j.Logger logger = ExtensionsLoggerFactory.getLogger(MetricDataParser.class);

    private String metricPrefix;

    private MonitorContextConfiguration configuration;

    private Map nodeFilters;

    private MetricsCollectorUtil util = new MetricsCollectorUtil();


    public MetricDataParser(String metricPrefix, MonitorContextConfiguration configuration, Map nodeFilters){
        this.metricPrefix = metricPrefix;
        this.configuration = configuration;
        this.nodeFilters = nodeFilters;
    }


    public String getMetricPrefix() {
        return metricPrefix;
    }

    /**
     * The data in the prefix Nodes|$node and Summary|
     *
     * @param stat
     * @param nodes
     */
    protected List<Metric> parseNodeData(Stat stat, ArrayNode nodes, ObjectMapper oMapper) {
        List<Metric> metrics = new ArrayList<Metric>();

        if (nodes != null) {

            for (JsonNode node : nodes) {
                String name = util.getStringValue("name", node);
                if (name != null) {
                    if(!util.isIncluded(nodeFilters, name, stat)){
                        logger.info("Skipping node name " + name + " as it is not present in the include filter");
                        continue;
                    }
                    else{
                        logger.info("Not Skipping node name " + name + " as it is present in the include filter");
                    }
                    String prefix = StringUtils.trim(stat.getAlias(), "|") + "|" + name;
                    for(MetricConfig metricConfig: stat.getMetricConfig()){
                        BigInteger metricValue =  util.getMetricValue(metricConfig.getAttr(), node, metricConfig.isBoolean());
                        if(metricValue!=null) {
                            Map<String, String> propertiesMap = oMapper.convertValue(metricConfig, Map.class);
                            Metric metric = new Metric(metricConfig.getAlias(), String.valueOf(metricValue), metricPrefix + prefix + "|" + metricConfig.getAlias(), propertiesMap);
                            metrics.add(metric);
                        }

                    }

                }
            }
        }

        return metrics;

    }

    protected List<Metric> parseFederationData(ArrayNode federationLinks) {

        List<Metric> metrics = new ArrayList<Metric>();
        logger.debug("Parsing federation data json: " + federationLinks);
        String prefix = "Federations|";
        if (federationLinks != null) {
            for (JsonNode federationLink : federationLinks) {
                final String exchangeName = util.getStringValue("exchange", federationLink);
                final String upstreamName = util.getStringValue("upstream", federationLink);
                final String status = util.getStringValue("status", federationLink);
                Metric metric = new Metric(exchangeName + "|" + upstreamName, String.valueOf(status.equals("running") ? 1 : 0), metricPrefix + prefix + exchangeName + "|" + upstreamName + "|running");
                metrics.add(metric);
            }
        }

        return metrics;
    }

    protected List<Metric> parseAdditionalData(Stat stat, ArrayNode optionalJson) {

        List<Metric> metrics = new ArrayList<Metric>();
        ObjectMapper oMapper = new ObjectMapper();

        logger.debug("Parsing optional data json: " + optionalJson);
        String prefix = stat.getAlias() + "|";
        if (optionalJson != null) {
            for(JsonNode node: optionalJson) {

                String nodeName = util.lower(util.getStringValue("name", node, "Default"));

                prefix = prefix + nodeName + "|";

                for (MetricConfig metricConfig : stat.getMetricConfig()) {

                    Map<String, String> propertiesMap = oMapper.convertValue(metricConfig, Map.class);

                    BigInteger value = util.getMetricValue(metricConfig.getAttr(), node, metricConfig.isBoolean());
                    if(value!=null) {
                        String metricName = StringUtils.hasText(stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                        Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + prefix + metricName, propertiesMap);
                        metrics.add(metric);
                    }
                }
            }

        }

        return metrics;
    }
}
