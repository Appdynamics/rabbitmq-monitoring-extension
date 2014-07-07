package com.appdynamics.extensions.rabbitmq;

import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.Logger;
import org.codehaus.jackson.Base64Variants;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: abey.tom
 * Date: 11/20/13
 * Time: 5:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class RabbitMQMonitor extends AManagedMonitor {
    public static final Logger logger = Logger.getLogger("com.singularity.extensions.RabbitMQMonitor");
    public static final String DEFAULT_METRIC_PREFIX = "Custom Metrics|RabbitMQ|";
    public static final String PERMISSIVE_SSL_FLAG = "false";

    private String permissiveSSL = PERMISSIVE_SSL_FLAG;
    private String metricPrefix = DEFAULT_METRIC_PREFIX;
    //Holds the Key-Description Mapping
    private Map<String, String> dictionary;
    //Items in Nodes|<node>|Messages - data looked up from /api/channels
    private List<String> channelNodeMsgProps = Arrays.asList("ack", "deliver", "deliver_no_ack", "get_no_ack", "publish", "redeliver");
    //Items in Nodes|<node>|Messages - data looked up from /api/queues
    private List<String> queueNodeMsgProps = Arrays.asList("messages_ready", "messages_unacknowledged");
    //Items in Nodes|<node>|Consumers - data looked up from /api/queues
    private List<String> queueNodeProps = Arrays.asList("consumers");
    //Items in Queues|<host>|<QName>|Messages - data looked up from /api/queues
    private List<String> queueMessageProps = Arrays.asList("messages_ready", "messages_unacknowledged");
    //Items in Queues|<host>|<QName>|Messages - data looked up from /api/queues/message_stats
    private List<String> queueMessageStatsProps = Arrays.asList("ack", "deliver_get", "deliver", "deliver_no_ack", "get", "get_no_ack", "publish", "redeliver");
    //Items in Summary|Messages - data looked up from /api/queues
    private List<String> queueSummaryProps = Arrays.asList("messages_ready", "deliver_get", "publish", "redeliver", "messages_unacknowledged");


    public RabbitMQMonitor() {
        String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
        logger.info(msg);
        System.out.println(msg);
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
    }


    public TaskOutput execute(Map<String, String> argsMap, TaskExecutionContext executionContext) throws TaskExecutionException {
        logger.info("Starting the RabbitMQ Metric Monitoring task");
        try {
            argsMap = checkArgs(argsMap);
            metricPrefix = argsMap.get("metricPrefix");
            if (logger.isDebugEnabled()) {
                logger.debug("The arguments after appending the default values are " + argsMap);
            }
            String encodedUserPass = encodeUserPass(argsMap);
            String base = buildBaseUrl(argsMap);
            ArrayNode nodes = invokeApi(base + "/nodes", encodedUserPass);
            ArrayNode channels = invokeApi(base + "/channels", encodedUserPass);
            ArrayNode queues = invokeApi(base + "/queues", encodedUserPass);
            process(nodes, channels, queues);
            logger.info("Completed the RabbitMQ Metric Monitoring task");
        } catch (Exception e) {
            logger.error("Unexpected error while running the RabbitMQ Monitor", e);
        }
        return new TaskOutput("RabbitMQ Metric Upload Complete ");
    }

    private void process(ArrayNode nodes, ArrayNode channels, ArrayNode queues) {
        parseNodeData(nodes, channels, queues);
        parseQueueData(queues);
    }

    /**
     * Iterate over the available queue.message_status. The prefix will be Queues|$host|$QName
     *
     * @param queues
     */
    private void parseQueueData(ArrayNode queues) {
        if (queues != null) {
            Map<String, BigInteger> valueMap = new HashMap<String, BigInteger>();
            for (JsonNode queue : queues) {
                //Rabbit MQ queue names are case sensitive,
                // however the controller bombs when there are 2 metrics with same name in different cases.
                String qName = lower(getStringValue("name", queue, "Default"));
                String vHost = getStringValue("vhost", queue, "Default");
                if (vHost.equals("/")) {
                    vHost = "Default";
                }
                String prefix = "Queues|" + vHost + "|" + qName;
                BigInteger consumers = getBigIntegerValue("consumers", queue, 0);
                printCollectiveObservedCurrent(prefix + "|Consumers", consumers);
                String msgPrefix = prefix + "|Messages|";
                for (String prop : queueMessageProps) {
                    BigInteger value = getBigIntegerValue(prop, queue, 0);
                    printCollectiveObservedCurrent(msgPrefix + getPropDesc(prop), value);
                    addToMap(valueMap, prop, value);
                }

                //These are from message_stats
                JsonNode msgStats = queue.get("message_stats");
                for (String prop : queueMessageStatsProps) {
                    BigInteger value = getBigIntegerValue(prop, msgStats, 0);
                    printCollectiveObservedCurrent(msgPrefix + getPropDesc(prop), value);
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
        } else {
            printCollectiveObservedCurrent("Summary|Queues", new BigInteger("0"));
        }
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
     * Defaults the value if not present.
     *
     * @param argsMapsActual
     * @return
     */
    protected Map<String, String> checkArgs(Map<String, String> argsMapsActual) {
        Map<String, String> newArgsMap;
        if (argsMapsActual != null) {
            newArgsMap = new HashMap<String, String>(argsMapsActual);
        } else {
            newArgsMap = new HashMap<String, String>();
        }
        if (newArgsMap.get("username") == null) {
            newArgsMap.put("username", "guest");
        }
        if (newArgsMap.get("password") == null) {
            newArgsMap.put("password", "guest");
        }
        if (newArgsMap.get("host") == null) {
            newArgsMap.put("host", "localhost");
        }
        if (newArgsMap.get("port") == null) {
            newArgsMap.put("port", "15672");
        }
        if (newArgsMap.get("useSSL") == null) {
            newArgsMap.put("useSSL", "false");
        }
        if(newArgsMap.get("permissiveSSL") == null) {
            newArgsMap.put("permissiveSSL", PERMISSIVE_SSL_FLAG);
        } else if (newArgsMap.get("permissiveSSL").equals("true")) {
            disableCertificateValidation();
        }
        String prefix = newArgsMap.get("metricPrefix");
        if (prefix == null) {
            newArgsMap.put("metricPrefix", DEFAULT_METRIC_PREFIX);
        } else {
            String trim = prefix.trim();
            Pattern compile = Pattern.compile("(.+?)(\\|+)");
            Matcher matcher = compile.matcher(trim);
            if (matcher.matches()) {
                trim = matcher.group(1);
            }
            newArgsMap.put("metricPrefix", trim + "|");
        }
        return newArgsMap;
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

    public static void disableCertificateValidation() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }};

        // Ignore differences between given hostname and certificate hostname
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) { return true; }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (Exception e) {}
    }

    public void printMetric(String metricName, BigInteger metricValue, String aggregation, String timeRollup, String cluster) {
        MetricWriter metricWriter = getMetricWriter(metricPrefix + metricName,
                aggregation,
                timeRollup,
                cluster
        );
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
        metricWriter.printMetric(value);
    }

    private void printCollectiveObservedCurrent(String metricName, BigInteger metricValue) {
        printMetric(metricName, metricValue,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
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

    public static String getImplementationVersion() {
        return RabbitMQMonitor.class.getPackage().getImplementationTitle();
    }
}
