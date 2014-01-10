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
4. 
