package com.appdynamics.extensions.rabbitmq;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TaskInputArgs;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.appdynamics.extensions.util.StringUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RabbitMQMonitoringTask implements AMonitorTaskRunnable{
	public static final Logger logger = Logger.getLogger(RabbitMQMonitoringTask.class);

	public void setConfiguration(MonitorConfiguration configuration) {
		this.configuration = configuration;
	}

	public void setInfo(InstanceInfo info) {
		this.info = info;
	}

	public void setDictionary(Map<String, String> dictionary) {
		this.dictionary = dictionary;
	}

	public void setQueueGroups(QueueGroup[] queueGroups) {
		this.queueGroups = queueGroups;
	}

	public void setMetricPrefix(String metricPrefix) {
		this.metricPrefix = metricPrefix;
	}

	private MonitorConfiguration configuration;

	private InstanceInfo info;

	private Map<String,String> dictionary;

	private QueueGroup[] queueGroups;

	private String metricPrefix;

	private String excludeQueueRegex;

	private Map<String, ?> configYml;

	private List<Map<String, List<Map<String, String>>>> metricsFromConfig;

	private MetricWriteHelper metricWriteHelper;

	private List<Metric> metrics;

	public void setMetricsFromConfig(List<Map<String, List<Map<String, String>>>> metricsFromConfig) {
		this.metricsFromConfig = metricsFromConfig;
	}

	private Map<String, List<Map<String, String>>> allMetricsFromConfig;

	public void setAllMetricsFromConfig(Map<String, List<Map<String, String>>> allMetricsFromConfig) {
		this.allMetricsFromConfig = allMetricsFromConfig;
	}

	public void setMetricWriteHelper(MetricWriteHelper metricWriteHelper) {
		this.metricWriteHelper = metricWriteHelper;
	}

	public void setMetrics(List<Metric> metrics) {
		this.metrics = metrics;
	}

	public RabbitMQMonitoringTask(MonitorConfiguration conf,InstanceInfo info,Map<String,String> dictionary,QueueGroup[] queueGroups,String metricPrefix,String excludeQueueRegex, MetricWriteHelper metricWriteHelper){
		this();
		this.configuration = conf;
		this.info = info;
		logger.debug(" Instance info initialized :" + info.toString());
		this.dictionary = dictionary;
		this.queueGroups = queueGroups;
		this.metricPrefix = metricPrefix;
		this.metricPrefix = metricPrefix + info.getDisplayName() + "|";
		this.excludeQueueRegex = excludeQueueRegex;
		this.configYml = conf.getConfigYml();
		this.metricsFromConfig = (List<Map<String, List<Map<String, String>>>>) this.configYml.get("metrics");
		allMetricsFromConfig = new HashMap<String, List<Map<String, String>>>();
		this.metricWriteHelper = metricWriteHelper;
		this.metrics = Lists.newArrayList();

	}
	public RabbitMQMonitoringTask(){}

	@Override
	public int hashCode() {
		return super.hashCode();
	}


	public void run() {
		try {


			String nodeUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/nodes").build();
			ArrayNode nodes = getJson(this.configuration.getHttpClient(), nodeUrl);

			String channelUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/channels").build();
			ArrayNode channels = getJson(this.configuration.getHttpClient(), channelUrl);

			String apiUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/queues").build();
			ArrayNode queues = getJson(this.configuration.getHttpClient(), apiUrl);

			populateMetricsMap();
			process(nodes, channels, queues);

			String federationLinkUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/federation-links").build();
			ArrayNode federationLinks = getOptionalJson(this.configuration.getHttpClient(), federationLinkUrl, ArrayNode.class);
			parseFederationData(federationLinks);

			String overviewUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/overview").build();
			JsonNode overview = getOptionalJson(this.configuration.getHttpClient(), overviewUrl, JsonNode.class);
			parseOverviewData(overview, nodes);

			if (metrics != null && metrics.size() > 0) {
				logger.debug("Printing rabbitmq metrics list of size " + metrics.size());
				metricWriteHelper.transformAndPrintMetrics(metrics);
			}

			logger.info("Completed the RabbitMQ Metric Monitoring task");
		} catch (Exception e) {
			metrics.add(new Metric("Availability", String.valueOf(BigInteger.ZERO), metricPrefix + "Availability"));
			logger.error("Unexpected error while running the RabbitMQ Monitor", e);
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

	protected <T> T getOptionalJson(CloseableHttpClient client, String url, Class<T> clazz) {
		try {
			HttpGet get = new HttpGet(url);
			ObjectMapper mapper = new ObjectMapper();
			T json = null;
			json = mapper.readValue(EntityUtils.toString(client.execute(get).getEntity()),clazz);
			if (logger.isDebugEnabled()) {
				logger.debug("The url " + url + " responded with a json " + json);
			}
			return json;
		} catch (Exception ex) {
			logger.error("Error while fetching the '/api/federation-links' data, returning NULL", ex);
			return null;
		}
	}


	private void process(ArrayNode nodes, ArrayNode channels, ArrayNode queues) {
		parseNodeData(nodes, channels, queues);
		parseQueueData(queues);
	}

	private void parseOverviewData(JsonNode overview, ArrayNode nodes) {
		if (overview != null) {
			JsonNode clusterNode = overview.get("cluster_name");
			//In some older versions, the node name is different
			if (clusterNode == null) {
				clusterNode = overview.get("node");
			}
			if (clusterNode != null) {

				List<Map<String, String>> queueTotalsPropsList= allMetricsFromConfig.get("queueTotalsProps");

				List<Map<String, String>> messageTotalsPropsList= allMetricsFromConfig.get("messageTotalsProps");

				List<Map<String, String>> objectTotalsPropsList= allMetricsFromConfig.get("objectTotalsProps");


				String clusterName = clusterNode.getTextValue();
				String prefix = "Clusters|" + clusterName + "|";
				//Queue Totals
				report(overview.get("message_stats"), messageTotalsPropsList, metricPrefix + prefix + "Messages|", true);
				report(overview.get("queue_totals"), queueTotalsPropsList, metricPrefix + prefix + "Queues|", true);
				report(overview.get("object_totals"), objectTotalsPropsList, metricPrefix + prefix + "Objects|", false);

				//Total Nodes
				String nodePrefix = prefix + "Nodes|";
				if (nodes != null) {
					Metric metric = new Metric("Total", String.valueOf(nodes.size()), metricPrefix + nodePrefix + "Total");
					metrics.add(metric);
					int runningCount = 0;
					for (JsonNode node : nodes) {
						Boolean running = getBooleanValue("running", node);
						if (running != null && running) {
							runningCount++;
						}
					}
					metrics.add(new Metric("Running", String.valueOf(runningCount), nodePrefix + "Running"));

					if (runningCount < nodes.size()) {
						metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ZERO), metricPrefix + prefix + "Cluster Health"));

					} else {
						metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ONE), metricPrefix + prefix + "Cluster Health"));

					}
				} else{
					// If there are no nodes running
					metrics.add(new Metric("Cluster Health", String.valueOf(BigInteger.ZERO), metricPrefix +prefix + "Cluster Health"));

				}
				metrics.add(new Metric("Availability", String.valueOf(BigInteger.ONE), metricPrefix + "Availability"));

			}
		} else {
			metrics.add(new Metric("Availability", String.valueOf(BigInteger.ZERO), metricPrefix + "Availability"));

		}
	}

	private void report(JsonNode node, List<Map<String, String>> fields, String metricPrefix, boolean useDictionary) {
		if (node != null && fields != null) {
			for (Map<String, String> field : fields) {
				JsonNode valueNode = node.get(field.get("name"));
				if (valueNode != null) {
					if (useDictionary) {
						metrics.add(new Metric(dictionary.get(field.get("name")), String.valueOf(valueNode.getIntValue()), metricPrefix + dictionary.get(field.get("name")), field));
					} else {
						metrics.add(new Metric(field.get("name"), String.valueOf(valueNode.getIntValue()), metricPrefix + field.get("name"), field));
					}
				}
			}
		} else {
			logger.debug("Not reporting the " + metricPrefix + " since the node is null");
		}
	}

	private void parseFederationData(ArrayNode federationLinks) {
		String prefix = "Federations|";
		if (federationLinks != null) {
			for (JsonNode federationLink : federationLinks) {
				final String exchangeName = getStringValue("exchange", federationLink);
				final String upstreamName = getStringValue("upstream", federationLink);
				final String status = getStringValue("status", federationLink);
				Metric metric = new Metric(exchangeName + "|" + upstreamName, String.valueOf(status.equals("running") ? 1 : 0), metricPrefix + prefix + exchangeName + "|" + upstreamName + "|running");
				metrics.add(metric);
			}
		}
	}

	/**
	 * Iterate over the available queue.message_status. The prefix will be Queues|$host|$QName
	 *
	 * @param queues
	 */
	private void parseQueueData(ArrayNode queues) {
		if (queues != null) {
			Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
			Map<String, String> metricTypeMap = new HashMap<String, String>();

			GroupStatTracker tracker = new GroupStatTracker(queueGroups);
			for (JsonNode queue : queues) {

				//Rabbit MQ queue names are case sensitive,
				// however the controller bombs when there are 2 metrics with same name in different cases.
				String qName = lower(getStringValue("name", queue, "Default"));
				String vHost = getStringValue("vhost", queue, "Default");
				if (vHost.equals("/")) {
					vHost = "Default";
				}
				if(!Strings.isNullOrEmpty(excludeQueueRegex)){
					if(qName.matches(excludeQueueRegex)){
						logger.info("Skipping queue name "+qName+ " as it matches exclude queue name regex");
						continue;
					}
					else{
						logger.info("Not Skipping queue name "+qName+ " as it doesn't matches exclude queue name regex");
					}
				}

				GroupStat groupStat = tracker.getGroupStat(vHost, qName);
				boolean showIndividualStats = groupStat.isShowIndividualStats();
				String prefix = "Queues|" + vHost + "|" + qName;
				String groupPrefix = "Queue Groups|" + vHost + "|" + groupStat.getGroupName();
				List<Map<String, String>> queueGroupPropsList= allMetricsFromConfig.get("queueGroupProps");
				BigInteger consumers = new BigInteger("0");
				for(Map<String, String> prop : queueGroupPropsList){
					consumers = getMetricValue(prop, queue);
					if (showIndividualStats) {
						Metric metric = new Metric(prop.get("name"), String.valueOf(consumers), metricPrefix + prefix + prop.get("name"), prop);
						metrics.add(metric);
					}
					groupStat.add(groupPrefix + prop.get("name"), consumers);
					groupStat.setMetricPropertiesMap(prop);
					}

				String msgPrefix = prefix + "|Messages|";
				String grpMsgPrefix = groupPrefix + "|Messages|";

				List<Map<String, String>> queueMessagePropsList= allMetricsFromConfig.get("queueMessageProps");

				for (Map<String, String> prop : queueMessagePropsList) {
					BigInteger value = getMetricValue(prop, queue);
					String metricName = getPropDesc(prop.get("name"));
					if (showIndividualStats) {
						Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + msgPrefix + metricName, prop);
						metrics.add(metric);
					}

					groupStat.add(groupPrefix + prop.get("name"), consumers);
					groupStat.setMetricPropertiesMap(prop);
					addToMap(valueMap, prop.get("name"), value);
					metricTypeMap.put(prop.get("name"),prop.get("metricType"));
				}

				String replicationPrefix = prefix + "|Replication|";

				List<Map<String, String>> queueReplicationCountsPropsList= allMetricsFromConfig.get("queueReplicationCountsProps");
				for (Map<String, String> prop : queueReplicationCountsPropsList) {
					BigInteger value = getChildrenCount(prop.get("name"), queue, 0);
					String metricName = getPropDesc(prop.get("name"));
					if (showIndividualStats) {
						Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + replicationPrefix + metricName, prop);
						metrics.add(metric);
					}
				}

				List<Map<String, String>> queueMessageStatsPropsList= allMetricsFromConfig.get("queueMessageStatsProps");


				for (Map<String, String> prop : queueMessageStatsPropsList) {
					BigInteger value = getMetricValue(prop, queue.get("message_stats"));
					String metricName = getPropDesc(prop.get("name"));
					if (showIndividualStats) {
						Metric metric = new Metric(metricName, String.valueOf(value), metricPrefix + msgPrefix + metricName, prop);
						metrics.add(metric);
					}
					groupStat.add(grpMsgPrefix + prop.get("name"), consumers);
					groupStat.setMetricPropertiesMap(prop);
					addToMap(valueMap, prop.get("name"), value);
					metricTypeMap.put(prop.get("name"),prop.get("metricType"));
				}
			}
			//Aggregate the above data for Summary|Messages
			String summaryPrefix = "Summary|Messages|";

			List<Map<String, String>> queueSummaryPropsList= allMetricsFromConfig.get("queueSummaryProps");


			for (Map<String, String> prop : queueSummaryPropsList) {
				BigInteger value = valueMap.get(prop.get("name"));
				Metric metric = new Metric(getPropDesc(prop.get("name")), String.valueOf(value), metricPrefix + summaryPrefix + getPropDesc(prop.get("name")), prop);
				metrics.add(metric);
			}
			//Total Number of Queues
			Metric metric = new Metric("Queues", String.valueOf(queues.size()), metricPrefix + "Summary|Queues");
			metrics.add(metric);

			//Print the regex queue group metrics
			Collection<GroupStat> groupStats = tracker.getGroupStats();
			if (groupStats != null) {
				for (GroupStat groupStat : groupStats) {
					Map<String, BigInteger> groupValMap = groupStat.getValueMap();
					for (String metricVal : groupValMap.keySet()) {
						metric = new Metric(metricVal, String.valueOf(groupValMap.get(metricVal)), metricPrefix + metricVal, groupStat.getMetricPropertiesMap());
						metrics.add(metric);
					}
				}
			}
		} else {
			metrics.add(new Metric("Queues", String.valueOf(BigInteger.ZERO), metricPrefix + "Summary|Queues"));
		}
	}

	private BigInteger getChildrenCount(String prop, JsonNode node, int defaultValue) {
		if (node != null) {
			final JsonNode metricNode = node.get(prop);
			if (metricNode != null && metricNode instanceof ArrayNode) {
				final ArrayNode arrayOfChildren = (ArrayNode) metricNode;
				return BigInteger.valueOf(arrayOfChildren.size());
			}
		}
		return BigInteger.valueOf(defaultValue);
	}

	private String lower(String value) {
		if (value != null) {
			return value.toLowerCase();
		}
		return value;
	}

	/**
	 * Gets the Description of the key from the dictionary.
	 *
	 * @param key
	 * @return
	 */
	private String getPropDesc(String key) {
		String name = dictionary.get(key);
		if (name == null) {
			name = key;
		}
		return name;
	}

	/**
	 * The data in the prefix Nodes|$node and Summary|
	 *
	 * @param nodes
	 * @param channels
	 * @param queues
	 */
	private void parseNodeData(ArrayNode nodes, ArrayNode channels, ArrayNode queues) {
		List<Map<String, String>> nodeDataList= allMetricsFromConfig.get("nodeDataMetrics");
		if (nodes != null) {
			for (JsonNode node : nodes) {
				String name = getStringValue("name", node);
				if (name != null) {
					List<JsonNode> nodeChannels = getChannels(channels, name);
					List<JsonNode> nodeQueues = getQueues(queues, name);
					String prefix = "Nodes|" + name;

					for(Map<String, String> nodeData : nodeDataList){

						BigInteger metricValue = getMetricValue(nodeData, node);

						if(metricValue!=null) {
							Metric metric = new Metric(nodeData.get("prefix"), String.valueOf(metricValue), metricPrefix + prefix + "|" + nodeData.get("prefix"), nodeData);
							metrics.add(metric);
						}
					}

					metrics.add(new Metric(prefix, String.valueOf(getBlockedChannelCount(nodeChannels)), metricPrefix + prefix + "|Channels|Blocked"));

					//Nodes|$node|Messages
					addChannelMessageProps(metricPrefix + prefix + "|Messages", nodeChannels);
					//Nodes|$node|Messages
					addQueueProps(metricPrefix + prefix + "|Messages", nodeQueues, "queueNodeMsgProps");
					//Nodes|$node|Consumers
					addQueueProps(metricPrefix + prefix + "|Consumers", nodeQueues, "queueNodeProps");
				}
			}
		}
		writeTotalChannelCount(channels);
		writeTotalConsumerCount(queues);

	}

	private BigInteger getNumericValueForBoolean(String key, JsonNode node, int defaultValue) {
		final Boolean booleanValue = getBooleanValue(key, node);
		if (booleanValue == null) {
			return BigInteger.valueOf(defaultValue);
		} else {
			return booleanValue.booleanValue() ? BigInteger.ONE : BigInteger.ZERO;
		}
	}

	/**
	 * Total Consumers for the Server = Sum of all consumers of all Queues
	 *
	 * @param queues
	 */
	private void writeTotalConsumerCount(ArrayNode queues) {
		BigInteger count = new BigInteger("0");
		if (queues != null) {
			for (JsonNode queue : queues) {
				BigInteger value = getBigIntegerValue("consumers", queue, 0);
				count = count.add(value);
			}
		}
		Metric metric = new Metric("Consumers", String.valueOf(count), metricPrefix + "Summary|Consumers" );
		metrics.add(metric);
	}

	/**
	 * Total cont of Channels for the server.
	 *
	 * @param channels
	 */
	private void writeTotalChannelCount(ArrayNode channels) {
		long channelCount;
		if (channels != null) {
			channelCount = channels.size();
		} else {
			channelCount = 0;
		}
		Metric metric = new Metric("Channels", String.valueOf(channelCount), metricPrefix + "|Summary|Channels");
		metrics.add(metric);
	}

	private void addQueueProps(String metricPrefix, List<JsonNode> nodeQueues, String configName) {

			for (JsonNode queue : nodeQueues) {
			List<Map<String, String>> queueNodePropsList= allMetricsFromConfig.get(configName);
			for (Map<String, String> prop : queueNodePropsList) {
				BigInteger value = getMetricValue(prop, queue);
				if(value!=null) {
					Metric metric = new Metric(prop.get("name"), String.valueOf(value), metricPrefix  + "|" + dictionary.get(prop.get("name")), prop);
					metrics.add(metric);
				}
			}
		}
	}


	/**
	 * Goes into Nodes|$node|Messages
	 *
	 * @param metricPrefix
	 * @param nodeChannels
	 */
	private void addChannelMessageProps(String metricPrefix, List<JsonNode> nodeChannels){

		for (JsonNode channel : nodeChannels) {
			JsonNode msgStats = channel.get("message_stats");
			List<Map<String, String>> channelNodeMsgPropsList = allMetricsFromConfig.get("channelNodeMsgProps");

			for (Map<String, String> prop : channelNodeMsgPropsList) {
				if (msgStats != null) {
					BigInteger statVal = getMetricValue(prop, channel);
					if(statVal!=null) {
						metrics.add(new Metric(prop.get("name"), String.valueOf(statVal), metricPrefix + "|" + dictionary.get(prop.get("name")), prop));
					}
				}
			}
		}
	}

	/**
	 * Adds the value to the Map. If the value is present it adds to the current value.
	 * The map is used to calculate the aggregate.
	 *
	 * @param valueMap
	 * @param prop
	 * @param val
	 */
	private void addToMap(Map<String, BigInteger> valueMap, String prop, BigInteger val) {
		if (val != null) {
			BigInteger curr = valueMap.get(prop);
			if (curr == null) {
				valueMap.put(prop, val);
			} else {
				valueMap.put(prop, curr.add(val));
			}
		}
	}

	/**
	 * Nodes|$node|Channels|Blocked
	 *
	 * @param nodeChannels
	 * @return
	 */
	private BigInteger getBlockedChannelCount(List<JsonNode> nodeChannels) {
		int blocked = 0;
		for (JsonNode nodeChannel : nodeChannels) {
			Boolean value = getBooleanValue("client_flow_blocked", nodeChannel);
			if (value != null && value) {
				blocked++;
			}
		}
		return new BigInteger(String.valueOf(blocked));
	}

	/**
	 * Get a list of channels for the give node.
	 *
	 * @param channels
	 * @param nodeName
	 * @return
	 */
	private List<JsonNode> getChannels(ArrayNode channels, String nodeName) {
		List<JsonNode> nodeChannels = new ArrayList<JsonNode>();
		if (channels != null && nodeName != null) {
			for (JsonNode channel : channels) {
				if (nodeName.equalsIgnoreCase(getStringValue("node", channel))) {
					nodeChannels.add(channel);
				}
			}
		}
		return nodeChannels;
	}

	/**
	 * Get a list of queues for the give node.
	 *
	 * @param queues
	 * @param nodeName
	 * @return
	 */
	private List<JsonNode> getQueues(ArrayNode queues, String nodeName) {
		List<JsonNode> nodeQueues = new ArrayList<JsonNode>();
		if (queues != null && nodeName != null) {
			for (JsonNode queue : queues) {
				if (nodeName.equalsIgnoreCase(getStringValue("node", queue))) {
					nodeQueues.add(queue);
				}
			}
		}
		return nodeQueues;
	}

	private String getStringValue(String propName, JsonNode node) {
		JsonNode jsonNode = node.get(propName);
		if (jsonNode != null) {
			return jsonNode.getTextValue();
		}
		return null;
	}

	private String getStringValue(String propName, JsonNode node, String defaultVal) {
		String value = getStringValue(propName, node);
		return value != null ? value : defaultVal;
	}

	private Boolean getBooleanValue(String propName, JsonNode node) {
		JsonNode jsonNode = node.get(propName);
		if (jsonNode != null) {
			return jsonNode.getBooleanValue();
		}
		return null;
	}

	private BigInteger getBigIntegerValue(String propName, JsonNode node) {
		if (node != null) {
			JsonNode jsonNode = node.get(propName);
			if (jsonNode != null) {
				try {
					return jsonNode.getBigIntegerValue();
				} catch (Exception e) {
					logger.warn("Cannot get the int value of the property "
							+ propName + " value is " + jsonNode.getTextValue());
				}
			}
		}
		return null;
	}

	private BigInteger getBigIntegerValue(String propName, JsonNode node, int defaultVal) {
		BigInteger value = getBigIntegerValue(propName, node);
		return value != null ? value : new BigInteger(String.valueOf(defaultVal));
	}

	private void populateMetricsMap(){
		for(Map<String, List<Map<String, String>>> metricsConfigEntry: metricsFromConfig){
			allMetricsFromConfig.putAll(metricsConfigEntry);
		}
	}

	private BigInteger applyDivisor(BigDecimal metricValue, String divisor) {

		if (Strings.isNullOrEmpty(divisor)) {
			return metricValue.toBigInteger();
		}

		try {
			metricValue = metricValue.divide(new BigDecimal(divisor));
			return metricValue.toBigInteger();
		} catch (NumberFormatException nfe) {
			logger.error(String.format("Cannot apply divisor {} to value {}.", divisor, metricValue), nfe);
		}
		throw new IllegalArgumentException("Cannot convert into BigInteger " + metricValue);
	}

	/**
	 * Calculates metric Value for given data
	 * @param dataMap
	 * @param node
	 * @return
	 */
	private BigInteger getMetricValue(Map<String, String> dataMap, JsonNode node){

		BigInteger metricValue;
		if(Boolean.valueOf(dataMap.get("isBoolean"))){
			metricValue = getNumericValueForBoolean(dataMap.get("name"), node, -1);
		}else{
			metricValue = getBigIntegerValue(dataMap.get("name"), node, 0);
		}

		if(StringUtils.hasText(dataMap.get("divisor"))){
			BigInteger data = getBigIntegerValue(dataMap.get("name"), node, 0);
			metricValue = applyDivisor(new BigDecimal(data), dataMap.get("divisor"));
		}
		return metricValue;
	}

	public void onTaskComplete() {
		logger.info("All tasks for server {} finished");
	}
}
