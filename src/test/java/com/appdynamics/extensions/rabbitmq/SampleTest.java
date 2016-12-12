package com.appdynamics.extensions.rabbitmq;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

public class SampleTest {

	@Test
	public void test1() throws TaskExecutionException{
		Map<String,String> map = new HashMap<String,String>();
		map.put("config-file", "src/main/resources/config/config.yml");
		new RabbitMQMonitor().execute(map, new TaskExecutionContext());
	}
	
}
