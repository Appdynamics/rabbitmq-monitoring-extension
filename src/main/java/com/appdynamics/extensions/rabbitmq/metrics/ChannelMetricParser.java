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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelMetricParser {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(ChannelMetricParser.class);

    private Stat stat;

    private String metricPrefix;

    private MetricsCollectorUtil util = new MetricsCollectorUtil();


    public ChannelMetricParser(Stat stat, String metricPrefix) {
        this.stat = stat;
        this.metricPrefix = metricPrefix;
    }

    /**
     * Goes into Nodes|$node|Messages
     *
     * @param nodeJson
     * @param channelJson
     */

    protected List<Metric> parseChannelData(ArrayNode channelJson, ArrayNode nodeJson, ObjectMapper oMapper){

        List<Metric> metrics = new ArrayList<Metric>();
        if (nodeJson != null) {
            for (JsonNode node : nodeJson) {
                String name = util.getStringValue("name", node);
                if (name != null) {

                    List<JsonNode> nodeChannels = getChannels(channelJson, name);
                    String prefix = "Nodes|" + name;

                    //Nodes|$node|Messages
                    metrics.addAll(addChannelMessageProps(metricPrefix + prefix + "|Messages", nodeChannels, oMapper));

                }

            }
        }
        metrics.add(writeTotalChannelCount(channelJson));
        return metrics;
    }

    // Check how to change this metricPrefix specific to this call
    private List<Metric> addChannelMessageProps(String metricPrefix, List<JsonNode> nodeChannels, ObjectMapper oMapper){

        List<Metric> metrics = new ArrayList<Metric>();

        Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
        Map<String, MetricConfig> propertiesMap = new HashMap<String, MetricConfig>();


        for (JsonNode channel : nodeChannels) {
            JsonNode msgStats = channel.get("message_stats");
            logger.debug("Message stats value: " +msgStats);
                for(MetricConfig metricConfig: stat.getMetricConfig()){
                    if (msgStats != null) {
                        BigInteger statVal = util.getMetricValue(metricConfig.getAttr(), msgStats, metricConfig.isBoolean());
                        if(statVal!=null) {
                            util.addToMap(valueMap, metricConfig.getAlias(), statVal);
                            propertiesMap.put(metricConfig.getAlias(), metricConfig);
                        }
                    }
                }

                for(Map.Entry entry: valueMap.entrySet()){
                    metrics.add(new Metric(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()),
                            metricPrefix + "|" +String.valueOf(entry.getKey()), oMapper.convertValue(propertiesMap.get(entry.getKey()), Map.class)));
                }

        }

        return metrics;
    }


    /**
     * Total count of Channels for the server.
     *
     * @param channels
     */
    private Metric writeTotalChannelCount(ArrayNode channels) {

        long channelCount;
        if (channels != null) {
            channelCount = channels.size();
        } else {
            channelCount = 0;
        }
        return new Metric("Channels", String.valueOf(channelCount), metricPrefix + "Summary|Channels");

    }


    /**
     * Get a list of channels for the give node.
     *
     * @param channels
     * @param nodeName
     * @return
     */
    private List<JsonNode> getChannels(ArrayNode channels, String nodeName) {
        List<JsonNode> nodeChannels = new ArrayList<JsonNode>();
        if (channels != null && nodeName != null) {
            for (JsonNode channel : channels) {
                if (nodeName.equalsIgnoreCase(util.getStringValue("node", channel))) {
                    nodeChannels.add(channel);
                }
            }
        }
        return nodeChannels;
    }

}
