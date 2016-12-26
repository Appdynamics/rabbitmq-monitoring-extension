package com.appdynamics.extensions.rabbitmq;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doAnswer;

public class RabbitMQMonitoringTaskTest {
	private RabbitMQMonitoringTask task;
	public static final Logger logger = Logger.getLogger(RabbitMQMonitoringTaskTest.class);
	private Map<String, String> expectedValueMap = new HashMap<String, String>();
	
	@Test
	public void before(){
		MonitorConfiguration configuration = Mockito.mock(MonitorConfiguration.class);
		InstanceInfo instanceInfo = Mockito.mock(InstanceInfo.class);
		QueueGroup group  = Mockito.mock(QueueGroup.class);
		QueueGroup[] groups = new QueueGroup[]{group};
		task = new RabbitMQMonitoringTask(configuration,instanceInfo,Mockito.anyMap(),groups , "Custom Metrics|");
		doAnswer(new Answer(){

			public Object answer(InvocationOnMock invocation) throws Throwable {
                String actualValue = (String) invocation.getArguments()[0];
                if (expectedValueMap.containsKey(metricName)) {
                    String expectedValue = expectedValueMap.get(metricName);
                    Assert.assertEquals("The value of the metric " + metricName + " failed", expectedValue, actualValue);
                    expectedValueMap.remove(metricName);
                } else {
                    Assert.fail("Unknown Metric " + metricName);
//                    System.out.println("\""+metricName+"\",\""+actualValue+"\"");
                }
                return null;
			}}).when(task).printMetric(anyString(), Mockito.any(BigInteger.class), anyString(), anyString(), anyString());;
		
		
		new Thread(task).start();
	}

}
