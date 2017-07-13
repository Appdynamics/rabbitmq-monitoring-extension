package com.appdynamics.extensions.rabbitmq;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.appdynamics.extensions.StringUtils;
import com.appdynamics.extensions.util.DeltaMetricsCalculator;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.google.common.base.Strings;

public class RabbitMQMonitoringTask implements Runnable{
	public static final Logger logger = Logger.getLogger("com.singularity.extensions.rabbitmq.RabbitMQMonitorTask");

	public MonitorConfiguration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(MonitorConfiguration configuration) {
		this.configuration = configuration;
	}

	public InstanceInfo getInfo() {
		return info;
	}

	public void setInfo(InstanceInfo info) {
		this.info = info;
	}

	public Map<String, String> getDictionary() {
		return dictionary;
	}

	public void setDictionary(Map<String, String> dictionary) {
		this.dictionary = dictionary;
	}

	public QueueGroup[] getQueueGroups() {
		return queueGroups;
	}

	public void setQueueGroups(QueueGroup[] queueGroups) {
		this.queueGroups = queueGroups;
	}

	public String getMetricPrefix() {
		return metricPrefix;
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

	public List<Map<String, List<Map<String, String>>>> getMetricsFromConfig() {
		return metricsFromConfig;
	}

	public void setMetricsFromConfig(List<Map<String, List<Map<String, String>>>> metricsFromConfig) {
		this.metricsFromConfig = metricsFromConfig;
	}

	private Map<String, List<Map<String, String>>> allMetricsFromConfig;

	public Map<String, List<Map<String, String>>> getAllMetricsFromConfig() {
		return allMetricsFromConfig;
	}

	public void setAllMetricsFromConfig(Map<String, List<Map<String, String>>> allMetricsFromConfig) {
		this.allMetricsFromConfig = allMetricsFromConfig;
	}

	private DeltaMetricsCalculator deltaCalculator;

	public DeltaMetricsCalculator getDeltaCalculator() {
		return deltaCalculator;
	}

	public void setDeltaCalculator(DeltaMetricsCalculator deltaCalculator) {
		this.deltaCalculator = deltaCalculator;
	}

	private static final String DEFAULT_METRIC_TYPE = "OBS.CUR.COL";

	public RabbitMQMonitoringTask(MonitorConfiguration conf,InstanceInfo info,Map<String,String> dictionary,QueueGroup[] queueGroups,String metricPrefix,String excludeQueueRegex, DeltaMetricsCalculator deltaCalculator){
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
		this.deltaCalculator = deltaCalculator;
		allMetricsFromConfig = new HashMap<String, List<Map<String, String>>>();

	}
	public RabbitMQMonitoringTask(){};

	private Map<String, BigInteger> perMinMetricsMap = new HashMap<String, BigInteger>();

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

			logger.info("Completed the RabbitMQ Metric Monitoring task");
		} catch (Exception e) {
			printCollectiveObservedAverage("Availability", BigInteger.ZERO, DEFAULT_METRIC_TYPE, false);

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
		return map;

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
				report(overview.get("message_stats"), messageTotalsPropsList, prefix + "Messages|", true);
				report(overview.get("queue_totals"), queueTotalsPropsList, prefix + "Queues|", true);
				report(overview.get("object_totals"), objectTotalsPropsList, prefix + "Objects|", false);

				//Total Nodes
				String nodePrefix = prefix + "Nodes|";
				if (nodes != null) {
					printCollectiveObservedAverage(nodePrefix + "Total",
										new BigInteger(String.valueOf(nodes.size())), DEFAULT_METRIC_TYPE, false);
					int runningCount = 0;
					for (JsonNode node : nodes) {
						Boolean running = getBooleanValue("running", node);
						if (running != null && running) {
							runningCount++;
						}
					}
					printCollectiveObservedAverage(nodePrefix + "Running",
										new BigInteger(String.valueOf(runningCount)), DEFAULT_METRIC_TYPE, false);
					if (runningCount < nodes.size()) {
						printIndividualObservedAverage(prefix + "Cluster Health",
												BigInteger.ZERO, DEFAULT_METRIC_TYPE, false);
					} else {
						printIndividualObservedAverage(prefix + "Cluster Health",
												BigInteger.ONE, DEFAULT_METRIC_TYPE, false);
					}
				} else{
					// If there are no nodes running
					printIndividualObservedAverage(prefix + "Cluster Health",
										BigInteger.ZERO, DEFAULT_METRIC_TYPE, false);
				}
				printCollectiveObservedAverage("Availability", BigInteger.ONE,
												DEFAULT_METRIC_TYPE, false);
			}
		} else {
			printCollectiveObservedAverage("Availability", BigInteger.ZERO,
												DEFAULT_METRIC_TYPE, false);
		}
	}

	private void report(JsonNode node, List<Map<String, String>> fields, String metricPrefix, boolean useDictionary) {
		if (node != null && fields != null) {
			for (Map<String, String> field : fields) {
				JsonNode valueNode = node.get(field.get("name"));
				Boolean collectDelta = Boolean.valueOf(field.get("collectDelta"));
				if (valueNode != null) {
					if (useDictionary) {
						printCollectiveObservedCurrent(metricPrefix + dictionary.get(field.get("name")), valueNode.getBigIntegerValue(), field.get("metricType"), collectDelta);
					} else {
						printCollectiveObservedCurrent(metricPrefix + field.get("name"), valueNode.getBigIntegerValue(), field.get("metricType"), collectDelta);
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
				printCollectiveObservedCurrent(prefix + exchangeName + "|" + upstreamName + "|running", BigInteger.valueOf(status.equals("running") ? 1 : 0),DEFAULT_METRIC_TYPE, false);
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
						printCollectiveObservedCurrent(prefix + prop.get("name"), consumers, prop.get("metricType"),
										    	Boolean.valueOf(prop.get("collectDelta")));
					}
					groupStat.add(groupPrefix + prop.get("name"), consumers);
					groupStat.setMetricTypeMap(groupPrefix + prop.get("name"), prop.get("metricType"));
					groupStat.setCollectDeltaMap(groupPrefix + prop.get("name"), Boolean.valueOf(prop.get("collectDelta")));
				}

				String msgPrefix = prefix + "|Messages|";
				String grpMsgPrefix = groupPrefix + "|Messages|";

				List<Map<String, String>> queueMessagePropsList= allMetricsFromConfig.get("queueMessageProps");

				for (Map<String, String> prop : queueMessagePropsList) {
					BigInteger value = getMetricValue(prop, queue);
					String metricName = getPropDesc(prop.get("name"));
					if (showIndividualStats) {
						printCollectiveObservedCurrent(msgPrefix + metricName, value, prop.get("metricType"),
												Boolean.valueOf(prop.get("collectDelta")));
					}

					groupStat.add(groupPrefix + prop.get("name"), consumers);
					groupStat.setMetricTypeMap(groupPrefix + prop.get("name"), prop.get("metricType"));
					groupStat.setCollectDeltaMap(groupPrefix + prop.get("name"), Boolean.valueOf(prop.get("collectDelta")));
					addToMap(valueMap, prop.get("name"), value);
					metricTypeMap.put(prop.get("name"),prop.get("metricType"));
				}

				String replicationPrefix = prefix + "|Replication|";

				List<Map<String, String>> queueReplicationCountsPropsList= allMetricsFromConfig.get("queueReplicationCountsProps");
				for (Map<String, String> prop : queueReplicationCountsPropsList) {
					BigInteger value = getChildrenCount(prop.get("name"), queue, 0);
					String metricName = getPropDesc(prop.get("name"));
					if (showIndividualStats) {
						printCollectiveObservedCurrent(replicationPrefix + metricName, value, prop.get("metricType"), Boolean.valueOf(prop.get("collectDelta")));
					}
				}

				List<Map<String, String>> queueMessageStatsPropsList= allMetricsFromConfig.get("queueMessageStatsProps");


				for (Map<String, String> prop : queueMessageStatsPropsList) {
					BigInteger value = getMetricValue(prop, queue);
					String metricName = getPropDesc(prop.get("name"));
					if (showIndividualStats) {
						printCollectiveObservedCurrent(msgPrefix + metricName, value, prop.get("metricType"), Boolean.valueOf(prop.get("collectDelta")));
					}
					groupStat.add(grpMsgPrefix + prop.get("name"), consumers);
					groupStat.setMetricTypeMap(grpMsgPrefix + prop.get("name"), prop.get("metricType"));
					groupStat.setCollectDeltaMap(grpMsgPrefix + prop.get("name"), Boolean.valueOf(prop.get("collectDelta")));
					addToMap(valueMap, prop.get("name"), value);
					metricTypeMap.put(prop.get("name"),prop.get("metricType"));
				}
			}
			//Aggregate the above data for Summary|Messages
			String summaryPrefix = "Summary|Messages|";

			List<Map<String, String>> queueSummaryPropsList= allMetricsFromConfig.get("queueSummaryProps");


			for (Map<String, String> prop : queueSummaryPropsList) {
				BigInteger value = valueMap.get(prop.get("name"));
				String metricType = metricTypeMap.get(prop.get("name"));
				printCollectiveObservedCurrent(summaryPrefix + getPropDesc(prop.get("name")), value, metricType, Boolean.valueOf(prop.get("collectDelta")));
			}
			//Total Number of Queues
			printCollectiveObservedCurrent("Summary|Queues", new BigInteger(String.valueOf(queues.size())), DEFAULT_METRIC_TYPE, false);

			//Print the regex queue group metrics
			Collection<GroupStat> groupStats = tracker.getGroupStats();
			if (groupStats != null) {
				for (GroupStat groupStat : groupStats) {
					Map<String, BigInteger> groupValMap = groupStat.getValueMap();
					for (String metric : groupValMap.keySet()) {
						printCollectiveObservedCurrent(metric, groupValMap.get(metric), groupStat.getMetricTypeMap().get(metric), groupStat.getCollectDeltaMap().get(metric));
					}
				}
			}
		} else {
			printCollectiveObservedCurrent("Summary|Queues", new BigInteger("0"), DEFAULT_METRIC_TYPE, false);
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

						printCollectiveObservedCurrent(prefix + nodeData.get("prefix"),metricValue,
								nodeData.get("metricType"), Boolean.valueOf(nodeData.get("collectDelta")));
					}

					printCollectiveObservedCurrent(prefix + "|Channels|Blocked",
							getBlockedChannelCount(nodeChannels), DEFAULT_METRIC_TYPE, false);

					//Nodes|$node|Messages
					addChannelMessageProps(prefix + "|Messages", nodeChannels);
					//Nodes|$node|Messages
					addQueueProps(prefix + "|Messages", nodeQueues, "queueNodeMsgProps");
					//Nodes|$node|Consumers
					addQueueProps(prefix + "|Consumers", nodeQueues, "queueNodeProps");
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
		printCollectiveObservedCurrent("Summary|Consumers",
				new BigInteger(String.valueOf(count)), DEFAULT_METRIC_TYPE, false);
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
		printCollectiveObservedCurrent("|Summary|Channels",
				new BigInteger(String.valueOf(channelCount)), DEFAULT_METRIC_TYPE, false);
	}

	private void addQueueProps(String metricPrefix, List<JsonNode> nodeQueues, String configName) {
		Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
		Map<String, String> metricTypeMap = new HashMap<String, String>();
		Map<String, Boolean> collectDeltaMap = new HashMap<String, Boolean>();

		for (JsonNode queue : nodeQueues) {
			List<Map<String, String>> queueNodePropsList= allMetricsFromConfig.get(configName);
			for (Map<String, String> prop : queueNodePropsList) {
				BigInteger value = getMetricValue(prop, queue);
				addToMap(valueMap, prop.get("name"), value);
				metricTypeMap.put(prop.get("name"), prop.get("metricType"));
				collectDeltaMap.put(prop.get("name"), Boolean.valueOf(prop.get("collectDelta")));
			}
		}
		uploadMetricValues(metricPrefix, valueMap, metricTypeMap, collectDeltaMap);
		//TODO what to do with the count?
	}


	/**
	 * Goes into Nodes|$node|Messages
	 *
	 * @param metricPrefix
	 * @param nodeChannels
	 */
	private void addChannelMessageProps(String metricPrefix, List<JsonNode> nodeChannels){
		Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
		Map<String, String> metricTypeMap = new HashMap<String, String>();
		Map<String, Boolean> collectDeltaMap = new HashMap<String, Boolean>();

		for (JsonNode channel : nodeChannels) {
			JsonNode msgStats = channel.get("message_stats");
			List<Map<String, String>> channelNodeMsgPropsList = allMetricsFromConfig.get("channelNodeMsgProps");

			for (Map<String, String> prop : channelNodeMsgPropsList) {
				if (msgStats != null) {
					BigInteger statVal = getMetricValue(prop, channel);
					addToMap(valueMap, prop.get("name"), statVal);
					metricTypeMap.put(prop.get("name"),prop.get("metricType"));
					collectDeltaMap.put(prop.get("name"), Boolean.valueOf(prop.get("collectDelta")));
				}
			}
		}
		uploadMetricValues(metricPrefix, valueMap, metricTypeMap, collectDeltaMap);
	}
	/**
	 * Iterates over the map and writes it to the metric writer.
	 *
	 * @param metricPrefix
	 * @param valueMap
	 */
	private void uploadMetricValues(String metricPrefix, Map<String, BigInteger> valueMap, Map<String, String> metricTypeMap, Map<String, Boolean> collectDeltaMap) {
		for (String key : valueMap.keySet()) {
			String name = getPropDesc(key);
			BigInteger value = valueMap.get(key);
			String metricType = metricTypeMap.get(key);
			printCollectiveObservedCurrent(metricPrefix + "|" + name, value, metricType, collectDeltaMap.get(key));
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

	public void printMetric(String metricName, BigInteger metricValue, String metricType) {

		String value;
		if (metricValue != null) {
			value = metricValue.toString();
		} else {
			value = "0";
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Sending ["
					+ "] metric = " + metricPrefix + metricName + " = " + value);
		}
		this.configuration.getMetricWriter().printMetric(metricPrefix + metricName, new BigDecimal(metricValue),metricType);

	}

	private void printCollectiveObservedCurrent(String metricName, BigInteger metricValue, String metricType, Boolean collectDelta) {

		printMetric(metricName, metricValue, metricType);

		if (collectDelta) {
			BigDecimal deltaMetricValue = deltaCalculator.calculateDelta(metricName, BigDecimal.valueOf(metricValue.longValue()));

			printMetric(metricName + " Delta",
					deltaMetricValue != null ? deltaMetricValue.toBigInteger() : new BigInteger("0"), metricType);
		}
		if (metricValue != null) {

			List<Map<String, String>> perMinMetricSuffixesList= allMetricsFromConfig.get("perMinMetricSuffixes");

			for (Map<String, String> suffix : perMinMetricSuffixesList) {
				if (metricName.endsWith(suffix.get("name"))) {
					BigInteger value = perMinMetricsMap.get(metricName);
					if (value != null) {
						BigInteger diff = metricValue.subtract(value);
						printCollectiveObservedAverage(metricName + " Per Minute", diff,
											suffix.get("metricType"),Boolean.valueOf(suffix.get("collectDelta")));
					}
					perMinMetricsMap.put(metricName, metricValue);
				}
			}
		}
	}

	protected void printCollectiveObservedAverage(String metricName, BigInteger metricValue, String metricType, Boolean collectDelta) {

		printMetric(metricName, metricValue,metricType);

		if (collectDelta) {
			BigDecimal deltaMetricValue = deltaCalculator.calculateDelta(metricName, BigDecimal.valueOf(metricValue.longValue()));

			printMetric(metricName + " Delta",
					deltaMetricValue != null ? deltaMetricValue.toBigInteger() : new BigInteger("0"), metricType);
		}
	}

	protected void printIndividualObservedAverage(String metricName, BigInteger metricValue, String metricType, Boolean collectDelta) {
		printMetric(metricName, metricValue,metricType);

		if (collectDelta) {
			BigDecimal deltaMetricValue = deltaCalculator.calculateDelta(metricName, BigDecimal.valueOf(metricValue.longValue()));

			printMetric(metricName + " Delta",
					deltaMetricValue != null ? deltaMetricValue.toBigInteger() : new BigInteger("0"), metricType);
		}
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

}
