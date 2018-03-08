/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.config.input.MetricConfig;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.queueGroup.GroupStat;
import com.appdynamics.extensions.rabbitmq.queueGroup.GroupStatTracker;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.appdynamics.extensions.util.StringUtils;
import com.google.common.base.Strings;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueueMetricParser {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(QueueMetricParser.class);

    private Stat stat;

    private String metricPrefix;

    private QueueGroup[] queueGroups;

    private MonitorConfiguration configuration;

    private MetricsCollectorUtil util = new MetricsCollectorUtil();

    public QueueMetricParser(Stat stat, MonitorConfiguration configuration, String metricPrefix, QueueGroup[] queueGroups) {
        this.stat = stat;
        this.metricPrefix = metricPrefix;
        this.queueGroups = queueGroups;
        this.configuration = configuration;
    }

    /**
     * Iterate over the available queue.message_status. The prefix will be Queues|$host|$QName
     *
     * @param queues
     */
    protected List<Metric> parseQueueData(ArrayNode queues, ArrayNode nodes) {

        List<Metric> metrics = new ArrayList<Metric>();
        ObjectMapper oMapper = new ObjectMapper();

        logger.debug("Queue json: " + queues);

        if (queues != null) {
            Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();

            GroupStatTracker tracker = new GroupStatTracker(queueGroups);
            for (JsonNode queue : queues) {

                //Rabbit MQ queue names are case sensitive,
                // however the controller bombs when there are 2 metrics with same name in different cases.
                String qName = util.lower(util.getStringValue("name", queue, "Default"));
                String vHost = util.getStringValue("vhost", queue, "Default");
                logger.debug("Processing queue data for queue: " + qName + " with vhost: " + vHost);
                if (vHost.equals("/")) {
                    vHost = "Default";
                }
                if(!util.isIncluded(configuration, qName, stat)){
                    logger.info("Skipping queue name "+qName+ " as it matches exclude queue name regex");
                    continue;
                }
                else{
                    logger.info("Not Skipping queue name "+qName+ " as it doesn't matches exclude queue name regex");
                }


                GroupStat groupStat = tracker.getGroupStat(vHost, qName);
                boolean showIndividualStats = groupStat.isShowIndividualStats();
                String prefix = "Queues|" + vHost + "|" + qName;
                String groupPrefix = "Queue Groups|" + vHost + "|" + groupStat.getGroupName();
                BigInteger consumers = new BigInteger("0");

                for(Stat childStat: stat.getStats()){
                    String statPrefix = prefix;
                    String statGroupPrefix = groupPrefix;

                    if(StringUtils.hasText(stat.getAlias())){
                        statPrefix += "|" + stat.getAlias() +"|";
                        statGroupPrefix += groupPrefix + "|" + stat.getAlias() +"|";
                    }

                    if(StringUtils.hasText(stat.getAlias()) && stat.getAlias().equalsIgnoreCase("Summary"))
                        statPrefix = "Summary|Messages|";

                    for(MetricConfig metricConfig: childStat.getMetricConfig()){

                        Map<String, String> propertiesMap = oMapper.convertValue(metricConfig, Map.class);

                        BigInteger value = util.getMetricValue(metricConfig.getAttr(), queue, metricConfig.isBoolean());
                        String metricName = StringUtils.hasText(stat.getAlias()) ? metricConfig.getAlias() : metricConfig.getAttr();
                        if (showIndividualStats) {
                            Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + statPrefix + metricName, propertiesMap);
                            metrics.add(metric);
                        }
                        if(!(stat.getAlias().equalsIgnoreCase("Replication")) || !(stat.getAlias().equalsIgnoreCase("Summary"))) {
                            groupStat.add(statGroupPrefix + metricConfig.getAttr(), consumers);
                            groupStat.setMetricPropertiesMap(propertiesMap);
                            util.addToMap(valueMap, metricConfig.getAttr(), value);
                        }
                    }
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
                        metric = new Metric(metricVal, String.valueOf(groupValMap.get(metricVal)), metricPrefix + metricVal, groupStat.getMetricPropertiesMap());
                        metrics.add(metric);
                    }
                }
            }
        } else {
            metrics.add(new Metric("Queues", String.valueOf(BigInteger.ZERO), metricPrefix + "Summary|Queues"));
        }

        metrics.add(writeTotalConsumerCount(queues));

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
}
