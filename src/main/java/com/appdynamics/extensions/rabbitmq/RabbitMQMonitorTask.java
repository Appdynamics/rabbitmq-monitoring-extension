package com.appdynamics.extensions.rabbitmq;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TaskInputArgs;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.instance.Instances;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.google.common.collect.Lists;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RabbitMQMonitorTask implements AMonitorTaskRunnable{

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RabbitMQMonitorTask.class);

    private MonitorConfiguration configuration;

    private String displayName;

    /* server properties */
    private InstanceInfo instanceInfo;

    /* a facade to report metrics to the machine agent.*/
    private MetricWriteHelper metricWriter;

    private QueueGroup[] queueGroups;

    private String metricPrefix;

    private String excludeQueueRegex;

    private List<Map<String, List<Map<String, String>>>> metricsFromConfig;

    private List<Metric> metrics;

    private Map<String, String> endpointFlagsMap;

    private Map<String, List<Map<String, String>>> allMetricsFromConfig;

    public void setAllMetricsFromConfig(Map<String, List<Map<String, String>>> allMetricsFromConfig) {
        this.allMetricsFromConfig = allMetricsFromConfig;
    }

    public void setMetricsFromConfig(List<Map<String, List<Map<String, String>>>> metricsFromConfig) {
        this.metricsFromConfig = metricsFromConfig;
    }

    public void setQueueGroups(QueueGroup[] queueGroups) {
        this.queueGroups = queueGroups;
    }

    public void setMetricPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
    }


    public void setMetrics(List<Metric> metrics) {
        this.metrics = metrics;
    }

    public void setEndpointFlagsMap(Map<String, String> endpointFlagsMap) {
        this.endpointFlagsMap = endpointFlagsMap;
    }


    RabbitMQMonitorTask(TasksExecutionServiceProvider serviceProvider, InstanceInfo instanceInfo, Instances instances) {
        this.configuration = serviceProvider.getMonitorConfiguration();
        this.instanceInfo = instanceInfo;
        this.metricWriter = serviceProvider.getMetricWriteHelper();
        this.metricPrefix = configuration.getMetricPrefix();
        this.excludeQueueRegex = instances.getExcludeQueueRegex();
        this.metricsFromConfig = (List<Map<String, List<Map<String, String>>>>) configuration.getConfigYml().get("metrics");
        this.endpointFlagsMap = (Map<String, String>) configuration.getConfigYml().get("endpointFlags");
        this.displayName = instanceInfo.getDisplayName();
        this.metrics = Lists.newArrayList();
        this.queueGroups = instances.getQueueGroups();
        allMetricsFromConfig = new HashMap<String, List<Map<String, String>>>();
    }


    public void run() {
        try {

            MetricDataParser dataParser = new MetricDataParser(allMetricsFromConfig, metricPrefix, queueGroups, excludeQueueRegex);

            String nodeUrl = UrlBuilder.builder(getUrlParametersMap(instanceInfo)).path("/api/nodes").build();
            ArrayNode nodes = getJson(this.configuration.getHttpClient(), nodeUrl);

            String channelUrl = UrlBuilder.builder(getUrlParametersMap(instanceInfo)).path("/api/channels").build();
            ArrayNode channels = getJson(this.configuration.getHttpClient(), channelUrl);

            String apiUrl = UrlBuilder.builder(getUrlParametersMap(instanceInfo)).path("/api/queues").build();
            ArrayNode queues = getJson(this.configuration.getHttpClient(), apiUrl);

            populateMetricsMap();
            metrics.addAll(dataParser.process(nodes, channels, queues));

            if(endpointFlagsMap.get("federationPlugin").equalsIgnoreCase("true")) {
                String federationLinkUrl = UrlBuilder.builder(getUrlParametersMap(instanceInfo)).path("/api/federation-links").build();
                ArrayNode federationLinks = getOptionalJson(this.configuration.getHttpClient(), federationLinkUrl, ArrayNode.class);
                metrics.addAll(dataParser.parseFederationData(federationLinks));

            }
            if(endpointFlagsMap.get("overview").equalsIgnoreCase("true")) {
                String overviewUrl = UrlBuilder.builder(getUrlParametersMap(instanceInfo)).path("/api/overview").build();
                JsonNode overview = getOptionalJson(this.configuration.getHttpClient(), overviewUrl, JsonNode.class);
                metrics.addAll(dataParser.parseOverviewData(overview, nodes));
            }

            if (metrics != null && metrics.size() > 0) {
                logger.debug("Printing rabbitmq metrics list of size " + metrics.size());
                metricWriter.transformAndPrintMetrics(metrics);
            }

            logger.info("Completed the RabbitMQ Metric Monitoring task");
        } catch (Exception e) {
            metrics.add(new Metric("Availability", String.valueOf(BigInteger.ZERO), metricPrefix + "Availability"));
            logger.error("Unexpected error while running the RabbitMQ Monitor", e);
        }

    }

    public void onTaskComplete() {
        logger.info("All tasks for server {} finished", displayName);
    }


    protected ArrayNode getJson(CloseableHttpClient client, String url) {
        HttpGet get = new HttpGet(url);
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode json = null;
        try {
            json = mapper.readValue(EntityUtils.toString(client.execute(get).getEntity()),ArrayNode.class);
        }  catch (Exception e) {
            logger.error("Error while fetching the " + url + " data, returning " + json, e);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("The url " + url + " responded with a json {}" + json);
        }
        return json;
    }

    protected  <T> T getOptionalJson(CloseableHttpClient client, String url, Class<T> clazz) {
        try {
            HttpGet get = new HttpGet(url);
            ObjectMapper mapper = new ObjectMapper();
            T json = mapper.readValue(EntityUtils.toString(client.execute(get).getEntity()),clazz);
            if (logger.isDebugEnabled()) {
                logger.debug("The url " + url + " responded with a json " + json);
            }
            return json;
        } catch (Exception ex) {
            logger.error("Error while fetching the '/api/federation-links' data, returning NULL", ex);
            return null;
        }
    }

    private Map<String,String> getUrlParametersMap(InstanceInfo info) {
        Map<String,String> map = new HashMap<String, String>();
        map.put(TaskInputArgs.HOST, info.getHost());
        map.put(TaskInputArgs.PORT, info.getPort().toString());
        map.put(TaskInputArgs.USER, info.getUsername());
        map.put(TaskInputArgs.PASSWORD, info.getPassword());
        map.put(TaskInputArgs.USE_SSL, info.getUseSSL().toString());
        checkForEnvironmentsOverride(map,info.getDisplayName());
        return map;

    }

    private void populateMetricsMap(){
            for(Map<String, List<Map<String, String>>> metricsConfigEntry: metricsFromConfig){
            allMetricsFromConfig.putAll(metricsConfigEntry);
        }
    }

    private void checkForEnvironmentsOverride(Map<String, String> map, String displayName) {
        String[] keys = new String[]{
                TaskInputArgs.HOST,
                TaskInputArgs.PORT,
                TaskInputArgs.USER,
                TaskInputArgs.PASSWORD,
                TaskInputArgs.USE_SSL
        };
        for (String key:keys) {
            map.put(key, System.getProperty("APPD_RABBITMQ_ENV_" + key.toUpperCase(), map.get(key)));
            map.put(key, System.getProperty("APPD_RABBITMQ_ENV_" + key.toUpperCase() + "_" + displayName, map.get(key)));
        }
    }

}
