/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.metrics;

import com.appdynamics.extensions.Constants;
import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.util.YmlUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsCollectorUtil {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(MetricsCollectorUtil.class);

    protected Map<String,String> getUrlParametersMap(InstanceInfo info) {
        Map<String,String> map = new HashMap<String, String>();
        map.put(Constants.HOST, info.getHost());
        map.put(Constants.PORT, info.getPort().toString());
        map.put(Constants.USER, info.getUsername());
        map.put(Constants.PASSWORD, info.getPassword());
        map.put(Constants.USE_SSL, info.getUseSSL().toString());
        checkForEnvironmentsOverride(map,info.getDisplayName());
        return map;

    }

    private void checkForEnvironmentsOverride(Map<String, String> map, String displayName) {
        String[] keys = new String[]{
                Constants.HOST,
                Constants.PORT,
                Constants.USER,
                Constants.PASSWORD,
                Constants.USE_SSL
        };
        for (String key:keys) {
            map.put(key, System.getProperty("APPD_RABBITMQ_ENV_" + key.toUpperCase(), map.get(key)));
            map.put(key, System.getProperty("APPD_RABBITMQ_ENV_" + key.toUpperCase() + "_" + displayName, map.get(key)));
        }
    }

    protected String getStringValue(String propName, JsonNode node) {
        JsonNode jsonNode = node.get(propName);
        if (jsonNode != null) {
            return jsonNode.textValue();
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
            return jsonNode.booleanValue();
        }
        return null;
    }

    protected BigInteger getBigIntegerValue(String propName, JsonNode node) {
        if (node != null) {
            JsonNode jsonNode = node.get(propName);
            if (jsonNode != null) {
                try {
                    return jsonNode.bigIntegerValue();
                } catch (Exception e) {
                    logger.warn("Cannot get the int value of the property "
                            + propName + " value is " + jsonNode.textValue());
                }
            }
        }
        return null;
    }

   /**
     * Calculates metric Value for given data
     * @param value
     * @param node
     * @return
     */
    protected BigInteger getMetricValue(String value, JsonNode node, String isBool){

        BigInteger metricValue;
        if(Boolean.valueOf(isBool)){
            metricValue = getNumericValueForBoolean(value, node, -1);
        }else{
            metricValue = getBigIntegerValue(value, node, 0);
        }
        return metricValue;
    }


    protected BigInteger getBigIntegerValue(String propName, JsonNode node, int defaultVal) {
        BigInteger value = getBigIntegerValue(propName, node);
        return value;
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

    /**
     * Adds the value to the Map. If the value is present it adds to the current value.
     * The map is used to calculate the aggregate.
     *
     * @param valueMap
     * @param prop
     * @param val
     */
    protected void addToMap(Map<String, BigInteger> valueMap, String prop, BigInteger val) {
        if (val != null) {
            BigInteger curr = valueMap.get(prop);
            if (curr == null) {
                valueMap.put(prop, val);
            } else {
                valueMap.put(prop, curr.add(val));
            }
        }
    }

    protected boolean isIncluded(Map filter, String entityName, Stat stat) {

            if (isIncluded(filter, entityName)) {
                return true;
            } else {
                logger.debug("The filter {} didnt match for entityName {} and url {}"
                        , filter, entityName, stat.getUrl());
                return false;
            }

    }

    //Apply the filter
    private boolean isIncluded(Map filter, String entityName) {
        if (filter != null) {
            List<String> includes = (List) filter.get("includes");
            logger.debug("For the entity name [{}], the includes filter is {}", entityName, includes);
            if (includes != null) {
                for (String include : includes) {
                    boolean matches = entityName.matches(include);
                    if (matches) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
