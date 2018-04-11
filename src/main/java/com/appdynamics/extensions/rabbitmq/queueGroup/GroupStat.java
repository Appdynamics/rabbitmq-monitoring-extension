/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.queueGroup;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by abey.tom on 9/17/14.
 */
public class GroupStat {
    private String vHost;
    private String groupName;
    private boolean showIndividualStats;

    private Map<String, BigInteger> valueMap;
    private Map<String, String> metricPropertiesMap;
    private Map<String, Boolean> collectDeltaMap;

    protected GroupStat() {
        showIndividualStats = true;
    }

    public GroupStat(String vHost, String groupName, boolean showIndividualStats) {
        this.vHost = vHost;
        this.groupName = groupName;
        this.showIndividualStats = showIndividualStats;
        valueMap = new HashMap<String, BigInteger>();
        metricPropertiesMap = new HashMap<String, String>();
        collectDeltaMap = new HashMap<String, Boolean>();
    }

    public String getvHost() {
        return vHost;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean isShowIndividualStats() {
        return showIndividualStats;
    }

    public void add(String metric, BigInteger value) {
        if (valueMap != null) {
            BigInteger bigInteger = valueMap.get(metric);
            if (bigInteger == null) {
                valueMap.put(metric, value);
            } else {
                valueMap.put(metric, bigInteger.add(value));
            }
        }
    }

    public void setMetricPropertiesMap(Map<String, String> properties){
        if(metricPropertiesMap !=null) {
            metricPropertiesMap.putAll(properties);
        }
    }

    public Map<String, BigInteger> getValueMap() {
        return valueMap;
    }

    public Map<String, String> getMetricPropertiesMap() {
        return metricPropertiesMap;
    }

    public Map<String, Boolean> getCollectDeltaMap() {
        return collectDeltaMap;
    }
}
