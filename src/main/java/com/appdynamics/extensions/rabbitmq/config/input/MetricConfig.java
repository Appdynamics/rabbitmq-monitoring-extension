/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq.config.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.*;
import java.math.BigDecimal;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetricConfig {
    public static final Logger logger = LoggerFactory.getLogger(MetricConfig.class);

    @XmlAttribute
    private String attr;
    @XmlAttribute
    private String alias;
    @XmlAttribute
    private String delta;
    @XmlAttribute(name = "metric-type")
    private String metricType;
    @XmlAttribute
    private BigDecimal multiplier;
    @XmlElement(name="isBoolean")
    private String isBoolean;

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

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
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
}
