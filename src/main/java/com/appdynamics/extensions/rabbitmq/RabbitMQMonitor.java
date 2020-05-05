/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.rabbitmq;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.Constants;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.rabbitmq.config.input.Stat;
import com.appdynamics.extensions.rabbitmq.instance.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.instance.Instances;
import com.appdynamics.extensions.rabbitmq.queueGroup.QueueGroup;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.extensions.util.CryptoUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RabbitMQMonitor extends ABaseMonitor {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(RabbitMQMonitor.class);
    private static final String METRIC_PREFIX = "Custom Metrics|RabbitMQ|";

    protected Instances instances = new Instances();


    @Override
    protected String getDefaultMetricPrefix() {
        return METRIC_PREFIX;
    }

    @Override
    public String getMonitorName() {
        return "RabbitMQ Monitor";
    }


    private void initialiseInstances(Map<String, ?> configYml) {
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

                AssertUtils.assertNotNull(instances, "The 'host name is not initialised");
                info.setHost((String) instance.get("host"));

                AssertUtils.assertNotNull(instances, "The 'username is not initialised");
                info.setUsername((String) instance.get("username"));


                if(!Strings.isNullOrEmpty((String) instance.get("password"))){
                    info.setPassword((String) instance.get("password"));
                }
                else if(!Strings.isNullOrEmpty((String) instance.get("encryptedPassword"))){
                    try {
                        Map<String, String> args = Maps.newHashMap();
                        args.put(Constants.ENCRYPTED_PASSWORD, (String)instance.get("encryptedPassword"));
                        args.put(Constants.ENCRYPTION_KEY, (String)configYml.get("encryptionKey"));
                        logger.debug("Decrypting the encrypted password");
                        info.setPassword(CryptoUtils.getPassword(args));

                    } catch (IllegalArgumentException e) {
                        String msg = "Encryption Key not specified. Please set the value in config.yml.";
                        logger.error(msg);
                        throw new IllegalArgumentException(msg);
                    }
                }

                AssertUtils.assertNotNull(instance.get("port"), "The 'port' in config.yml is not initialised");
                info.setPort((Integer) instance.get("port"));

                if(instance.get("useSSL")!=null){
                    info.setUseSSL((Boolean) instance.get("useSSL"));
                }
                else{
                    info.setUseSSL(false);
                }
                instancesToSet[index++] = info;
            }
            this.instances.setInstances(instancesToSet);
        }
        else{
            logger.error("no instances configured");
        }
        List<Map<String,?>> queueGroups = (List<Map<String, ?>>) configYml.get("queueGroups");
        if(queueGroups != null && queueGroups.size() > 0){
            int index = 0;
            QueueGroup[] groups =new QueueGroup[queueGroups.size()];
            for(Map<String,?> group : queueGroups){
                QueueGroup g = new QueueGroup();
                g.setGroupName((String) group.get("groupName"));
                g.setQueueNameRegex((String) group.get("queueNameRegex"));
                g.setShowIndividualStats((Boolean) group.get("showIndividualStats"));
                groups[index++] = g;
            }
            this.instances.setQueueGroups(groups);
        }
        else{
            logger.debug("no queue groups defined");
        }


    }

    @Override
    protected void initializeMoreStuff(Map<String, String> args) {
        this.getContextConfiguration().setMetricXml(args.get("metric-file"), Stat.Stats.class);

    }

    @Override
    protected void doRun(TasksExecutionServiceProvider serviceProvider) {

        initialiseInstances(this.getContextConfiguration().getConfigYml());

        AssertUtils.assertNotNull(this.getContextConfiguration().getMetricsXml(), "Metrics xml not available");
        AssertUtils.assertNotNull(instances, "The 'instances' section in config.yml is not initialised");
        for (InstanceInfo instanceInfo : instances.getInstances()) {
            RabbitMQMonitorTask task = new RabbitMQMonitorTask(serviceProvider, this.getContextConfiguration(), instanceInfo, instances.getQueueGroups());
            AssertUtils.assertNotNull(instanceInfo.getDisplayName(), "The displayName can not be null");
            serviceProvider.submit((String) instanceInfo.getDisplayName(), task);
        }
    }

    protected List<Map<String, ?>> getServers() {
        List<Map<String, ?>> servers = (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get("servers");
        AssertUtils.assertNotNull(servers, "The 'servers' section in config.yml is not initialised");
        return servers;
    }

}
