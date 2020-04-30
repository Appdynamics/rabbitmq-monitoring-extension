/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.queueGroup;


import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by abey.tom on 9/16/14.
 */
public class GroupStatTracker {
    public static final Logger logger = ExtensionsLoggerFactory.getLogger(GroupStatTracker.class);

    private Map<String, GroupStat> groupStatMap;
    private QueueGroup[] queueGroups;


    public GroupStatTracker(QueueGroup[] queueGroups) {
        this.queueGroups = queueGroups;
        if (queueGroups != null && queueGroups.length > 0) {
            groupStatMap = new HashMap<String, GroupStat>();
        } else {
            logger.info("The queue groups are not set. Only the individual Queue stats will be reported");
        }
    }

    public GroupStat getGroupStat(String vHost, String qName) {
        if (queueGroups != null) {
            QueueGroup selected = null;
            for (QueueGroup group : queueGroups) {
                boolean matches = qName.matches(group.getQueueNameRegex());
                if (logger.isDebugEnabled()) {
                    logger.debug("Matching the queue name " + qName + " with the regex "
                            + group.getQueueNameRegex() + "and groupName " + group.getGroupName()
                            + ". The result is " + matches);
                }
                if (matches) {
                    selected = group;
                    break;
                }
            }
            if (selected != null) {
                String groupName = selected.getGroupName();
                StringBuilder sb = new StringBuilder();
                sb.append(vHost).append("-").append(groupName);
                String id = sb.toString();
                GroupStat stat = groupStatMap.get(id);
                if (stat == null) {
                    stat = new GroupStat(vHost, groupName, selected.isShowIndividualStats());
                    groupStatMap.put(id, stat);
                }
                return stat;
            } else {
                logger.debug("No Queue Groups matched for the Queue name " + qName);
            }
        }
        return new GroupStat();
    }

    public Collection<GroupStat> getGroupStats() {
        if (groupStatMap != null) {
            return groupStatMap.values();
        }
        return null;
    }

}
