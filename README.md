# AppDynamics RabbitMQ Monitoring Extension

This extension works only with the standalone machine agent.

##Use Case
RabbitMQ is open source message broker software that implements the Advanced Message Queuing Protocol (AMQP). It is written in the Erlang programming language.
The RabbitMQ Monitoring extension collects metrics from an RabbitMQ messaging server and uploads them to the AppDynamics Controller. 

##Prerequisite
The RabbitMQ Management Plugin must be enabled. Please refer to http://www.rabbitmq.com/management.html for more details.

##Installation

1. Run "mvn clean install"
2. Download and unzip the file 'target/RabbitMQMonitor.zip' to \<machineagent install dir\>/monitors
3. Configure the config.yml. A sample config is as follows :


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
```

##Metrics
The following metrics are reported. The Metric Path is relative to the "metricPrefix" defined in the monitor.xml

| Metric Path  | Description  |
|---------------- |------------- |
| Nodes/{node}/Erlang Processes | The count of Erlang Processes running in the node |
| Nodes/{node}/File Descriptors | The count of open file descriptors in the node |
| Nodes/{node}/Memory(MB) | The memory in MB used by the node |
| Nodes/{node}/Sockets |  The count of open sockets in the node |
| Nodes/{node}/Channels/Count | The count of channels in the node |
| Nodes/{node}/Channels/Blocked |  The count of BLOCKED channels in the node |
| Nodes/{node}/Messages/Delivered | The count of messages 'deliver' in the node |
| Nodes/{node}/Messages/Acknowledged | The count of messages 'ack' in the node |
| Nodes/{node}/Messages/Got No-Ack | The count of messages with the status 'get_no_ack' in the node |
| Nodes/{node}/Messages/Delivered No-Ack | The count of messages with the status 'deliver_no_ack' in the node  |
| Nodes/{node}/Messages/Redelivered | The count of messages with the status 'redeliver' in the node |
| Nodes/{node}/Messages/Published | The count of messages with the status 'publish' in the node |
| Nodes/{node}/Messages/Available | The count of messages with the status 'messages_ready' in the node |
| Nodes/{node}/Messages/Pending Acknowledgements | The count of messages with the status 'messages_unacknowledged' in the node |
| Nodes/{node}/Consumers/Count | The count of consumers for the node |
| Queues/{vHost}/{qName}/Consumers | The consumer count of a queue in a host |
| Queues/{vHost}/{qName}/Messages/Acknowledged | The count of messages with the status 'ack' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Available | The count of messages with the status 'messages_ready' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Delivered (Total) | The count of messages with the status 'deliver_get' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Delivered | The count of messages with the status 'deliver' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Delivered No-Ack | The count of messages with the status 'deliver_no_ack' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Got | The count of messages with the status 'get' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Got No-Ack | The count of messages with the status 'get_no_ack' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Published | The count of messages with the status 'publish' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Redelivered | The count of messages with the status 'redeliver' in the host and the given queue |
| Queues/{vHost}/{qName}/Messages/Pending Acknowledgements | The count of messages with the status 'messages_unacknowledged' in the host and the given queue |
| Summary/Channels | The total number of channels registered in the server |
| Summary/Consumers | The total number of Consumers registered in the server |
| Summary/Messages/Available | The total count of messages with the status 'messages_ready' in the RabbitMQ server |
| Summary/Messages/Delivered (Total) | The total count of messages with the status 'deliver_get' in the RabbitMQ server |
| Summary/Messages/Published | The total count of messages with the status 'publish' in the RabbitMQ server |
| Summary/Messages/Redelivered | The total count of messages with the status 'redeliver' in the RabbitMQ server |
| Summary/Messages/Pending Acknowledgements | The total count of messages with the status 'messages_unacknowledged' in the RabbitMQ server |
| Summary/Queues | The count of queues in the RabbitMQ Server |


##Password Encryption Support 

To avoid setting the clear text password in the config.yml. Please follow the process to encrypt the password and set the encrypted password and the key in the config.yml
1. Download the util jar to encrypt the password from here 
2. Encrypt password from the commandline 
java -cp "appd-exts-commons-1.1.2.jar" com.appdynamics.extensions.crypto.Encryptor myKey myPassword 
3. Add the properties in the config.yml. See sample config above


#Custom Dashboard
![](https://github.com/Appdynamics/rabbitmq-monitoring-extension/raw/master/RabbitMQCustomDashboard.png)

##Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

##Community

Find out more in the [AppSphere](http://appsphere.appdynamics.com/t5/eXchange/RabbitMQ-Monitoring-Extension/idi-p/5717) community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).