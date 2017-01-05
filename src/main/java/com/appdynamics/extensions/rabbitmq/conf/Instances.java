package com.appdynamics.extensions.rabbitmq.conf;

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
