#
# AppDynamics RabbitMQ Monitoring Extension:

## Use Case

RabbitMQ is open source message broker software that implements the Advanced Message Queuing Protocol (AMQP).
The RabbitMQ Monitoring extension collects metrics from an RabbitMQ management API and uploads them to the AppDynamics Controller.

## Prerequisite

The RabbitMQ Management Plugin must be enabled. Please refer to  [this page](http://www.rabbitmq.com/management.html) for more details.

In order to use this extension, you do need a [Standalone JAVA Machine Agent](https://docs.appdynamics.com/display/PRO44/Java+Agent) or [SIM Agent](https://docs.appdynamics.com/display/PRO44/Server+Visibility).  For more details on downloading these products, please  visit [here](https://download.appdynamics.com/).

The extension needs to be able to connect to RabbitMQ in order to collect and send metrics. To do this, you will have to either establish a remote connection in between the extension and the product, or have an agent on the same machine running the product in order for the extension to collect and send the metrics.

## Installation

1. Download and unzip the RabbitMQMonitor.zip to the "<MachineAgent_Dir>/monitors" directory
2. Edit the file config.yml as described below in Configuration Section, located in    <MachineAgent_Dir>/monitors/RabbitMQMonitor and update the RabbitMQ server(s) details.
3. All metrics to be reported are configured in metrics.xml. Users can remove entries from metrics.xml to stop the metric from reporting.
4. Restart the Machine Agent


Please place the extension in the **"monitors"** directory of your **Machine Agent** installation directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.

## Configuration

#### Queue Group Configuration

   The queue can be grouped and the metrics for the group of queues can be collected with this feature. The grouping can be   used for a scenario where there was a large number of Queues(20+) and they were very short lived (hours to couple of days). Another use case if for example, there are 10 queues working on 'order placement' and 5 queues working on 'user notification', then you can create a group for 'order placement' and get the collective stats.

   This will create a new tree node named "Queue Groups" as a sibling of "Queues". There is a file named "monitors/RabbitMQMonitor/config.yml" where you add the queue configuration.
You can also exclude one or more queue(s) by supplying a regex to match such queue names. Please take a look at config.yml for detailed information.

#### Include Filters

    Use the regex in includes parameters of filters, to specify the nodes/queues you'd like to collect metrics on. Be default, the config.yml has includes filter set to include all nodes/queues.

#### EndPoint Flags

    Use endpoint-flags to enable/disable(set flag to true/false) metrics for overview and federation-plugin of RabbitMQ.

#### Instances Configuration

   The extension supports reporting metrics from multiple rabbitMQ instances. Have a look at config.yml for more details.

   Configure the extension by editing the config.yml file in `<MACHINE_AGENT_HOME>/monitors/RabbitMQMonitor/`. Below is the format


``` yaml
####### RabbitMQ Server Instances. You can configure multiple instances as follows to report metrics from #######
servers:
   - host: "localhost"
     port: 15672
     useSSL: false
     username: "guest"
     password: "guest"
     ##passwordEncrypted : Encrypted Password to be used, In this case do not use normal password field as above
     displayName: "displayName1" //The display name to be used for the metrics of this server, mandatory

   - host: "localhost"
     port: 15673
     useSSL: false
     username: "guest"
     password: "guest"
     displayName: "displayName2" //The display name to be used for the metrics of this server, mandatory

connection:
  socketTimeout: 10000
  connectTimeout: 10000

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

####Include Filters####
filter:
  nodes:
    includes: [".*"]
  queues:
    includes: [".*"]
###The above regex can be supplied to include metric reporting for nodes/queue names that match this regex###

####End point Flags. Enable/disable federation and overview metrics reporting####
endpointFlags:
  federationPlugin: "false"
  overview: "true"

##encryptionKey: "myKey", the encryption key used to encrypt passowrd(s), same will be used to decrypt`

# number of concurrent tasks
numberOfThreads: 5


#This will create this metric in all the tiers, under this path
#metricPrefix: Custom Metrics | RabbitMQ |

#This will create it in specific Tier/Component. Make sure to replace  with the appropriate one from your environment.
#To find the  in your environment, please follow the screenshot https://docs.appdynamics.com/display/PRO42/Build+a+Monitoring+Extension+Using+Java
metricPrefix: Server|Component:<Component_ID>|Custom Metrics|RabbitMQ|
```
## Credentials Encryption

Please visit [this page](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

## Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130)

## Troubleshooting
1. Please ensure the RabbitMQ Management Plugin is enabled. Please check "" section of [this page](http://www.rabbitmq.com/management.html) for more details.
2. Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension. If these don't solve your issue, please follow the last step on the [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) to contact the support team.

## Support Tickets
If after going through the [Troubleshooting Document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) you have not been able to get your extension working, please file a ticket and add the following information.

Please provide the following in order for us to assist you better.

    1. Stop the running machine agent.
    2. Delete all existing logs under <MachineAgent>/logs.
    3. Please enable debug logging by editing the file <MachineAgent>/conf/logging/log4j.xml. Change the level value of the following <logger> elements to debug.
        <logger name="com.singularity">
        <logger name="com.appdynamics">
    4. Start the machine agent and please let it run for 10 mins. Then zip and upload all the logs in the directory <MachineAgent>/logs/*.
    5. Attach the zipped <MachineAgent>/conf/* directory here.
    6. Attach the zipped <MachineAgent>/monitors/ExtensionFolderYouAreHavingIssuesWith directory here.

For any support related questions, you can also contact help@appdynamics.com.



## Contributing

Always feel free to fork and contribute any changes directly here on [GitHub](https://github.com/Appdynamics/rabbitmq-monitoring-extension/).

## Version
|          Name            |  Version   |
|--------------------------|------------|
|Extension Version         |2.0.0       |
|Controller Compatibility  |3.7 or Later|
|Product Tested On         |3.2.0+      |
|Last Update               |03/021/2018 |
