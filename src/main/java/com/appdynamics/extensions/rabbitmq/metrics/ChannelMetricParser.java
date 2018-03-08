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

public class ChannelMetricParser {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ChannelMetricParser.class);

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

    protected List<Metric> parseChannelData(ArrayNode channelJson, ArrayNode nodeJson){

        List<Metric> metrics = new ArrayList<Metric>();
        if (nodeJson != null) {
            for (JsonNode node : nodeJson) {
                String name = util.getStringValue("name", node);
                if (name != null) {

                    List<JsonNode> nodeChannels = getChannels(channelJson, name);
                    String prefix = "Nodes|" + name;

                    //Nodes|$node|Messages
                    metrics.addAll(addChannelMessageProps(metricPrefix + prefix + "|Messages", nodeChannels));

                }

            }
        }
        metrics.add(writeTotalChannelCount(channelJson));
        return metrics;
    }

    // Check how to change this metricPrefix specific to this call
    private List<Metric> addChannelMessageProps(String metricPrefix, List<JsonNode> nodeChannels){

        List<Metric> metrics = new ArrayList<Metric>();
        ObjectMapper oMapper = new ObjectMapper();

        for (JsonNode channel : nodeChannels) {
            JsonNode msgStats = channel.get("message_stats");
            logger.debug("Message stats value: " +msgStats);
                for(MetricConfig metricConfig: stat.getMetricConfig()){
                    Map<String, String> propertiesMap = oMapper.convertValue(metricConfig, Map.class);
                    if (msgStats != null) {
                        BigInteger statVal = util.getMetricValue(metricConfig.getAttr(), msgStats, metricConfig.isBoolean());
                        if(statVal!=null) {
                            logger.debug(String.format("Channel Metrics with name: %s value: %s path: %s", metricConfig.getAttr(), statVal, metricPrefix+"|"+metricConfig.getAlias()));
                            metrics.add(new Metric(metricConfig.getAttr(), String.valueOf(statVal), metricPrefix + "|" + metricConfig.getAlias(), propertiesMap));
                        }
                    }
                }
        }

        return metrics;
    }


    /**
     * Total cont of Channels for the server.
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
        return new Metric("Channels", String.valueOf(channelCount), metricPrefix + "|Summary|Channels");

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
