package com.appdynamics.extensions.rabbitmq;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TaskInputArgs;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.appdynamics.extensions.rabbitmq.conf.InstanceInfo;
import com.appdynamics.extensions.rabbitmq.conf.Instances;
import com.appdynamics.extensions.rabbitmq.conf.QueueGroup;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: abey.tom
 * Date: 11/20/13
 * Time: 5:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class RabbitMQMonitor extends ABaseMonitor {
	public static final Logger logger = Logger.getLogger("com.singularity.extensions.rabbitmq.RabbitMQMonitor");
	public static final String DEFAULT_METRIC_PREFIX = "Custom Metrics|RabbitMQ|";

	private String metricPrefix = DEFAULT_METRIC_PREFIX;

	//Holds the Key-Description Mapping
	private Map<String, String> dictionary;


	protected Instances instances = new Instances();

	public RabbitMQMonitor() {
		String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
		logger.info(msg);
		dictionary = new HashMap<String, String>();
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
				if(!Strings.isNullOrEmpty((String) instance.get("host"))){
					info.setHost((String) instance.get("host"));
				}
				else{
					info.setHost("localhost");
				}
				if(!Strings.isNullOrEmpty((String) instance.get("username"))){
					info.setUsername((String) instance.get("username"));
				}
				else{
					info.setUsername("guest");
				}

				if(!Strings.isNullOrEmpty((String) instance.get("password"))){
					info.setPassword((String) instance.get("password"));
				}
				else if(!Strings.isNullOrEmpty((String) instance.get("encryptedPassword"))){
					try {
						Map<String, String> args = Maps.newHashMap();
						args.put(TaskInputArgs.PASSWORD_ENCRYPTED, (String)instance.get("encryptedPassword"));
						args.put(TaskInputArgs.ENCRYPTION_KEY, (String)instance.get("encryptionKey"));
						info.setPassword(CryptoUtil.getPassword(args));

					} catch (IllegalArgumentException e) {
						String msg = "Encryption Key not specified. Please set the value in config.yml.";
						logger.error(msg);
						throw new IllegalArgumentException(msg);
					}
				}
				else{
					info.setPassword("guest");
				}
				if(instance.get("port")!=null){
					info.setPort((Integer) instance.get("port"));
				}
				else{
					info.setPort(15672);
				}
				if(instance.get("useSSL")!=null){
					info.setUseSSL((Boolean) instance.get("useSSL"));
				}
				else{
					info.setUseSSL(false);
				}
				if(instance.get("connectTimeout")!=null){
					info.setConnectTimeout((Integer) instance.get("connectTimeout"));
				}
				else{
					info.setConnectTimeout(10000);
				}
				if(instance.get("socketTimeout")!=null){
					info.setSocketTimeout((Integer) instance.get("connectTimeout"));
				}
				else{
					info.setSocketTimeout(10000);
				}
				instancesToSet[index++] = info;
			}
			this.instances.setExcludeQueueRegex((String) configYml.get("excludeQueueRegex"));
			this.instances.setInstances(instancesToSet);
		}
		else{
			logger.error("no instances configured");
		}
		List<Map<String,?>> queueGroups = (List<Map<String, ?>>) configYml.get("queueGroups");
		if(queueGroups!=null && queueGroups.size()>0){
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

		dictionary.putAll((Map<String, String>)configYml.get("dictionary"));

	}

	public static String getImplementationVersion() {
		return RabbitMQMonitor.class.getPackage().getImplementationTitle();
	}

	protected String getDefaultMetricPrefix() {
		return metricPrefix;
	}

	public String getMonitorName() {
		return "RabbitMQ Monitor";
	}

	protected void doRun(TasksExecutionServiceProvider tasksExecutionServiceProvider) {
		Map<String, ?> config = configuration.getConfigYml();
		initialiseInstances(this.configuration.getConfigYml());
		String excludeQueueRegex = instances.getExcludeQueueRegex();
		metricPrefix = configuration.getMetricPrefix() + "|";
		if(config!=null){
			for(InstanceInfo info : instances.getInstances()){
				RabbitMQMonitoringTask task = new RabbitMQMonitoringTask(configuration, info,dictionary,instances.getQueueGroups(),metricPrefix,excludeQueueRegex, tasksExecutionServiceProvider.getMetricWriteHelper());
				 tasksExecutionServiceProvider.submit((String) info.getDisplayName(), task);
			}
		}
		else{
			logger.error("Configuration not found");
		}
	}

	protected int getTaskCount() {
		return 0;
	}

	public static void main(String [] args){

			ConsoleAppender ca = new ConsoleAppender();
			ca.setWriter(new OutputStreamWriter(System.out));
			ca.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
			ca.setThreshold(Level.DEBUG);

			logger.getRootLogger().addAppender(ca);

			final RabbitMQMonitor monitor = new RabbitMQMonitor();

			final Map<String, String> taskArgs = new HashMap<String, String>();
			taskArgs.put("config-file", "/Users/akshay.srivastava/AppDynamics/extensions/rabbitmq-monitoring-extension/src/main/resources/config/config.yml");


			//monitor.execute(taskArgs, null);

			ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			scheduler.scheduleAtFixedRate(new Runnable() {
				public void run() {
					try {
						monitor.execute(taskArgs, null);
					} catch (Exception e) {
						logger.error("Error while running the task", e);
					}
				}
			}, 2, 30, TimeUnit.SECONDS);
	}
}
