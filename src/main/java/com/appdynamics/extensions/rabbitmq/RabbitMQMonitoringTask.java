package com.appdynamics.extensions.rabbitmq;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.Base64Variants;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.singularity.ee.agent.systemagent.api.MetricWriter;

public class RabbitMQMonitoringTask implements Runnable{
	public static final Logger logger = Logger.getLogger("com.singularity.extensions.rabbitmq.RabbitMQMonitorTask");

	private MonitorConfiguration configuration;

	private InstanceInfo info;

	private Map<String,String> dictionary;

	private QueueGroup[] queueGroups;

	private String metricPrefix;

	public RabbitMQMonitoringTask(MonitorConfiguration conf,InstanceInfo info,Map<String,String> dictionary,QueueGroup[] queueGroups,String metricPrefix){
		this.configuration = conf;
		this.info = info;
		this.dictionary = dictionary;
		this.queueGroups = queueGroups;
		this.metricPrefix = metricPrefix;
	}

	//Items in Nodes|<node>|Messages - data looked up from /api/channels
	private List<String> channelNodeMsgProps = Arrays.asList("ack", "deliver", "deliver_no_ack", "get_no_ack", "publish", "redeliver");
	//Items in Nodes|<node>|Messages - data looked up from /api/queues
	private List<String> queueNodeMsgProps = Arrays.asList("messages_ready", "messages_unacknowledged");
	//Items in Nodes|<node>|Consumers - data looked up from /api/queues
	private List<String> queueNodeProps = Arrays.asList("consumers");
	//Items in Queues|<host>|<QName>|Messages - data looked up from /api/queues
	private List<String> queueMessageProps = Arrays.asList("messages_ready", "messages_unacknowledged");
	private List<String> queueReplicationCountsProps = Arrays.asList("slave_nodes", "synchronised_slave_nodes", "down_slave_nodes");
	//Items in Queues|<host>|<QName>|Messages - data looked up from /api/queues/message_stats
	private List<String> queueMessageStatsProps = Arrays.asList("ack", "deliver_get", "deliver", "deliver_no_ack", "get", "get_no_ack", "publish", "redeliver");
	//Items in Summary|Messages - data looked up from /api/queues
	private List<String> queueSummaryProps = Arrays.asList("messages_ready", "deliver_get", "publish", "redeliver", "messages_unacknowledged");
	//Overview Queue Totals
	private List<String> queueTotalsProps = Arrays.asList("messages", "messages_ready", "messages_unacknowledged");
	private List<String> messageTotalsProps = Arrays.asList("publish", "ack", "deliver_get", "deliver");
	private List<String> objectTotalsProps = Arrays.asList("consumers", "queues", "exchanges", "connections", "channels");

	//Per Minute Metrics, All these metric suffixes will be reported as per minute also
	private List<String> perMinMetricSuffixes = Arrays.asList("|Messages|Delivered (Total)", "|Messages|Published", "|Messages|Acknowledged", "|Messages|Redelivered");
	private Map<String, BigInteger> perMinMetricsMap = new HashMap<String, BigInteger>();

	public void run() {
		try {

			String nodeUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/nodes").build();
			ArrayNode nodes = getJson(this.configuration.getHttpClient(), nodeUrl);

			String channelUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/channels").build();
			ArrayNode channels = getJson(this.configuration.getHttpClient(), channelUrl);

			String apiUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/queues").build();
			ArrayNode queues = getJson(this.configuration.getHttpClient(), apiUrl);
			process(nodes, channels, queues);

			String federationLinkUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/federation-links").build();
			ArrayNode federationLinks = getOptionalJson(this.configuration.getHttpClient(), federationLinkUrl, ArrayNode.class);
			parseFederationData(federationLinks);

			String overviewUrl = UrlBuilder.builder(getUrlParametersMap(info)).path("/api/overview").build();
			JsonNode overview = getOptionalJson(this.configuration.getHttpClient(), overviewUrl, JsonNode.class);
			parseOverviewData(overview, nodes);

			logger.info("Completed the RabbitMQ Metric Monitoring task");
		} catch (Exception e) {  		
			printCollectiveObservedAverage("Availability", BigInteger.ZERO);

			logger.error("Unexpected error while running the RabbitMQ Monitor", e);
		} finally {
			try {
				this.configuration.getHttpClient().close();
			} catch (Exception e) {
				logger.error("Error while closing the http client", e);
			}
		}

	}

	private Map<String,String> getUrlParametersMap(InstanceInfo info) {
		Map<String,String> map = new HashMap<String, String>();
		map.put(TaskInputArgs.HOST, info.getHost());
		map.put(TaskInputArgs.PORT, info.getPort().toString());
		map.put(TaskInputArgs.USER, info.getUsername());
		map.put(TaskInputArgs.PASSWORD, info.getPassword());
		map.put("useSSL", info.getUseSSL().toString());
		return map;

	}
	protected ArrayNode getJson(CloseableHttpClient client, String url) {
		HttpGet get = new HttpGet(url);
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode json = null;
		try {
			json = mapper.readValue(EntityUtils.toString(client.execute(get).getEntity()),ArrayNode.class);
		}  catch (Exception e) {
			e.printStackTrace();
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
			logger.debug("Error while fetching the '/api/federation-links' data, returning NULL", ex);
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
				String clusterName = clusterNode.getTextValue();
				String prefix = "Clusters|" + clusterName + "|";
				//Queue Totals
				report(overview.get("message_stats"), messageTotalsProps, prefix + "Messages|", true);
				report(overview.get("queue_totals"), queueTotalsProps, prefix + "Queues|", true);
				report(overview.get("object_totals"), objectTotalsProps, prefix + "Objects|", false);

				//Total Nodes
				String nodePrefix = prefix + "Nodes|";
				if (nodes != null) {
					printCollectiveObservedAverage(nodePrefix + "Total", new BigInteger(String.valueOf(nodes.size())));
					int runningCount = 0;
					for (JsonNode node : nodes) {
						Boolean running = getBooleanValue("running", node);
						if (running != null && running) {
							runningCount++;
						}
					}
					printCollectiveObservedAverage(nodePrefix + "Running", new BigInteger(String.valueOf(runningCount)));
					if (runningCount < nodes.size()) {
						printIndividualObservedAverage(prefix + "Cluster Health", BigInteger.ZERO);
					} else {
						printIndividualObservedAverage(prefix + "Cluster Health", BigInteger.ONE);
					}
				} else{
					// If there are no nodes running
					printIndividualObservedAverage(prefix + "Cluster Health", BigInteger.ZERO);
				}
				printCollectiveObservedAverage("Availability", BigInteger.ONE);
			}
		} else {
			printCollectiveObservedAverage("Availability", BigInteger.ZERO);
		}
	}

	private void report(JsonNode node, List<String> fields, String metricPrefix, boolean useDictionary) {
		if (node != null && fields != null) {
			for (String field : fields) {
				JsonNode valueNode = node.get(field);
				if (valueNode != null) {
					if (useDictionary) {
						printCollectiveObservedCurrent(metricPrefix + dictionary.get(field), valueNode.getBigIntegerValue());
					} else {
						printCollectiveObservedCurrent(metricPrefix + field, valueNode.getBigIntegerValue());
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
				printMetric(prefix + exchangeName + "|" + upstreamName + "|running", BigInteger.valueOf(status.equals("running") ? 1 : 0),
						MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
						MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
						MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
						);
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
			GroupStatTracker tracker = new GroupStatTracker(queueGroups);
			for (JsonNode queue : queues) {

				//Rabbit MQ queue names are case sensitive,
				// however the controller bombs when there are 2 metrics with same name in different cases.
				String qName = lower(getStringValue("name", queue, "Default"));
				String vHost = getStringValue("vhost", queue, "Default");
				if (vHost.equals("/")) {
					vHost = "Default";
				}
				GroupStat groupStat = tracker.getGroupStat(vHost, qName);
				boolean showIndividualStats = groupStat.isShowIndividualStats();
				String prefix = "Queues|" + vHost + "|" + qName;
				String groupPrefix = "Queue Groups|" + vHost + "|" + groupStat.getGroupName();
				BigInteger consumers = getBigIntegerValue("consumers", queue, 0);
				if (showIndividualStats) {
					printCollectiveObservedCurrent(prefix + "|Consumers", consumers);
				}
				groupStat.add(groupPrefix + "|Consumers", consumers);
				String msgPrefix = prefix + "|Messages|";
				String grpMsgPrefix = groupPrefix + "|Messages|";
				for (String prop : queueMessageProps) {
					BigInteger value = getBigIntegerValue(prop, queue, 0);
					String metricName = getPropDesc(prop);
					if (showIndividualStats) {
						printCollectiveObservedCurrent(msgPrefix + metricName, value);
					}
					groupStat.add(grpMsgPrefix + metricName, value);
					addToMap(valueMap, prop, value);
				}
				String replicationPrefix = prefix + "|Replication|";
				for (String prop : queueReplicationCountsProps) {
					BigInteger value = getChildrenCount(prop, queue, 0);
					String metricName = getPropDesc(prop);
					if (showIndividualStats) {
						printCollectiveObservedCurrent(replicationPrefix + metricName, value);
					}
				}

				//Fetch data from message_stats object
				JsonNode msgStats = queue.get("message_stats");
				for (String prop : queueMessageStatsProps) {
					BigInteger value = getBigIntegerValue(prop, msgStats, 0);
					String metricName = getPropDesc(prop);
					if (showIndividualStats) {
						printCollectiveObservedCurrent(msgPrefix + metricName, value);
					}
					groupStat.add(grpMsgPrefix + metricName, value);
					addToMap(valueMap, prop, value);
				}
			}
			//Aggregate the above data for Summary|Messages
			String summaryPrefix = "Summary|Messages|";
			for (String prop : queueSummaryProps) {
				BigInteger value = valueMap.get(prop);
				printCollectiveObservedCurrent(summaryPrefix + getPropDesc(prop), value);
			}
			//Total Number of Queues
			printCollectiveObservedCurrent("Summary|Queues", new BigInteger(String.valueOf(queues.size())));

			//Print the regex queue group metrics
			Collection<GroupStat> groupStats = tracker.getGroupStats();
			if (groupStats != null) {
				for (GroupStat groupStat : groupStats) {
					Map<String, BigInteger> groupValMap = groupStat.getValueMap();
					for (String metric : groupValMap.keySet()) {
						printCollectiveObservedCurrent(metric, groupValMap.get(metric));
					}
				}
			}
		} else {
			printCollectiveObservedCurrent("Summary|Queues", new BigInteger("0"));
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
		if (nodes != null) {
			for (JsonNode node : nodes) {
				String name = getStringValue("name", node);
				if (name != null) {
					List<JsonNode> nodeChannels = getChannels(channels, name);
					List<JsonNode> nodeQueues = getQueues(queues, name);
					String prefix = "Nodes|" + name;
					BigInteger procUsed = getBigIntegerValue("proc_used", node, 0);
					printCollectiveObservedCurrent(prefix + "|Erlang Processes", procUsed);
					printCollectiveObservedCurrent(prefix + "|Disk Free Alarm Activated", getNumericValueForBoolean("disk_free_alarm", node, -1));
					printCollectiveObservedCurrent(prefix + "|Memory Free Alarm Activated", getNumericValueForBoolean("mem_alarm", node, -1));
					BigInteger fdUsed = getBigIntegerValue("fd_used", node, 0);
					printCollectiveObservedCurrent(prefix + "|File Descriptors", fdUsed);
					BigInteger memUsed = getBigIntegerValue("mem_used", node, 0);
					int round = (int) Math.round(memUsed.intValue() / (1024D * 1024D));
					printCollectiveObservedCurrent(prefix + "|Memory(MB)", new BigInteger(String.valueOf(round)));
					BigInteger sockUsed = getBigIntegerValue("sockets_used", node, 0);
					printCollectiveObservedCurrent(prefix + "|Sockets", sockUsed);
					printCollectiveObservedCurrent(prefix + "|Channels|Count", new BigInteger(String.valueOf(nodeChannels.size())));
					printCollectiveObservedCurrent(prefix + "|Channels|Blocked", getBlockedChannelCount(nodeChannels));
					//Nodes|$node|Messages
					addChannelMessageProps(prefix + "|Messages", nodeChannels);
					//Nodes|$node|Messages
					addQueueMessageProps(prefix + "|Messages", nodeQueues);
					//Nodes|$node|Consumers
					addQueueProps(prefix + "|Consumers", nodeQueues);
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
		printCollectiveObservedCurrent("Summary|Consumers", new BigInteger(String.valueOf(count)));
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
		printCollectiveObservedCurrent("Summary|Channels", new BigInteger(String.valueOf(channelCount)));
	}

	private void addQueueProps(String metricPrefix, List<JsonNode> nodeQueues) {
		Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
		for (JsonNode queue : nodeQueues) {
			for (String prop : queueNodeProps) {
				BigInteger value = getBigIntegerValue(prop, queue);
				addToMap(valueMap, prop, value);
			}
		}
		uploadMetricValues(metricPrefix, valueMap);
		//TODO what to do with the count?
	}

	/**
	 * Goes into Nodes|$node|Messages
	 *
	 * @param metricPrefix
	 * @param nodeQueues
	 */
	private void addQueueMessageProps(String metricPrefix, List<JsonNode> nodeQueues) {
		Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
		for (JsonNode queue : nodeQueues) {
			for (String prop : queueNodeMsgProps) {
				BigInteger value = getBigIntegerValue(prop, queue);
				addToMap(valueMap, prop, value);
			}
		}
		uploadMetricValues(metricPrefix, valueMap);
	}

	/**
	 * Goes into Nodes|$node|Messages
	 *
	 * @param metricPrefix
	 * @param nodeChannels
	 */
	private void addChannelMessageProps(String metricPrefix, List<JsonNode> nodeChannels) {
		Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
		for (JsonNode channel : nodeChannels) {
			JsonNode msgStats = channel.get("message_stats");
			for (String prop : channelNodeMsgProps) {
				if (msgStats != null) {
					BigInteger statVal = getBigIntegerValue(prop, msgStats, 0);
					addToMap(valueMap, prop, statVal);
				}
			}
		}
		uploadMetricValues(metricPrefix, valueMap);
	}

	/**
	 * Iterates over the map and writes it to the metric writer.
	 *
	 * @param metricPrefix
	 * @param valueMap
	 */
	private void uploadMetricValues(String metricPrefix, Map<String, BigInteger> valueMap) {
		for (String key : valueMap.keySet()) {
			String name = getPropDesc(key);
			BigInteger value = valueMap.get(key);
			printCollectiveObservedCurrent(metricPrefix + "|" + name, value);
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


	private String buildBaseUrl(Map<String, String> argsMap) {
		StringBuilder sb = new StringBuilder();
		String useSSL = argsMap.get("useSSL");
		if (useSSL.equalsIgnoreCase("true")) {
			sb.append("https://");
		} else {
			sb.append("http://");
		}
		sb.append(argsMap.get("host")).append(":");
		sb.append(argsMap.get("port")).append("/");
		sb.append("api");
		if (logger.isDebugEnabled()) {
			logger.debug("Base URL initialized to " + sb.toString());
		}
		return sb.toString();
	}


	/**
	 * @param urlStr
	 * @param encodedUserPass
	 * @return
	 */
	public ArrayNode invokeApi(String urlStr, String encodedUserPass) {
		InputStream in = null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			URL url = new URL(urlStr);
			URLConnection connection = url.openConnection();
			connection.setRequestProperty("Authorization", "Basic " + encodedUserPass);
			connection.setRequestProperty("Accept", "application/json");
			in = connection.getInputStream();
			ArrayNode nodes = mapper.readValue(in, ArrayNode.class);
			if (logger.isDebugEnabled()) {
				logger.debug("The api " + urlStr + " returned the json " + nodes);
			}
			return nodes;
		} catch (IOException e) {
			logger.error("Exception while invoking the api at " + urlStr, e);
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public void printMetric(String metricName, BigInteger metricValue, String aggregation, String timeRollup, String cluster) {

		String value;
		if (metricValue != null) {
			value = metricValue.toString();
		} else {
			value = "0";
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Sending [" + aggregation + "/" + timeRollup + "/" + cluster
					+ "] metric = " + metricPrefix + metricName + " = " + value);
		}
		this.configuration.getMetricWriter().printMetric(metricPrefix + metricName,value,aggregation,timeRollup,cluster);

	}

	private void printCollectiveObservedCurrent(String metricName, BigInteger metricValue) {
		printMetric(metricName, metricValue,
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
				);
		if (metricValue != null) {
			for (String suffix : perMinMetricSuffixes) {
				if (metricName.endsWith(suffix)) {
					BigInteger value = perMinMetricsMap.get(metricName);
					if (value != null) {
						BigInteger diff = metricValue.subtract(value);
						printCollectiveObservedAverage(metricName + " Per Minute", diff);
					}
					perMinMetricsMap.put(metricName, metricValue);
				}
			}
		}
	}

	protected void printCollectiveObservedAverage(String metricName, BigInteger metricValue) {
		printMetric(metricName, metricValue,
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
				);
	}

	protected void printIndividualObservedAverage(String metricName, BigInteger metricValue) {
		printMetric(metricName, metricValue,
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL
				);
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

	/**
	 * Encodes the Username and Password using Base64 encoding.
	 *
	 * @param argsMap Expected to contain the use and password
	 * @return
	 */
	private String encodeUserPass(Map<String, String> argsMap) {
		String username = argsMap.get("username");
		String password = argsMap.get("password");
		StringBuilder sb = new StringBuilder();
		sb.append(username).append(":").append(password);
		return Base64Variants.MIME.encode(sb.toString().getBytes());
	}	

}
