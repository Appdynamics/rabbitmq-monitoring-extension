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
3. Open monitor.xml and configure the RabbitMQ arguments.
<pre>
```
<argument name="host" is-required="true" default-value="localhost"/>
<argument name="port" is-required="true" default-value="15672"/>
<argument name="useSSL" is-required="true" default-value="false"/>
<argument name="username" is-required="true" default-value="guest"/>
<argument name="password" is-required="true" default-value="guest"/>
<argument name="metricPrefix" is-required="true" default-value="Custom Metrics|RabbitMQ|"/>
```
</pre>

##Metrics
The following metrics are reported
```
Custom Metrics|RabbitMQ|Nodes|<host>|Erlang Processes
Custom Metrics|RabbitMQ|Nodes|<host>|File Descriptors
Custom Metrics|RabbitMQ|Nodes|<host>|Memory(MB)
Custom Metrics|RabbitMQ|Nodes|<host>|Sockets
Custom Metrics|RabbitMQ|Nodes|<host>|Channels|Count
Custom Metrics|RabbitMQ|Nodes|<host>|Channels|Blocked
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Delivered
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Acknowledged
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Got No-Ack
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Delivered No-Ack
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Redelivered
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Published
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Available
Custom Metrics|RabbitMQ|Nodes|<host>|Messages|Pending Acknowledgements
Custom Metrics|RabbitMQ|Nodes|<host>|Consumers|Count
Custom Metrics|RabbitMQ|Summary|Channels
Custom Metrics|RabbitMQ|Summary|Consumers
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Consumers
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Acknowledged
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Available
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Delivered (Total)
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Delivered
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Delivered No-Ack
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Got
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Got No-Ack
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Published
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Redelivered
Custom Metrics|RabbitMQ|Queues|Default|<QName>|Messages|Pending Acknowledgements
Custom Metrics|RabbitMQ|Summary|Messages|Available
Custom Metrics|RabbitMQ|Summary|Messages|Delivered (Total)
Custom Metrics|RabbitMQ|Summary|Messages|Published
Custom Metrics|RabbitMQ|Summary|Messages|Redelivered
Custom Metrics|RabbitMQ|Summary|Messages|Pending Acknowledgements
Custom Metrics|RabbitMQ|Summary|Queues
```
##Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

##Community

Find out more in the [AppSphere](http://appsphere.appdynamics.com/t5/eXchange/RabbitMQ-Monitoring-Extension/idi-p/5717) community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).

