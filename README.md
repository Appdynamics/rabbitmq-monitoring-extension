# AppDynamics RabbitMQ Monitoring Extension

This extension works only with the standalone machine agent.

##Use Case

RabbitMQ is open source message broker software that implements the Advanced Message Queuing Protocol (AMQP). It is written in the Erlang programming language.
The RabbitMQ Monitoring extension collects metrics from an RabbitMQ messaging server and uploads them to the AppDynamics Controller.

## Prerequisite

The RabbitMQ Management Plugin must be enabled. Please refer to http://www.rabbitmq.com/management.html for more details.

## Installation

The RabbitMQ Management Plugin must be enabled. Please refer to RabbitMQ Management API for more details.

1. Download and unzip the RabbitMQMonitor.zip to the "<MachineAgent_Dir>/monitors" directory
2. Edit the file config.yml as described below in Configuration Section, located in    <MachineAgent_Dir>/monitors/RabbitMQMonitor and update the RabbitMQ server(s) details.
3. Restart the Machine Agent

## Configuration

1. Queue Group Configuration

   The queue can be grouped and the metrics for the group of queues can be collected with this feature. The grouping can be   used for a scenario where there was a large number of Queues(20+) and they were very short lived (hours to couple of days). Another use case if for example, there are 10 queues working on 'order placement' and 5 queues working on 'user notification', then you can create a group for 'order placement' and get the collective stats.

   This will create a new tree node named "Queue Groups" as a sibling of "Queues". There is a file named "monitors/RabbitMQMonitor/config.yml" where you add the queue configuration.
You can also exclude one or more queue(s) by supplying a regex to match such queue names. Please take a look at config.yml for detailed information.

2. Instances Configuration

   The extension supports reporting metrics from multiple rabbitMQ instances. Have a look at config.yml for more details.

   Configure the extension by editing the config.yaml file in `<MACHINE_AGENT_HOME>/monitors/RabbitMQMonitor/`. Below is the format


``` yaml
############
## Queue Group Configuration. The queue stats will be grouped by the 'groupName'
## if the 'queueNameRegex' matches the name of the Queue.

## groupName            The stats from Queues matched by the 'queueNameRegex' will be reported under this name
## queueNameRegex       A Regex to match the Queue Name
## showIndividualStats  If set to false then the Individual Queue stats will not be reported.
##                      This will help if there are several short lived queues and an explosion of metrics
##                      in the controller can be avoided
############

# Uncomment the following lines for configuration
queueGroups:
- groupName: group1
  queueNameRegex: queue.+
  showIndividualStats: false

# Queue Group Configuration
#- groupName: group2
#  queueNameRegex: temp.+
#  showIndividualStats: false

####Exclude Queues###
excludeQueueRegex: ^[0-9]*
###The above regex can be supplied to exclude metric reporting for queue names that match this regex###

####### RabbitMQ Server Instances. You can configure multiple instances as follows to report metrics from #######
servers:
   - host: "localhost"
     port: 15672
     useSSL: false
     username: "guest"
     password: "guest"
     ##passwordEncrypted : Encrypted Password to be used, In this case do not use normal password field as above
     connectTimeout: 10000
     socketTimeout: 10000
     displayName: "displayName" //The display name to be used for the metrics of this server, mandatory

   - host: "localhost"
     port: 15673
     useSSL: false
     username: "guest"
     password: "guest"
     connectTimeout: 10000
     socketTimeout: 10000  
 	 displayName: "displayName1" //The display name to be used for the metrics of this server, mandatory

##encryptionKey: "myKey", the encryption key used to encrypt passowrd(s), same will be used to decrypt`

# number of concurrent tasks
numberOfThreads: 5

dictionary:
  ack: "Acknowledged"
  deliver: "Delivered"
  deliver_get: "Delivered (Total)"
  deliver_no_ack: "Delivered No-Ack"
  get: "Got"
  get_no_ack: "Got No-Ack"
  publish: "Published"
  redeliver: "Redelivered"
  messages_ready: "Available"
  messages_unacknowledged: "Pending Acknowledgements"
  consumers: "Count"
  active_consumers: "Active"
  idle_consumers: "Idle"
  slave_nodes: "Slaves Count"
  synchronised_slave_nodes: "Synchronized Slaves Count"
  down_slave_nodes: "Down Slaves Count"
  messages: "Messages"

metrics:
     #Items in Nodes||Messages - data looked up from /api/channels
   - channelNodeMsgProps:
         - name: "ack"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "deliver"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "deliver_no_ack"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "get_no_ack"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "publish"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "redeliver"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Queue Group data, stats combined by groupName defined in queueGroups above
   - queueGroupProps:
         - name: "consumers"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Nodes||Messages - data looked up from /api/queues
   - queueNodeMsgProps:
         - name: "messages_ready"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "messages_unacknowledged"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Nodes||Consumers - data looked up from /api/queues
   - queueNodeProps:
         - name: "consumers"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Queues|||Messages - data looked up from /api/queues
   - queueMessageProps:
         - name: "messages_ready"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "messages_unacknowledged"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Queues|||Replication - data looked up from /api/queues
   - queueReplicationCountsProps:
         - name: "slave_nodes"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "synchronised_slave_nodes"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "down_slave_nodes"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Queues|||Messages - data looked up from /api/queues/message_stats
   - queueMessageStatsProps:
         - name: "ack"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "deliver_get"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "deliver"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "deliver_no_ack"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "get"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "get_no_ack"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "publish"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "redeliver"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Summary|Messages - data looked up from /api/queues
   - queueSummaryProps:
         - name: "messages_ready"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "deliver_get"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "publish"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "redeliver"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "messages_unacknowledged"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Clusters||Queues - data looked up from /api/overview
   - queueTotalsProps:
         - name: "messages"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "messages_ready"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "messages_unacknowledged"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Clusters||Messages - data looked up from /api/overview
   - messageTotalsProps:
         - name: "publish"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "deliver_get"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "confirm"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "get"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
     #Items in Clusters||Objects - data looked up from /api/overview
   - objectTotalsProps:
         - name: "consumers"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "queues"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "exchanges"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "connections"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "channels"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
   #Items in Nodes| - data looked up from /api/nodes
   - nodeDataMetrics:
         - name: "proc_used"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           prefix: "|Erlang Processes"
         - name: "disk_free_alarm"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           isBoolean: "true"
           prefix: "|Disk Free Alarm Activated"
         - name: "mem_alarm"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           isBoolean: "true"
           prefix: "|Memory Free Alarm Activated"
         - name: "fd_used"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           prefix: "|File Descriptors"
         - name: "mem_used"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           divisor: "1048576"
           prefix: "|Memory(MB)"
         - name: "sockets_used"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           prefix: "|Sockets"
         - name: "channels_count"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           prefix: "|Channels|Count"
         - name: "channels_blocked"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           prefix: "|Channels|Blocked"
         - name: "summary_channels"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           prefix: "|Summary|Channels"
         - name: "consumers"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
           prefix: "|Summary|Consumers"
     #Per Minute Metrics, All these metric suffixes will be reported as per minute also
   - perMinMetricSuffixes:
         - name: "|Messages|Delivered (Total)"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "|Messages|Published"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "|Messages|Acknowledged"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"
         - name: "|Messages|Redelivered"
           metricType: "OBS.CUR.COL"
           collectDelta: "false"

#This will create this metric in all the tiers, under this path
metricPrefix: Custom Metrics | RabbitMQ |

#This will create it in specific Tier/Component. Make sure to replace  with the appropriate one from your environment.
#To find the  in your environment, please follow the screenshot https://docs.appdynamics.com/display/PRO42/Build+a+Monitoring+Extension+Using+Java
#metricPrefix: Server|Component:|Custom Metrics|RabbitMQ|
```
## Workbench

Workbench is a feature by which you can preview the metrics before registering it with the controller. This is useful if you want to fine tune the configurations. Workbench is embedded into the extension jar.
To use the workbench
Follow all the installation steps
Start the workbench with the command
      java -jar /monitors/RabbitMQMonitor/rabbitmq-monitoring-extension.jar


This starts an http server at http://host:9090/. This can be accessed from the browser.
If the server is not accessible from outside/browser, you can use the following end points to see the list of registered metrics and errors.
# Get the stats
    curl http://localhost:9090/api/stats
    #Get the registered metrics
    curl http://localhost:9090/api/metric-paths
You can make the changes to config.yml and validate it from the browser or the API
Once the configuration is complete, you can kill the workbench and start the Machine Agent.



## Password Encryption Support

To avoid setting the clear text password in the config.yml. Please follow the process to encrypt the password and set the encrypted password and the key in the config.yml
1. Download the util jar to encrypt the password from here
2. Encrypt password from the commandline
java -cp "appd-exts-commons-1.1.2.jar" com.appdynamics.extensions.crypto.Encryptor myKey myPassword
3. Add the properties in the config.yml. See sample config above


# Custom Dashboard
![](https://github.com/Appdynamics/rabbitmq-monitoring-extension/raw/master/RabbitMQCustomDashboard.png)

## Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

## Community

Find out more in the [AppSphere](http://appsphere.appdynamics.com/t5/eXchange/RabbitMQ-Monitoring-Extension/idi-p/5717) community.

## Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).
