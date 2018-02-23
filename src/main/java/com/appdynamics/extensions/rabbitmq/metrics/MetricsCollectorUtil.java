/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.TaskInputArgs;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsCollectorUtil {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectorUtil.class);

    protected Map<String,String> getUrlParametersMap(InstanceInfo info) {
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

    protected void populateMetricsMap(Map<String, List<Map<String, String>>> allMetricsFromConfig, List<Map<String, List<Map<String, String>>>> metricsFromConfig){
        for(Map<String, List<Map<String, String>>> metricsConfigEntry: metricsFromConfig){
            allMetricsFromConfig.putAll(metricsConfigEntry);
        }
    }

    protected String getStringValue(String propName, JsonNode node) {
        JsonNode jsonNode = node.get(propName);
        if (jsonNode != null) {
            return jsonNode.getTextValue();
        }
        return null;
    }

    protected String getStringValue(String propName, JsonNode node, String defaultVal) {
        String value = getStringValue(propName, node);
        return value != null ? value : defaultVal;
    }

    protected Boolean getBooleanValue(String propName, JsonNode node) {
        JsonNode jsonNode = node.get(propName);
        if (jsonNode != null) {
            return jsonNode.getBooleanValue();
        }
        return null;
    }

    protected BigInteger getBigIntegerValue(String propName, JsonNode node) {
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

    /**
     * Calculates metric Value for given data
     * @param dataMap
     * @param node
     * @return
     */
    protected BigInteger getMetricValue(Map<String, String> dataMap, JsonNode node){

        BigInteger metricValue;
        if(Boolean.valueOf(dataMap.get("isBoolean"))){
            metricValue = getNumericValueForBoolean(dataMap.get("name"), node, -1);
        }else{
            metricValue = getBigIntegerValue(dataMap.get("name"), node, 0);
        }

        /*if(StringUtils.hasText(dataMap.get("divisor"))){
            BigInteger data = getBigIntegerValue(dataMap.get("name"), node, 0);
            metricValue = applyDivisor(new BigDecimal(data), dataMap.get("divisor"));
        }*/
        return metricValue;
    }

    protected BigInteger getChildrenCount(String prop, JsonNode node, int defaultValue) {
        if (node != null) {
            final JsonNode metricNode = node.get(prop);
            if (metricNode != null && metricNode instanceof ArrayNode) {
                final ArrayNode arrayOfChildren = (ArrayNode) metricNode;
                return BigInteger.valueOf(arrayOfChildren.size());
            }
        }
        return BigInteger.valueOf(defaultValue);
    }

    protected BigInteger getBigIntegerValue(String propName, JsonNode node, int defaultVal) {
        BigInteger value = getBigIntegerValue(propName, node);
        return value != null ? value : new BigInteger(String.valueOf(defaultVal));
    }

    protected String lower(String value) {
        if (value != null) {
            return value.toLowerCase();
        }
        return value;
    }

    protected BigInteger getNumericValueForBoolean(String key, JsonNode node, int defaultValue) {
        final Boolean booleanValue = getBooleanValue(key, node);
        if (booleanValue == null) {
            return BigInteger.valueOf(defaultValue);
        } else {
            return booleanValue ? BigInteger.ONE : BigInteger.ZERO;
        }
    }
}
