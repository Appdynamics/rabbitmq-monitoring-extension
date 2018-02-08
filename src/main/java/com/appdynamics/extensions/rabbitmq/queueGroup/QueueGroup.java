package com.appdynamics.extensions.rabbitmq.queueGroup;

/**
 * Created by abey.tom on 9/16/14.
 */
public class QueueGroup {

    private String groupName;
    private String queueNameRegex;
    private Boolean showIndividualStats;

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getQueueNameRegex() {
        return queueNameRegex;
    }

    public void setQueueNameRegex(String queueNameRegex) {
        this.queueNameRegex = queueNameRegex;
    }

    public boolean isShowIndividualStats() {
        return showIndividualStats;
    }

    public void setShowIndividualStats(boolean showIndividualStats) {
        this.showIndividualStats = showIndividualStats;
    }
}
