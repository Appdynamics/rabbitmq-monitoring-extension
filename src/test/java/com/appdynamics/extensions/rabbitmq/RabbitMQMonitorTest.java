package com.appdynamics.extensions.rabbitmq;

import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

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
    private final RabbitMQMonitor rabbitMonitor;
    private Map<String,String> expectedValueMap = new HashMap<String, String>();

    public RabbitMQMonitorTest() {
        rabbitMonitor = Mockito.spy(new RabbitMQMonitor());
        //When it asks for a writer mock a writer and return
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return createMockWriter((String)invocationOnMock.getArguments()[0]);
            }
        }).when(rabbitMonitor).getMetricWriter(anyString(), anyString(), anyString(), anyString());

        //When it invokes the API, read the json for the api and return.
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                String url = (String) invocationOnMock.getArguments()[0];
                String file = null;
                if(url.contains("/nodes")){
                    file = "/json/nodes.json";
                } else if(url.contains("/channels")){
                    file = "/json/channels.json";
                } else if(url.contains("/queues")){
                    file = "/json/queues.json";
                }
                logger.info("Returning the mocked data for the api "+file);
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(getClass().getResourceAsStream(file),ArrayNode.class);
            }
        }).when(rabbitMonitor).invokeApi(anyString(), anyString());
        initExpectedValueMap();
    }

    private void initExpectedValueMap() {
        //Only the important aggregated metrics
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Erlang Processes","215");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Memory(MB)","21");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Sockets","3");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Channels|Count","2");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Channels|Blocked","0");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Delivered","1288");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Messages|Acknowledged","1288");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Nodes|rabbit@ABEY-WIN7-32|Consumers|Count","2");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Channels","2");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Consumers","2");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Messages|Delivered (Total)","1288");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Messages|Published","1288");
        expectedValueMap.put("Custom Metrics|RabbitMQ|Summary|Queues","2");
    }

    private Object createMockWriter(final String metricName) {
        MetricWriter mock = Mockito.mock(MetricWriter.class);
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                String actualValue = (String) invocationOnMock.getArguments()[0];
                if(expectedValueMap.containsKey(metricName)){
                    String expectedValue = expectedValueMap.get(metricName);
                    Assert.assertEquals("The value of the metric "+metricName+" failed",expectedValue, actualValue);
                }
                return null;
            }
        }).when(mock).printMetric(anyString());
        return mock;
    }

    @Test
    public void test() throws TaskExecutionException {
        rabbitMonitor.execute(new HashMap<String, String>(),null);
    }

    @Test
    public void checkArgsTest(){
        Map<String, String> map = rabbitMonitor.checkArgs(null);
        Assert.assertEquals("guest",map.get("username"));
        Assert.assertEquals("guest",map.get("password"));
        Assert.assertEquals("localhost",map.get("host"));
        Assert.assertEquals("15672",map.get("port"));
        Assert.assertEquals("false",map.get("useSSL"));
        Assert.assertEquals("Custom Metrics|RabbitMQ|",map.get("metricPrefix"));

        Map<String,String> argsMap = new HashMap<String, String>();
        argsMap.put("username", "userx");
        argsMap.put("password", "passwordx");
        argsMap.put("host", "192x");
        argsMap.put("port", "15672x");
        argsMap.put("useSSL", "falsex");
        argsMap.put("metricPrefix", "X|Custom Metrics|RabbitMQ|");
        map = rabbitMonitor.checkArgs(argsMap);
        Assert.assertEquals("userx",map.get("username"));
        Assert.assertEquals("passwordx",map.get("password"));
        Assert.assertEquals("192x",map.get("host"));
        Assert.assertEquals("15672x",map.get("port"));
        Assert.assertEquals("falsex",map.get("useSSL"));
        Assert.assertEquals("X|Custom Metrics|RabbitMQ|",map.get("metricPrefix"));
    }

    public static void main(String[] args) {

    }
}
