/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.instance.Instances;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
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
public class FederationMetricsCollectorTest {

    @Mock
    private TasksExecutionServiceProvider serviceProvider;

    private Map server;
    @Mock
    private MonitorConfiguration monitorConfiguration;

    @Mock
    private MetricWriteHelper metricWriter;

    @Mock
    private MetricDataParser dataParser;

    @Mock
    private MetricsCollectorUtil metricsCollectorUtil;

    private FederationMetricsCollector metricsCollectorTask;

    public static final Logger logger = Logger.getLogger(FederationMetricsCollector.class);

    private Instances instances = initialiseInstances(YmlReader.readFromFile(new File("src/test/resources/test-config.yml")));;

    private Map<String, String> expectedValueMap = new HashMap<String, String>();
    private Map<String, List<Map<String, String>>> allMetricsFromConfig = new HashMap<String, List<Map<String, String>>>();
    private List<Map<String, List<Map<String, String>>>> metricsFromConfig = (List<Map<String, List<Map<String, String>>>>)YmlReader.readFromFile(new File("src/test/resources/test-config.yml")).get("com/appdynamics/extensions/rabbitmq/metrics");
    private List<Metric> metrics = new ArrayList<Metric>();


    @Before
    public void before(){

        Mockito.when(serviceProvider.getMonitorConfiguration()).thenReturn(monitorConfiguration);
        Mockito.when(serviceProvider.getMetricWriteHelper()).thenReturn(metricWriter);

        metricsFromConfig = ((List<Map<String, List<Map<String, String>>>>)YmlReader.readFromFile(new File("src/test/resources/test-config.yml")).get("metrics"));

        for(Map<String, List<Map<String, String>>> metricsConfigEntry: metricsFromConfig){
            allMetricsFromConfig.putAll(metricsConfigEntry);
        }

        dataParser = Mockito.spy(new MetricDataParser(allMetricsFromConfig, "", instances.getQueueGroups(), instances.getExcludeQueueRegex()));

        metricsCollectorTask = Mockito.spy(new FederationMetricsCollector(monitorConfiguration, instances.getInstances()[0], metricWriter, metrics, dataParser));
        metricsCollectorTask.setMetricsCollectorUtil(metricsCollectorUtil);



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
        }).when(metricsCollectorUtil).getOptionalJson(any(CloseableHttpClient.class), anyString(), any(Class.class));
    }

    @Test
    public void checkReturnsFederationStatusWhenAvailable() throws TaskExecutionException {
        expectedValueMap = new HashMap<String, String>();
        initExpectedFederationMetrics();
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(monitorConfiguration).getHttpClient();
        metricsCollectorTask.run();
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    private void initExpectedFederationMetrics() {
        expectedValueMap.put("Federations|myexch1|myexch1_upstream_0|running", "0");
        expectedValueMap.put("Federations|myexch1|myexch1_upstream_1|running", "1");
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


        return instancesObj;
    }
}
