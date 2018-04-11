/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.config.input;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.math.BigDecimal;
import java.util.Map;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetricConfig {
    public static final Logger logger = LoggerFactory.getLogger(MetricConfig.class);

    @XmlAttribute
    private String attr;
    @XmlAttribute
    private String alias;
    @XmlAttribute
    private String delta;
    @XmlAttribute
    private String aggregationType;
    @XmlAttribute
    private String timeRollUpType;
    @XmlAttribute
    private String clusterRollUpType;
    @XmlAttribute
    private BigDecimal multiplier;
    @XmlElement(name="isBoolean")
    private String isBoolean= "false";
    @XmlElement(name = "convert")
    private MetricConverter[] convert;


    public String getAttr() {
        return attr;
    }

    public void setAttr(String attr) {
        this.attr = attr;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public String isBoolean() {
        return isBoolean;
    }

    public void setBoolean(String isBoolean) {
        this.isBoolean = isBoolean;
    }

    public String getDelta() {
        return delta;
    }

    public void setDelta(String delta) {
        this.delta = delta;
    }

    public String getAggregationType() {
        return aggregationType;
    }

    public void setAggregationType(String aggregationType) {
        this.aggregationType = aggregationType;
    }

    public String getTimeRollUpType() {
        return timeRollUpType;
    }

    public void setTimeRollUpType(String timeRollUpType) {
        this.timeRollUpType = timeRollUpType;
    }

    public String getClusterRollUpType() {
        return clusterRollUpType;
    }

    public void setClusterRollUpType(String clusterRollUpType) {
        this.clusterRollUpType = clusterRollUpType;
    }

    public Map<String, String> getConvert() {
        Map<String, String> converterMap = Maps.newHashMap();
        if(convert!=null && convert.length > 0) {
            return generateConverterMap(converterMap);
        }
        return converterMap;
    }

    private Map<String, String> generateConverterMap(Map<String, String> converterMap) {
        for(MetricConverter converter : convert) {
            converterMap.put(converter.getLabel(), converter.getValue());
        }
        return converterMap;
    }

    public void setConvert(MetricConverter[] convert) {
        this.convert = convert;
    }
}
