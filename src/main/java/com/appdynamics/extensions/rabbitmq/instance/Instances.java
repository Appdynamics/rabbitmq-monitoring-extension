/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.instance;

import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;

public class Instances {
	
	private QueueGroup[] queueGroups;
	private InstanceInfo[] instances;
	public InstanceInfo[] getInstances() {
		return instances;
	}
	public void setInstances(InstanceInfo[] instances) {
		this.instances = instances;
	}
	public QueueGroup[] getQueueGroups() {
		return queueGroups;
	}
	public void setQueueGroups(QueueGroup[] queueGroups) {
		this.queueGroups = queueGroups;
	}

}
