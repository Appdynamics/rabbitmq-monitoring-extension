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
import com.appdynamics.extensions.rabbitmq.queueGroup.GroupStat;
import com.appdynamics.extensions.rabbitmq.queueGroup.GroupStatTracker;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.appdynamics.extensions.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueueMetricParser {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(QueueMetricParser.class);

    private Stat stat;

    private String metricPrefix;

    private QueueGroup[] queueGroups;

    private Map queueFilters;

    private MonitorContext context;

    private MetricsCollectorUtil util = new MetricsCollectorUtil();

    public QueueMetricParser(Stat stat, MonitorContext context, String metricPrefix, QueueGroup[] queueGroups, Map queueFilters) {
        this.stat = stat;
        this.metricPrefix = metricPrefix;
        this.queueGroups = queueGroups;
        this.queueFilters = queueFilters;
        this.context = context;
    }

    /**
     * Iterate over the available queue.message_status. The prefix will be Queues|$host|$QName
     *
     * @param queues
     */
    protected List<Metric> parseQueueData(ArrayNode queues, ArrayNode nodeJson, ObjectMapper oMapper) {

        List<Metric> metrics = new ArrayList<Metric>();
        try {
            if (nodeJson != null) {
                for (JsonNode node : nodeJson) {
                    String name = util.getStringValue("name", node);
                    if (name != null) {

                        List<JsonNode> nodeQueues = getQueues(queues, name);
                        String prefix = "Nodes|" + name;

                        //Nodes|$node|Messages
                        metrics.addAll(addQueueProps(metricPrefix + prefix, nodeQueues));
                    }

                }
            }
            if (queues != null) {
                //flag to ensure summary is calculated only once
                boolean summaryStat = false;

                Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
                Map<String, Map<String, String>> metricPropertiesMap = new HashMap<String, Map<String, String>>();

                GroupStatTracker tracker = new GroupStatTracker(queueGroups);
                for (JsonNode queue : queues) {

                    //Rabbit MQ queue names are case sensitive,
                    // however the controller bombs when there are 2 metrics with same name in different cases.
                    try {
                        String qName = util.lower(util.getStringValue("name", queue, "Default"));
                        String vHost = util.getStringValue("vhost", queue, "Default");
                        logger.debug("Processing queue data for queue: " + qName + " with vhost: " + vHost);
                        if (vHost.equals("/")) {
                            vHost = "Default";
                        }
                        if (!util.isIncluded(queueFilters, qName, stat)) {
                            logger.info("Skipping queue name " + qName + " as it matches exclude queue name regex");
                            continue;
                        } else {
                            logger.info("Not Skipping queue name " + qName + " as it doesn't matches exclude queue name regex");
                        }


                        GroupStat groupStat = tracker.getGroupStat(vHost, qName);
                        boolean showIndividualStats = groupStat.isShowIndividualStats();
                        String prefix = "Queues|" + vHost + "|" + qName + "|";
                        String groupPrefix = "Queue Groups|" + vHost + "|" + groupStat.getGroupName() + "|";
                        BigInteger consumers = util.getMetricValue("consumers", queue, "false");

                        if (showIndividualStats && consumers != null) {
                            Metric metric = new Metric("Consumers", String.valueOf(consumers), metricPrefix + prefix + "Consumers");
                            metrics.add(metric);
                        }
                        groupStat.add(groupPrefix + "Consumers", consumers);

                        for (Stat childStat : stat.getStats()) {
                            String statPrefix = prefix;
                            String statGroupPrefix = groupPrefix + "Messages|";

                            if (StringUtils.hasText(childStat.getAlias()) && childStat.getAlias().equalsIgnoreCase("QueuesSummary") && !summaryStat) {
                                summaryStat = true;
                                statPrefix = "Summary|Messages|";
                            } else if (StringUtils.hasText(childStat.getAlias()) && childStat.getAlias().equalsIgnoreCase("QueuesSummary") && summaryStat) {
                                continue;
                            } else {
                                statPrefix += childStat.getAlias() + "|";
                            }

                            for (MetricConfig metricConfig : childStat.getMetricConfig()) {

                                Map<String, String> propertiesMap = oMapper.convertValue(metricConfig, Map.class);
                                BigInteger value = util.getMetricValue(metricConfig.getAttr(), queue.get("message_stats"), metricConfig.isBoolean());
                                if (value == null) {
                                    value = this.util.getMetricValue(metricConfig.getAttr(), queue, metricConfig.isBoolean());
                                }
                                String metricName = StringUtils.hasText(stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();

                            /*if (childStat.getAlias().equals("MessagesQueueData")) {
                                metricName = StringUtils.hasText(this.stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                                switch (metricConfig.getAttr())
                                {
                                    case "messages_ready":
                                        value = this.util.getMetricValue(metricConfig.getAttr(), queue, metricConfig.isBoolean());
                                        metricName = StringUtils.hasText(this.stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                                        break;
                                    case "messages_unacknowledged":
                                        value = this.util.getMetricValue(metricConfig.getAttr(), queue, metricConfig.isBoolean());
                                        metricName = StringUtils.hasText(this.stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                                }
                            }*/
                                logger.debug("Collected Messages metrics successfully");
                                if (childStat.getAlias().equals("Replication")) {
                                    switch (metricConfig.getAttr()) {
                                        case "slave_nodes":
                                            value = queue.get("slave_nodes") != null ? BigInteger.valueOf(queue.get("slave_nodes").size()) : BigInteger.ZERO;
                                            metricName = StringUtils.hasText(this.stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                                            break;
                                        case "synchronised_slave_nodes":
                                            value = queue.get("synchronised_slave_nodes") != null ? BigInteger.valueOf(queue.get("synchronised_slave_nodes").size()) : BigInteger.ZERO;
                                            metricName = StringUtils.hasText(this.stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                                            break;
                                        case "down_slave_nodes":
                                            value = queue.get("down_slave_nodes") != null ? BigInteger.valueOf(queue.get("down_slave_nodes").size()) : BigInteger.ZERO;
                                            metricName = StringUtils.hasText(this.stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                                    }
                                }
                                logger.debug("Collected Replication metrics successfully");

                                if (showIndividualStats && value != null) {
                                    Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + statPrefix + metricName, propertiesMap);
                                    metrics.add(metric);
                                }
                                if (!("Replication".equalsIgnoreCase(childStat.getAlias())) && !("QueuesSummary".equalsIgnoreCase(childStat.getAlias())) && value != null) {
                                    groupStat.add(statGroupPrefix + metricConfig.getAlias(), value);
                                    metricPropertiesMap.put(statGroupPrefix + metricConfig.getAlias(), propertiesMap);
                                    util.addToMap(valueMap, statGroupPrefix + metricConfig.getAlias(), value);
                                }
                            }
                        }
                    }catch (Exception e){
                        logger.error("Exception parsing data for queue " + util.lower(util.getStringValue("name", queue, "Default")) + " with exception: ",e);
                    }
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
                            metric = new Metric(metricVal, String.valueOf(groupValMap.get(metricVal)), metricPrefix + metricVal, metricPropertiesMap.get(metricVal) != null ? metricPropertiesMap.get(metricVal) : new HashMap<String, String>());
                            metrics.add(metric);
                        }
                    }
                }
            } else {
                metrics.add(new Metric("Queues", String.valueOf(BigInteger.ZERO), metricPrefix + "Summary|Queues"));
            }

            metrics.add(writeTotalConsumerCount(queues));
        }catch (Exception e){
            logger.error("Queue parser error", e);
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

    private List<Metric> addQueueProps(String metricPrefix, List<JsonNode> nodeQueues) {

        List<Metric> metrics = new ArrayList<Metric>();
        try {
            Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();

            for (JsonNode queue : nodeQueues) {
                util.addToMap(valueMap, metricPrefix + "|" + "Messages|Available", util.getMetricValue("messages_ready", queue, "false"));
                util.addToMap(valueMap, metricPrefix + "|" + "Messages|Pending Acknowledgements", util.getMetricValue("messages_unacknowledged", queue, "false"));
                util.addToMap(valueMap, metricPrefix + "|" + "Consumers|Count", util.getMetricValue("consumers", queue, "false"));
            }

            for (Map.Entry entry : valueMap.entrySet()) {
                metrics.add(new Metric(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()), String.valueOf(entry.getKey())));
            }
        }catch(Exception e){
            logger.error("Exception parsing queue when calculating Node|<NodeName>|Messages data ",e);
        }
        return metrics;
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
