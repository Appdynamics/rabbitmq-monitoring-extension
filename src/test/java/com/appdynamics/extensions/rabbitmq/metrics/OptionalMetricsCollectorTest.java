/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.instance.Instances;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.appdynamics.extensions.util.YmlUtils;
import com.appdynamics.extensions.yml.YmlReader;
import com.google.common.base.Strings;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.http.impl.client.CloseableHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;


@RunWith(PowerMockRunner.class)
@PrepareForTest(HttpClientUtils.class)
public class OptionalMetricsCollectorTest {

    @Mock
    private TasksExecutionServiceProvider serviceProvider;

    @Mock
    private MonitorContextConfiguration monitorContextConfiguration;

    @Mock
    private MonitorContext monitorContext;

    @Mock
    private MetricWriteHelper metricWriter;

    @Mock
    private MetricDataParser dataParser;

    @Mock
    private Stat stat;

    @Mock
    private MetricsCollectorUtil metricsCollectorUtil;

    @Mock
    private Phaser phaser;

    private OptionalMetricsCollector metricsCollectorTask;

    public static final Logger logger = ExtensionsLoggerFactory.getLogger(OptionalMetricsCollector.class);

    private Instances instances = initialiseInstances(YmlReader.readFromFile(new File("src/test/resources/test-config.yml")));

    private Map<String, String> expectedValueMap = new HashMap<String, String>();

    private List<Metric> metrics = new ArrayList<Metric>();

    private Map nodeFilters;


    @Before
    public void before(){

        Mockito.when(serviceProvider.getMetricWriteHelper()).thenReturn(metricWriter);
        Mockito.when(stat.getAlias()).thenReturn("FederationLinks");
        Mockito.when(stat.getUrl()).thenReturn("/api/federation-links");
        Mockito.when(monitorContextConfiguration.getContext()).thenReturn(monitorContext);

        nodeFilters = (Map) YmlUtils.getNestedObject(monitorContextConfiguration.getConfigYml(), "filter", "nodes");

        dataParser = Mockito.spy(new MetricDataParser("", monitorContextConfiguration, nodeFilters));

        metricsCollectorTask = Mockito.spy(new OptionalMetricsCollector(stat, monitorContextConfiguration.getContext(), instances.getInstances()[0], metricWriter, dataParser, "true", phaser));
        metricsCollectorTask.setMetricsCollectorUtil(metricsCollectorUtil);

        PowerMockito.mockStatic(HttpClientUtils.class);

        PowerMockito.when(HttpClientUtils.getResponseAsJson(any(CloseableHttpClient.class), anyString(), any(Class.class))).thenAnswer(
                new Answer() {
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper mapper = new ObjectMapper();
                        String url = (String) invocationOnMock.getArguments()[1];
                        logger.info("Returning the mocked data for the api " + url);
                        String file = null;
                        if (url.contains("federation-links")) {
                            file = "/json/federation-links.json";
                            return mapper.readValue(getClass().getResourceAsStream(file), ArrayNode.class);
                        }
                        return null;
                    }
                });
    }

    @Test
    public void checkReturnsFederationStatusWhenAvailable() throws TaskExecutionException {
        expectedValueMap = new HashMap<String, String>();
        initExpectedFederationMetrics();
        Mockito.doReturn(Mockito.mock(CloseableHttpClient.class)).when(monitorContext).getHttpClient();
        metricsCollectorTask.run();
        for(Metric metric: metricsCollectorTask.getMetrics()) {

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
        Assert.assertTrue("The expected values were not send. The missing values are " + expectedValueMap,
                 expectedValueMap.isEmpty());
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
                instancesToSet[index++] = info;
            }
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
