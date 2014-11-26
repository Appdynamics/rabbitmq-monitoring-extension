package com.appdynamics.extensions.rabbitmq;

import com.appdynamics.extensions.http.SimpleHttpClient;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.appdynamics.extensions.yml.YmlReader;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Created with IntelliJ IDEA.
 * User: abey.tom
 * Date: 11/21/13
 * Time: 9:57 AM
 * To change this template use File | Settings | File Templates.
 */
public class RabbitMQMonitorTest {
    public static final Logger logger = Logger.getLogger(RabbitMQMonitorTest.class);
    private RabbitMQMonitor rabbitMonitor;
    private Map<String, String> expectedValueMap;

    @Before
    public void before() {
        rabbitMonitor = Mockito.spy(new RabbitMQMonitor());
        //When it asks for a writer mock a writer and return
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return createMockWriter((String) invocationOnMock.getArguments()[0]);
            }
        }).when(rabbitMonitor).getMetricWriter(anyString(), anyString(), anyString(), anyString());

        //When it invokes the API, read the json for the api and return.
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
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
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(getClass().getResourceAsStream(file), ArrayNode.class);
            }
        }).when(rabbitMonitor).getJson(any(SimpleHttpClient.class), anyString());
        expectedValueMap = new HashMap<String, String>();
    }

    private Object createMockWriter(final String metricName) {
        MetricWriter mock = Mockito.mock(MetricWriter.class);
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                String actualValue = (String) invocationOnMock.getArguments()[0];
                if (expectedValueMap.containsKey(metricName)) {
                    String expectedValue = expectedValueMap.get(metricName);
                    Assert.assertEquals("The value of the metric " + metricName + " failed", expectedValue, actualValue);
                    expectedValueMap.remove(metricName);
                } else {
                    Assert.fail("Unknown Metric " + metricName);
                }
                return null;
            }
        }).when(mock).printMetric(anyString());
        return mock;
    }

    @Test
    public void testNoGroups() throws TaskExecutionException {
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedQueueMetrics();
        rabbitMonitor.execute(new HashMap<String, String>(), null);
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    @Test
    public void testWithGroupsNoIndividual() throws TaskExecutionException {
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedGroupMetrics();
        rabbitMonitor.queueGroups = YmlReader.read(getClass().getResourceAsStream("/test-config.yml"), QueueGroup[].class);
        rabbitMonitor.execute(new HashMap<String, String>(), null);
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    @Test
    public void testWithGroupsWithIndividual() throws TaskExecutionException {
        initExpectedNodeMetrics();
        initExpectedSummaryMetrics();
        initExpectedGroupMetrics();
        initExpectedQueueMetrics();
        rabbitMonitor.queueGroups = YmlReader.read(getClass().getResourceAsStream("/test-config.yml"), QueueGroup[].class);
        rabbitMonitor.queueGroups[0].setShowIndividualStats(true);
        rabbitMonitor.execute(new HashMap<String, String>(), null);
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    @Test
    public void checkArgsTest() {
        Map<String, String> map = rabbitMonitor.checkArgs(null);
        Assert.assertEquals("guest", map.get("username"));
        Assert.assertEquals("guest", map.get("password"));
        Assert.assertEquals("localhost", map.get("host"));
        Assert.assertEquals("15672", map.get("port"));
        Assert.assertEquals("false", map.get("useSSL"));
        Assert.assertEquals("Custom Metrics|RabbitMQ|", map.get("metricPrefix"));

        Map<String, String> argsMap = new HashMap<String, String>();
        argsMap.put("username", "userx");
        argsMap.put("password", "passwordx");
        argsMap.put("host", "192x");
        argsMap.put("port", "15672x");
        argsMap.put("useSSL", "falsex");
        argsMap.put("metricPrefix", "X|Custom Metrics|RabbitMQ|");
        map = rabbitMonitor.checkArgs(argsMap);
        Assert.assertEquals("userx", map.get("username"));
        Assert.assertEquals("passwordx", map.get("password"));
        Assert.assertEquals("192x", map.get("host"));
        Assert.assertEquals("15672x", map.get("port"));
        Assert.assertEquals("falsex", map.get("useSSL"));
        Assert.assertEquals("X|Custom Metrics|RabbitMQ|", map.get("metricPrefix"));
    }

    private void initExpectedQueueMetrics() {
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Consumers", "5");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Available", "60");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Pending Acknowledgements", "70");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Acknowledged", "10");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Delivered (Total)", "30");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Delivered", "20");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Delivered No-Ack", "25");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Got", "5");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Got No-Ack", "15");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Published", "40");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save|Messages|Redelivered", "35");

        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Consumers", "1");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Available", "16");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Pending Acknowledgements", "17");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Acknowledged", "11");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Delivered (Total)", "13");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Delivered", "12");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Delivered No-Ack", "26");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Got", "6");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Got No-Ack", "16");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Published", "14");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queues|Default|queue.user.save-2|Messages|Redelivered", "36");
    }

    private void initExpectedSummaryMetrics() {
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Channels", "2");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Consumers", "6");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Messages|Delivered (Total)", String.valueOf(30 + 13));
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Messages|Published", String.valueOf(14 + 40));
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Queues", "2");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Messages|Available", "76");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Messages|Redelivered", "71");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Messages|Pending Acknowledgements", "87");
    }

    private void initExpectedGroupMetrics() {
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Redelivered", "71");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Delivered", "32");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Delivered (Total)", "43");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Published", "54");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Delivered No-Ack", "51");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Got", "11");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Available", "76");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Consumers", "6");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Acknowledged", "21");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Got No-Ack", "31");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Queue Groups|Default|group1|Messages|Pending Acknowledgements", "87");
    }

    private void initExpectedNodeMetrics() {
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Erlang Processes", "215");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Disk Free Alarm Activated", "0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Memory Free Alarm Activated", "1");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Memory(MB)", "21");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Sockets", "3");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Channels|Count", "2");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Channels|Blocked", "0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Delivered", String.valueOf(33 + 34));
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Acknowledged", String.valueOf(23 + 24));
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Consumers|Count", "6");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|File Descriptors", "0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Got No-Ack", "0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Delivered No-Ack", "0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Redelivered", "0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Published", "0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Available", "76");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Pending Acknowledgements", "87");
    }
}
