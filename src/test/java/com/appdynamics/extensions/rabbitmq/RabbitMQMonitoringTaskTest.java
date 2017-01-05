package com.appdynamics.extensions.rabbitmq;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.SimpleHttpClient;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.Instances;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.appdynamics.extensions.yml.YmlReader;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doAnswer;

public class RabbitMQMonitoringTaskTest {
	private RabbitMQMonitoringTask task;
	public static final Logger logger = Logger.getLogger(RabbitMQMonitoringTaskTest.class);
	private Map<String, String> expectedValueMap = new HashMap<String, String>();
	private Map<String,String> dictionary = new HashMap<String, String>();
	@Before
	public void before(){
		task = Mockito.spy(new RabbitMQMonitoringTask());
        initDictionary();
			doAnswer(new Answer(){

			public Object answer(InvocationOnMock invocation) throws Throwable {
				String actualValue = ((BigInteger) invocation.getArguments()[1]).toString();
				String metricName = (String) invocation.getArguments()[0];
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

			doAnswer(new Answer() {
				public Object answer(InvocationOnMock invocationOnMock) throws Throwable {

					ObjectMapper mapper = new ObjectMapper();
					String url = (String) invocationOnMock.getArguments()[1];
					String file = null;
					if (url.contains("/nodes")) {
						file = "/json/nodes.json";
					} else if (url.contains("/channels")) {
						file = "/json/channels.json";
					} else if (url.contains("/queues")) {
						file = "/json/queues.json";
					}
					logger.info("Returning the mocked data for the api " + file);
					return mapper.readValue(getClass().getResourceAsStream(file), ArrayNode.class);
				}
			}).when(task).getJson(any(CloseableHttpClient.class), anyString());
			doAnswer(new Answer() {
				public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
					ObjectMapper mapper = new ObjectMapper();
					String url = (String) invocationOnMock.getArguments()[1];
					logger.info("Returning the mocked data for the api " + url);
					String file = null;
					if (url.contains("/overview")) {
						file = "/json/overview.json";
						return mapper.readValue(getClass().getResourceAsStream(file), JsonNode.class);
					} else if (url.contains("federation-links")) {
						file = "/json/federation-links.json";
						return mapper.readValue(getClass().getResourceAsStream(file), ArrayNode.class);
					}
					return null;

				}
			}).when(task).getOptionalJson(any(CloseableHttpClient.class), anyString(), any(Class.class));
			
			
	}
    @Test
    public void testNoGroups() throws TaskExecutionException, InterruptedException {
    	expectedValueMap = new HashMap<String, String>();
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedQueueMetrics();
        initExpectedFederationMetrics();
        initExpectedClusterMetrics();
        MonitorConfiguration conf = Mockito.mock(MonitorConfiguration.class);
        task.setConfiguration(conf);
        InstanceInfo info = new InstanceInfo();
        info.setHost("");
        info.setPort(88);
        info.setUsername("");
        info.setPassword("");
        info.setUseSSL(false);
        task.setInfo(info);
        task.setDictionary(dictionary);
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(conf).getHttpClient();
        task.run();
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }
    
    @Test
    public void testWithGroupsNoIndividual() throws TaskExecutionException {
    	expectedValueMap = new HashMap<String, String>();
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedGroupMetrics();
        initExpectedFederationMetrics();
        initExpectedClusterMetrics();
        Instances instances = YmlReader.read(getClass().getResourceAsStream("/test-config.yml"), Instances.class);
        
        MonitorConfiguration conf = Mockito.mock(MonitorConfiguration.class);
        task.setConfiguration(conf);
        task.setDictionary(dictionary);
        task.setInfo(instances.getInstances()[0]);
        task.setQueueGroups(instances.getQueueGroups());
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(conf).getHttpClient();
        task.run();
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }
    
    @Test
    public void testWithGroupsWithIndividual() throws TaskExecutionException {
    	expectedValueMap = new HashMap<String, String>();
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedGroupMetrics();
        initExpectedQueueMetrics();
        initExpectedFederationMetrics();
        initExpectedClusterMetrics();
        Instances instances = YmlReader.read(getClass().getResourceAsStream("/test-config.yml"), Instances.class);
        instances.getQueueGroups()[0].setShowIndividualStats(true);
        MonitorConfiguration conf = Mockito.mock(MonitorConfiguration.class);
        task.setConfiguration(conf);
        task.setDictionary(dictionary);
        task.setInfo(instances.getInstances()[0]);
        task.setQueueGroups(instances.getQueueGroups());
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(conf).getHttpClient();
        task.run();
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }
    
    @Test
    public void checkReturnsFederationStatusWhenAvailable() throws TaskExecutionException {
    	expectedValueMap = new HashMap<String, String>();
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedQueueMetrics();
        initExpectedFederationMetrics();
        initExpectedClusterMetrics();
        Instances instances = YmlReader.read(getClass().getResourceAsStream("/test-config.yml"), Instances.class);
        
        MonitorConfiguration conf = Mockito.mock(MonitorConfiguration.class);
        task.setConfiguration(conf);
        task.setDictionary(dictionary);
        task.setInfo(instances.getInstances()[0]);
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(conf).getHttpClient();
        task.run();
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }
    
    private void initDictionary() {
		dictionary = new HashMap<String, String>();
		dictionary.put("ack", "Acknowledged");
		dictionary.put("deliver", "Delivered");
		dictionary.put("deliver_get", "Delivered (Total)");
		dictionary.put("deliver_no_ack", "Delivered No-Ack");
		dictionary.put("get", "Got");
		dictionary.put("get_no_ack", "Got No-Ack");
		dictionary.put("publish", "Published");
		dictionary.put("redeliver", "Redelivered");
		dictionary.put("messages_ready", "Available");
		dictionary.put("messages_unacknowledged", "Pending Acknowledgements");
		dictionary.put("consumers", "Count");
		dictionary.put("active_consumers", "Active");
		dictionary.put("idle_consumers", "Idle");
		dictionary.put("slave_nodes", "Slaves Count");
		dictionary.put("synchronised_slave_nodes", "Synchronized Slaves Count");
		dictionary.put("down_slave_nodes", "Down Slaves Count");
		dictionary.put("messages", "Messages");
		
	}
	private void initExpectedClusterMetrics(){
        expectedValueMap.put("Clusters|rabbit@rabbit1|Messages|Published","41");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Messages|Acknowledged","42");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Messages|Delivered (Total)","43");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Messages|Delivered","44");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Queues|Messages","1");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Queues|Available","2");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Queues|Pending Acknowledgements","3");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Objects|consumers","5");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Objects|queues","2");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Objects|exchanges","8");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Objects|connections","6");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Objects|channels","7");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Nodes|Total","1");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Nodes|Running","0");
        expectedValueMap.put("Clusters|rabbit@rabbit1|Cluster Health","0");
        expectedValueMap.put("Availability","1");
    }

    private void initExpectedFederationMetrics() {
        expectedValueMap.put("Federations|myexch1|myexch1_upstream_0|running", "0");
        expectedValueMap.put("Federations|myexch1|myexch1_upstream_1|running", "1");
    }

    private void initExpectedQueueMetrics() {
        expectedValueMap.put("Queues|Default|queue.user.save|Consumers", "5");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Available", "60");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Pending Acknowledgements", "70");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Acknowledged", "10");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Delivered (Total)", "30");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Delivered", "20");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Delivered No-Ack", "25");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Got", "5");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Got No-Ack", "15");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Published", "40");
        expectedValueMap.put("Queues|Default|queue.user.save|Messages|Redelivered", "35");
        expectedValueMap.put("Queues|Default|queue.user.save|Replication|Synchronized Slaves Count", "0");
        expectedValueMap.put("Queues|Default|queue.user.save|Replication|Down Slaves Count", "0");
        expectedValueMap.put("Queues|Default|queue.user.save|Replication|Slaves Count", "0");

        expectedValueMap.put("Queues|Default|queue.user.save-2|Consumers", "1");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Available", "16");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Pending Acknowledgements", "17");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Acknowledged", "11");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Delivered (Total)", "13");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Delivered", "12");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Delivered No-Ack", "26");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Got", "6");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Got No-Ack", "16");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Published", "14");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Messages|Redelivered", "36");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Replication|Synchronized Slaves Count", "1");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Replication|Down Slaves Count", "0");
        expectedValueMap.put("Queues|Default|queue.user.save-2|Replication|Slaves Count", "1");
    }

    private void initExpectedSummaryMetrics() {
        expectedValueMap.put("Summary|Channels", "2");
        expectedValueMap.put("Summary|Consumers", "6");
        expectedValueMap.put("Summary|Messages|Delivered (Total)", String.valueOf(30 + 13));
        expectedValueMap.put("Summary|Messages|Published", String.valueOf(14 + 40));
        expectedValueMap.put("Summary|Queues", "2");
        expectedValueMap.put("Summary|Messages|Available", "76");
        expectedValueMap.put("Summary|Messages|Redelivered", "71");
        expectedValueMap.put("Summary|Messages|Pending Acknowledgements", "87");
    }

    private void initExpectedGroupMetrics() {
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Redelivered", "71");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Delivered", "32");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Delivered (Total)", "43");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Published", "54");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Delivered No-Ack", "51");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Got", "11");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Available", "76");
        expectedValueMap.put("Queue Groups|Default|group1|Consumers", "6");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Acknowledged", "21");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Got No-Ack", "31");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|Pending Acknowledgements", "87");
    }

    private void initExpectedNodeMetrics() {
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Erlang Processes", "215");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Disk Free Alarm Activated", "0");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Memory Free Alarm Activated", "1");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Memory(MB)", "21");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Sockets", "3");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Channels|Count", "2");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Channels|Blocked", "0");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Delivered", String.valueOf(33 + 34));
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Acknowledged", String.valueOf(23 + 24));
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Consumers|Count", "6");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|File Descriptors", "0");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Got No-Ack", "0");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Delivered No-Ack", "0");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Redelivered", "0");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Published", "0");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Available", "76");
        expectedValueMap.put("Nodes|rabbit@ABEY-WIN7-32|Messages|Pending Acknowledgements", "87");
    }
    

}
