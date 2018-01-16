package com.appdynamics.extensions.rabbitmq;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.Instances;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.appdynamics.extensions.yml.YmlReader;
import com.google.common.base.Strings;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;

@RunWith(MockitoJUnitRunner.class)
public class RabbitMQMonitoringTaskTest {

    @Mock
    private MetricWriteHelper metricWriter;

	private RabbitMQMonitoringTask task;
	public static final Logger logger = Logger.getLogger(RabbitMQMonitoringTaskTest.class);
	private Map<String, String> expectedValueMap = new HashMap<String, String>();
	private Map<String,String> dictionary = new HashMap<String, String>();
    private Map<String, List<Map<String, String>>> allMetricsFromConfig = new HashMap<String, List<Map<String, String>>>();
    private List<Metric> metrics = new ArrayList<Metric>();


	@Before
	public void before(){
		task = Mockito.spy(new RabbitMQMonitoringTask());

        task.setMetricWriteHelper(metricWriter);
        initDictionary();
			doAnswer(new Answer(){

			public Object answer(InvocationOnMock invocation) throws Throwable {
			    for(Metric metric: metrics) {

                    String actualValue = metric.getMetricValue();
                    String metricName = metric.getMetricPath();
                    if (expectedValueMap.containsKey(metricName)) {
                        String expectedValue = expectedValueMap.get(metricName);
                        Assert.assertEquals("The value of the metric " + metricName + " failed", expectedValue, actualValue);
                        expectedValueMap.remove(metricName);
                    } else {
                        System.out.println("\"" + metricName + "\",\"" + actualValue + "\"");
                        Assert.fail("Unknown Metric " + metricName);
                    }
                }
				return null;
			}}).when(metricWriter).transformAndPrintMetrics(metrics);

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
    public void testWithGroupsNoIndividual() throws TaskExecutionException {

    	expectedValueMap = new HashMap<String, String>();
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedGroupMetrics();
        initExpectedFederationMetrics();
        initExpectedClusterMetrics();

        Instances instances = initialiseInstances(YmlReader.readFromFile(new File("src/test/resources/test-config.yml")));


        MonitorConfiguration conf = Mockito.mock(MonitorConfiguration.class);
        task.setConfiguration(conf);
        task.setDictionary(dictionary);
        task.setMetricsFromConfig((List<Map<String, List<Map<String, String>>>>)YmlReader.readFromFile(new File("src/test/resources/test-config.yml")).get("metrics"));
        task.setAllMetricsFromConfig(allMetricsFromConfig);
        task.setInfo(instances.getInstances()[0]);
        task.setQueueGroups(instances.getQueueGroups());
        task.setMetricPrefix("");
        task.setMetrics(metrics);

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
        Instances instances = initialiseInstances(YmlReader.readFromFile(new File("src/test/resources/test-config.yml")));
        instances.getQueueGroups()[0].setShowIndividualStats(true);
        MonitorConfiguration conf = Mockito.mock(MonitorConfiguration.class);
        task.setConfiguration(conf);
        task.setDictionary(dictionary);
        task.setMetricsFromConfig((List<Map<String, List<Map<String, String>>>>)YmlReader.readFromFile(new File("src/test/resources/test-config.yml")).get("metrics"));
        task.setAllMetricsFromConfig(allMetricsFromConfig);
        task.setInfo(instances.getInstances()[0]);
        task.setQueueGroups(instances.getQueueGroups());
        task.setMetricPrefix("");
        task.setMetrics(metrics);
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
        Instances instances = initialiseInstances(YmlReader.readFromFile(new File("src/test/resources/test-config.yml")));
        
        MonitorConfiguration conf = Mockito.mock(MonitorConfiguration.class);
        task.setConfiguration(conf);
        task.setDictionary(dictionary);
        task.setMetricsFromConfig((List<Map<String, List<Map<String, String>>>>)YmlReader.readFromFile(new File("src/test/resources/test-config.yml")).get("metrics"));
        task.setAllMetricsFromConfig(allMetricsFromConfig);
        task.setInfo(instances.getInstances()[0]);
        task.setMetricPrefix("");
        task.setMetrics(metrics);
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
        expectedValueMap.put("Clusters|rabbitmqCluster|Messages|Published","31");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Messages|Published Delta","0");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Messages|Acknowledged","29");
        expectedValueMap.put("Clusters|rabbitmqCluster|Messages|Delivered (Total)","32");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Messages|Delivered (Total) Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Queues|Messages","13");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Queues|Messages Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Queues|Available","13");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Queues|Available Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Queues|Pending Acknowledgements","7");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Queues|Pending Acknowledgements Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Objects|queues","3");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Objects|queues Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Objects|exchanges","11");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Objects|exchanges Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Objects|connections","3");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Objects|connections Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Objects|channels","1");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Objects|channels Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Objects|consumers","1");
        //expectedValueMap.put("Clusters|rabbitmqCluster|Objects|consumers Delta","0");
        expectedValueMap.put("Clusters|rabbitmqCluster|Nodes|Total","1");
        expectedValueMap.put("Clusters|rabbitmqCluster|Nodes|Running","1");
        expectedValueMap.put("Clusters|rabbitmqCluster|Cluster Health","1");
        expectedValueMap.put("Availability","1");
    }

    private void initExpectedFederationMetrics() {
        expectedValueMap.put("Federations|myexch1|myexch1_upstream_0|running", "0");
        expectedValueMap.put("Federations|myexch1|myexch1_upstream_1|running", "1");
    }

    private void initExpectedQueueMetrics() {
        expectedValueMap.put("Queues|Default|node1q1consumers", "1");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Available", "36");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Available Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Pending Acknowledgements", "50");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Pending Acknowledgements Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Acknowledged", "8");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Acknowledged Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Delivered (Total)", "20");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Delivered (Total) Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Delivered", "18");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Delivered Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Delivered No-Ack", "20");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Delivered No-Ack Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Got", "6");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Got Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Got No-Ack", "12");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Published", "30");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Published Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Messages|Redelivered", "25");
        //expectedValueMap.put("Queues|Default|node1q1|Messages|Redelivered Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Replication|Synchronized Slaves Count", "0");
        //expectedValueMap.put("Queues|Default|node1q1|Replication|Synchronized Slaves Count Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Replication|Down Slaves Count", "0");
        //expectedValueMap.put("Queues|Default|node1q1|Replication|Down Slaves Count Delta", "0");
        expectedValueMap.put("Queues|Default|node1q1|Replication|Slaves Count", "0");
        //expectedValueMap.put("Queues|Default|node1q1|Replication|Slaves Count Delta", "0");
        /*expectedValueMap.put("Summary|Messages|Available", "37");
        expectedValueMap.put("Summary|Messages|Delivered (Total)", "23");
        expectedValueMap.put("Summary|Messages|Published", "34");
        expectedValueMap.put("Summary|Messages|Redelivered", "28");
        expectedValueMap.put("Summary|Messages|Pending Acknowledgements", "52");
        expectedValueMap.put("Summary|Queues", "2");*/



        expectedValueMap.put("Queues|Default|node2q2consumers", "1");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Available", "1");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Available Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Pending Acknowledgements", "2");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Pending Acknowledgements Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Acknowledged", "0");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Acknowledged Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Delivered (Total)", "3");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Delivered (Total) Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Delivered", "3");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Delivered Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Delivered No-Ack", "1");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Delivered No-Ack Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Got", "0");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Got Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Got No-Ack", "1");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Published", "4");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Published Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Messages|Redelivered", "3");
        //expectedValueMap.put("Queues|Default|node2q2|Messages|Redelivered Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Replication|Synchronized Slaves Count", "0");
        //expectedValueMap.put("Queues|Default|node2q2|Replication|Synchronized Slaves Count Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Replication|Down Slaves Count", "0");
        //expectedValueMap.put("Queues|Default|node2q2|Replication|Down Slaves Count Delta", "0");
        expectedValueMap.put("Queues|Default|node2q2|Replication|Slaves Count", "0");
        //expectedValueMap.put("Queues|Default|node2q2|Replication|Slaves Count Delta", "0");

    }

    private void initExpectedSummaryMetrics() {
        expectedValueMap.put("|Summary|Channels", "2");
        expectedValueMap.put("Summary|Consumers", "2");
        expectedValueMap.put("Summary|Queues", "2");
        expectedValueMap.put("Summary|Messages|Delivered (Total)", "23");
        expectedValueMap.put("Summary|Messages|Published", "34");
        expectedValueMap.put("Summary|Messages|Available", "37");
        expectedValueMap.put("Summary|Messages|Redelivered", "28");
        expectedValueMap.put("Summary|Messages|Pending Acknowledgements", "52");
    }

    private void initExpectedGroupMetrics() {
        expectedValueMap.put("Queue Groups|Default|group1|Messages|redeliver", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|redeliver Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|deliver", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|deliver Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|deliver_get", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|deliver_get Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|publish", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|publish Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|deliver_no_ack", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|deliver_no_ack Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|get", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|get Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1messages_ready", "2");
        //expectedValueMap.put("Queue Groups|Default|group1messages_ready Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1consumers", "2");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|ack", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|ack Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1|Messages|get_no_ack", "2");
        //expectedValueMap.put("Queue Groups|Default|group1|Messages|get_no_ack Delta", "0");
        expectedValueMap.put("Queue Groups|Default|group1messages_unacknowledged", "2");
        //expectedValueMap.put("Queue Groups|Default|group1messages_unacknowledged Delta", "0");
    }

    private void initExpectedNodeMetrics() {
        expectedValueMap.put("Nodes|rabbit@rabbit1|Erlang Processes", "210");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Erlang Processes Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Disk Free Alarm Activated", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Disk Free Alarm Activated Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Memory Free Alarm Activated", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Memory Free Alarm Activated Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Memory(MB)", "121");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Memory(MB) Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Sockets", "3");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Sockets Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Delivered", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Delivered Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Acknowledged", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Acknowledged Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Consumers|Count", "1");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Consumers|Count Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|File Descriptors", "26");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|File Descriptors Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Got No-Ack", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Got No-Ack Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Delivered No-Ack", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Delivered No-Ack Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Redelivered", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Redelivered Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Published", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Published Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Available", "36");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Available Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Pending Acknowledgements", "50");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Messages|Pending Acknowledgements Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Channels|Count", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Channels|Count Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Summary|Channels", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Summary|Channels Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Summary|Consumers", "0");
        //expectedValueMap.put("Nodes|rabbit@rabbit1|Summary|Consumers Delta", "0");
        expectedValueMap.put("Nodes|rabbit@rabbit1|Channels|Blocked", "0");
    }


    private Instances initialiseInstances(Map<String, ?> configYml) {

	    Instances instancesObj = new Instances();
        List<Map<String,?>> instances = (List<Map<String, ?>>) configYml.get("servers");
        if(instances!=null && instances.size()>0){
            int index = 0;
            InstanceInfo[] instancesToSet = new InstanceInfo[instances.size()];
            for(Map<String,?> instance : instances){
                InstanceInfo info = new InstanceInfo();
                if(Strings.isNullOrEmpty((String) instance.get("displayName"))){
                    logger.error("Display name not mentioned for server ");
                    throw new RuntimeException("Display name not mentioned for server");
                }
                else{
                    info.setDisplayName((String) instance.get("displayName"));
                }
                if(!Strings.isNullOrEmpty((String) instance.get("host"))){
                    info.setHost((String) instance.get("host"));
                }
                else{
                    info.setHost("localhost");
                }
                if(!Strings.isNullOrEmpty((String) instance.get("username"))){
                    info.setUsername((String) instance.get("username"));
                }
                else{
                    info.setUsername("guest");
                }

                if(!Strings.isNullOrEmpty((String) instance.get("password"))){
                    info.setPassword((String) instance.get("password"));
                }
                else{
                    info.setPassword("guest");
                }
                if(instance.get("port")!=null){
                    info.setPort((Integer) instance.get("port"));
                }
                else{
                    info.setPort(15672);
                }
                if(instance.get("useSSL")!=null){
                    info.setUseSSL((Boolean) instance.get("useSSL"));
                }
                else{
                    info.setUseSSL(false);
                }
                if(instance.get("connectTimeout")!=null){
                    info.setConnectTimeout((Integer) instance.get("connectTimeout"));
                }
                else{
                    info.setConnectTimeout(10000);
                }
                if(instance.get("socketTimeout")!=null){
                    info.setSocketTimeout((Integer) instance.get("connectTimeout"));
                }
                else{
                    info.setSocketTimeout(10000);
                }
                instancesToSet[index++] = info;
            }
            instancesObj.setExcludeQueueRegex((String) configYml.get("excludeQueueRegex"));
            instancesObj.setInstances(instancesToSet);
        }
        else{
            logger.error("no instances configured");
        }
        List<Map<String,?>> queueGroups = (List<Map<String, ?>>) configYml.get("queueGroups");
        if(queueGroups!=null && queueGroups.size()>0){
            int index = 0;
            QueueGroup[] groups =new QueueGroup[queueGroups.size()];
            for(Map<String,?> group : queueGroups){
                QueueGroup g = new QueueGroup();
                g.setGroupName((String) group.get("groupName"));
                g.setQueueNameRegex((String) group.get("queueNameRegex"));
                g.setShowIndividualStats((Boolean) group.get("showIndividualStats"));
                groups[index++] = g;
            }
            instancesObj.setQueueGroups(groups);
        }
        else{
            logger.debug("no queue groups defined");
        }

        dictionary.putAll((Map<String, String>)configYml.get("dictionary"));

        return instancesObj;
    }
}
