/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.queueGroup.GroupStat;
import com.appdynamics.extensions.rabbitmq.queueGroup.GroupStatTracker;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.google.common.base.Strings;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricDataParser {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MetricDataParser.class);

    private Map<String, List<Map<String, String>>> allMetricsFromConfig;

    private String metricPrefix;

    private QueueGroup[] queueGroups;

    private String excludeQueueRegex;

    private MetricsCollectorUtil util = new MetricsCollectorUtil();


    public MetricDataParser(Map<String, List<Map<String, String>>> allMetricsFromConfig, String metricPrefix, QueueGroup[] queueGroups, String excludeQueueRegex){
        this.allMetricsFromConfig = allMetricsFromConfig;
        this.metricPrefix = metricPrefix;
        this.queueGroups = queueGroups;
        this.excludeQueueRegex = excludeQueueRegex;
    }

    public Map<String, List<Map<String, String>>> getAllMetricsFromConfig() {
        return allMetricsFromConfig;
    }

    protected List<Metric> process(ArrayNode nodes, ArrayNode channels, ArrayNode queues) {

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.addAll(parseNodeData(nodes, channels, queues));
        metrics.addAll(parseQueueData(queues));

        return metrics;
    }

    /**
     * The data in the prefix Nodes|$node and Summary|
     *
     * @param nodes
     * @param channels
     * @param queues
     */
    private List<Metric> parseNodeData(ArrayNode nodes, ArrayNode channels, ArrayNode queues) {
        List<Map<String, String>> nodeDataList= allMetricsFromConfig.get("nodeDataMetrics");
        List<Metric> metrics = new ArrayList<Metric>();

        if (nodes != null) {
            for (JsonNode node : nodes) {
                String name = util.getStringValue("name", node);
                if (name != null) {
                    List<JsonNode> nodeChannels = getChannels(channels, name);
                    List<JsonNode> nodeQueues = getQueues(queues, name);
                    String prefix = "Nodes|" + name;

                    for(Map<String, String> nodeData : nodeDataList){

                        BigInteger metricValue = util.getMetricValue(nodeData, node);

                        if(metricValue!=null) {
                            Metric metric = new Metric(nodeData.get("prefix"), String.valueOf(metricValue), metricPrefix + prefix + "|" + nodeData.get("prefix"), nodeData);
                            metrics.add(metric);
                        }
                    }

                    metrics.add(new Metric(prefix, String.valueOf(getBlockedChannelCount(nodeChannels)), metricPrefix + prefix + "|Channels|Blocked"));

                    //Nodes|$node|Messages
                    metrics.addAll(addChannelMessageProps(metricPrefix + prefix + "|Messages", nodeChannels));
                    //Nodes|$node|Messages
                    metrics.addAll(addQueueProps(metricPrefix + prefix + "|Messages", nodeQueues, "queueNodeMsgProps"));
                    //Nodes|$node|Consumers
                    metrics.addAll(addQueueProps(metricPrefix + prefix + "|Consumers", nodeQueues, "queueNodeProps"));
                }
            }
        }
        metrics.add(writeTotalChannelCount(channels));
        metrics.add(writeTotalConsumerCount(queues));

        return metrics;

    }

    /**
     * Iterate over the available queue.message_status. The prefix will be Queues|$host|$QName
     *
     * @param queues
     */
    private List<Metric> parseQueueData(ArrayNode queues) {
        List<Metric> metrics = new ArrayList<Metric>();

        if (queues != null) {
            Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();

            GroupStatTracker tracker = new GroupStatTracker(queueGroups);
            for (JsonNode queue : queues) {

                //Rabbit MQ queue names are case sensitive,
                // however the controller bombs when there are 2 metrics with same name in different cases.
                String qName = util.lower(util.getStringValue("name", queue, "Default"));
                String vHost = util.getStringValue("vhost", queue, "Default");
                if (vHost.equals("/")) {
                    vHost = "Default";
                }
                if(!Strings.isNullOrEmpty(excludeQueueRegex)){
                    if(qName.matches(excludeQueueRegex)){
                        logger.info("Skipping queue name "+qName+ " as it matches exclude queue name regex");
                        continue;
                    }
                    else{
                        logger.info("Not Skipping queue name "+qName+ " as it doesn't matches exclude queue name regex");
                    }
                }

                GroupStat groupStat = tracker.getGroupStat(vHost, qName);
                boolean showIndividualStats = groupStat.isShowIndividualStats();
                String prefix = "Queues|" + vHost + "|" + qName;
                String groupPrefix = "Queue Groups|" + vHost + "|" + groupStat.getGroupName();
                List<Map<String, String>> queueGroupPropsList= allMetricsFromConfig.get("queueGroupProps");
                BigInteger consumers = new BigInteger("0");
                for(Map<String, String> prop : queueGroupPropsList){
                    logger.debug("Queue data with metric name: " + prop.get("name"));
                    consumers = util.getMetricValue(prop, queue);
                    if (showIndividualStats) {
                        Metric metric = new Metric(prop.get("name"), String.valueOf(consumers), metricPrefix + prefix + prop.get("name"), prop);
                        metrics.add(metric);
                    }
                    groupStat.add(groupPrefix + prop.get("name"), consumers);
                    groupStat.setMetricPropertiesMap(prop);
                }

                String msgPrefix = prefix + "|Messages|";
                String grpMsgPrefix = groupPrefix + "|Messages|";

                List<Map<String, String>> queueMessagePropsList= allMetricsFromConfig.get("queueMessageProps");

                for (Map<String, String> prop : queueMessagePropsList) {
                    BigInteger value = util.getMetricValue(prop, queue);
                    String metricName = prop.get("alias");
                    if (showIndividualStats) {
                        Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + msgPrefix + metricName, prop);
                        metrics.add(metric);
                    }

                    groupStat.add(groupPrefix + prop.get("name"), consumers);
                    groupStat.setMetricPropertiesMap(prop);
                    addToMap(valueMap, prop.get("name"), value);
                }

                String replicationPrefix = prefix + "|Replication|";

                List<Map<String, String>> queueReplicationCountsPropsList= allMetricsFromConfig.get("queueReplicationCountsProps");
                for (Map<String, String> prop : queueReplicationCountsPropsList) {
                    BigInteger value = util.getChildrenCount(prop.get("name"), queue, 0);
                    String metricName = prop.get("alias");
                    if (showIndividualStats) {
                        Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + replicationPrefix + metricName, prop);
                        metrics.add(metric);
                    }
                }

                List<Map<String, String>> queueMessageStatsPropsList= allMetricsFromConfig.get("queueMessageStatsProps");


                for (Map<String, String> prop : queueMessageStatsPropsList) {
                    BigInteger value = util.getMetricValue(prop, queue.get("message_stats"));
                    String metricName = prop.get("alias");
                    if (showIndividualStats) {
                        Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + msgPrefix + metricName, prop);
                        metrics.add(metric);
                    }
                    groupStat.add(grpMsgPrefix + prop.get("name"), consumers);
                    groupStat.setMetricPropertiesMap(prop);
                    addToMap(valueMap, prop.get("name"), value);
                }
            }
            //Aggregate the above data for Summary|Messages
            String summaryPrefix = "Summary|Messages|";

            List<Map<String, String>> queueSummaryPropsList= allMetricsFromConfig.get("queueSummaryProps");

            for (Map<String, String> prop : queueSummaryPropsList) {
                BigInteger value = valueMap.get(prop.get("name"));
                Metric metric = new Metric(prop.get("alias"), String.valueOf(value), metricPrefix + summaryPrefix + prop.get("alias"), prop);
                metrics.add(metric);
            }
            //Total Number of Queues
            Metric metric = new Metric("Queues", String.valueOf(queues.size()), metricPrefix + "Summary|Queues");
            metrics.add(metric);

            //Print the regex queue group metrics
            Collection<GroupStat> groupStats = tracker.getGroupStats();
            if (groupStats != null) {
                for (GroupStat groupStat : groupStats) {
                    Map<String, BigInteger> groupValMap = groupStat.getValueMap();
                    for (String metricVal : groupValMap.keySet()) {
                        metric = new Metric(metricVal, String.valueOf(groupValMap.get(metricVal)), metricPrefix + metricVal, groupStat.getMetricPropertiesMap());
                        metrics.add(metric);
                    }
                }
            }
        } else {
            metrics.add(new Metric("Queues", String.valueOf(BigInteger.ZERO), metricPrefix + "Summary|Queues"));
        }

        return metrics;
    }

    protected List<Metric> parseFederationData(ArrayNode federationLinks) {

        List<Metric> metrics = new ArrayList<Metric>();
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

    protected List<Metric> parseOverviewData(JsonNode overview, ArrayNode nodes) {

        List<Metric> metrics = new ArrayList<Metric>();

        if (overview != null) {
            JsonNode clusterNode = overview.get("cluster_name");
            //In some older versions, the node name is different
            if (clusterNode == null) {
                clusterNode = overview.get("node");
            }
            if (clusterNode != null) {

                List<Map<String, String>> queueTotalsPropsList= allMetricsFromConfig.get("queueTotalsProps");

                List<Map<String, String>> messageTotalsPropsList= allMetricsFromConfig.get("messageTotalsProps");

                List<Map<String, String>> objectTotalsPropsList= allMetricsFromConfig.get("objectTotalsProps");


                String clusterName = clusterNode.getTextValue();
                String prefix = "Clusters|" + clusterName + "|";
                //Queue Totals
                metrics.addAll(report(overview.get("message_stats"), messageTotalsPropsList, metricPrefix + prefix + "Messages|", true));
                metrics.addAll(report(overview.get("queue_totals"), queueTotalsPropsList, metricPrefix + prefix + "Queues|", true));
                metrics.addAll(report(overview.get("object_totals"), objectTotalsPropsList, metricPrefix + prefix + "Objects|", false));

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

    private List<Metric> report(JsonNode node, List<Map<String, String>> fields, String metricPrefix, boolean useDictionary) {
        List<Metric> metrics = new ArrayList<Metric>();
        if (node != null && fields != null) {
            for (Map<String, String> field : fields) {
                JsonNode valueNode = node.get(field.get("name"));
                if (valueNode != null) {
                    if (useDictionary) {
                        metrics.add(new Metric(field.get("alias"), String.valueOf(valueNode.getIntValue()), metricPrefix + field.get("alias"), field));
                    } else {
                        metrics.add(new Metric(field.get("name"), String.valueOf(valueNode.getIntValue()), metricPrefix + field.get("name"), field));
                    }
                }
            }
        } else {
            logger.debug("Not reporting the " + metricPrefix + " since the node is null");
        }
        return metrics;
    }


    /**
     * Total Consumers for the Server = Sum of all consumers of all Queues
     *
     * @param queues
     */
    private Metric writeTotalConsumerCount(ArrayNode queues) {

        BigInteger count = new BigInteger("0");
        if (queues != null) {
            for (JsonNode queue : queues) {
                BigInteger value = util.getBigIntegerValue("consumers", queue, 0);
                count = count.add(value);
            }
        }
        return new Metric("Consumers", String.valueOf(count), metricPrefix + "Summary|Consumers" );

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

    private List<Metric> addQueueProps(String metricPrefix, List<JsonNode> nodeQueues, String configName) {

        List<Metric> metrics = new ArrayList<Metric>();

        for (JsonNode queue : nodeQueues) {
            List<Map<String, String>> queueNodePropsList= allMetricsFromConfig.get(configName);
            for (Map<String, String> prop : queueNodePropsList) {
                BigInteger value = util.getMetricValue(prop, queue);
                if(value!=null) {
                    Metric metric = new Metric(prop.get("name"), String.valueOf(value), metricPrefix  + "|" + prop.get("alias"), prop);
                    metrics.add(metric);
                }
            }
        }
        return metrics;
    }


    /**
     * Goes into Nodes|$node|Messages
     *
     * @param metricPrefix
     * @param nodeChannels
     */
    private List<Metric> addChannelMessageProps(String metricPrefix, List<JsonNode> nodeChannels){

        List<Metric> metrics = new ArrayList<Metric>();
        for (JsonNode channel : nodeChannels) {
            JsonNode msgStats = channel.get("message_stats");
            List<Map<String, String>> channelNodeMsgPropsList = allMetricsFromConfig.get("channelNodeMsgProps");

            for (Map<String, String> prop : channelNodeMsgPropsList) {
                if (msgStats != null) {
                    BigInteger statVal = util.getMetricValue(prop, channel);
                    if(statVal!=null) {
                        metrics.add(new Metric(prop.get("name"), String.valueOf(statVal), metricPrefix + "|" + prop.get("alias"), prop));
                    }
                }
            }
        }
        return metrics;
    }

    /**
     * Adds the value to the Map. If the value is present it adds to the current value.
     * The map is used to calculate the aggregate.
     *
     * @param valueMap
     * @param prop
     * @param val
     */
    private void addToMap(Map<String, BigInteger> valueMap, String prop, BigInteger val) {
        if (val != null) {
            BigInteger curr = valueMap.get(prop);
            if (curr == null) {
                valueMap.put(prop, val);
            } else {
                valueMap.put(prop, curr.add(val));
            }
        }
    }

    /**
     * Nodes|$node|Channels|Blocked
     *
     * @param nodeChannels
     * @return
     */
    private BigInteger getBlockedChannelCount(List<JsonNode> nodeChannels) {
        int blocked = 0;
        for (JsonNode nodeChannel : nodeChannels) {
            Boolean value = util.getBooleanValue("client_flow_blocked", nodeChannel);
            if (value != null && value) {
                blocked++;
            }
        }
        return new BigInteger(String.valueOf(blocked));
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

    /**
     * Get a list of queues for the give node.
     *
     * @param queues
     * @param nodeName
     * @return
     */
    private List<JsonNode> getQueues(ArrayNode queues, String nodeName) {
        List<JsonNode> nodeQueues = new ArrayList<JsonNode>();
        if (queues != null && nodeName != null) {
            for (JsonNode queue : queues) {
                if (nodeName.equalsIgnoreCase(util.getStringValue("node", queue))) {
                    nodeQueues.add(queue);
                }
            }
        }
        return nodeQueues;
    }


}
