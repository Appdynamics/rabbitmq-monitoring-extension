package com.appdynamics.extensions.rabbitmq;

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

    protected GroupStat() {
        showIndividualStats = true;
    }

    public GroupStat(String vHost, String groupName, boolean showIndividualStats) {
        this.vHost = vHost;
        this.groupName = groupName;
        this.showIndividualStats = showIndividualStats;
        valueMap = new HashMap<String, BigInteger>();
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

    public Map<String, BigInteger> getValueMap() {
        return valueMap;
    }
}
