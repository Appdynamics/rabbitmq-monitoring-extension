package com.appdynamics.extensions.rabbitmq;

import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.junit.Test;

import java.util.Map;

public class RabbitMQMonitorTest {

    @Test
    public void test() throws TaskExecutionException {
        RabbitMQMonitor monitor = new RabbitMQMonitor();
        Map<String, String> taskArgs = Maps.newHashMap();
        taskArgs.put("config-file", "src/test/resources/test-config.yml");
        monitor.execute(taskArgs, null);
    }
}