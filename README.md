#
# AppDynamics RabbitMQ Monitoring Extension:

## Use Case

RabbitMQ is open source message broker software that implements the Advanced Message Queuing Protocol (AMQP).
The RabbitMQ Monitoring extension collects metrics from an RabbitMQ management API and uploads them to the AppDynamics Controller.

## Prerequisite

1. Before the extension is installed, the prerequisites mentioned [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Prerequisites-Guide/ta-p/35213) need to be met. Please do not proceed with the extension installation if the specified prerequisites are not met.

2. Download and install [Apache Maven](https://maven.apache.org/) which is configured with `Java 8` to build the extension artifact from source. You can check the java version used in maven using command `mvn -v` or `mvn --version`. If your maven is using some other java version then please download java 8 for your platform and set JAVA_HOME parameter before starting maven.

3. The RabbitMQ Management Plugin must be enabled. Please refer to  [this page](http://www.rabbitmq.com/management.html) for more details.

4. The extension needs to be able to connect to RabbitMQ in order to collect and send metrics. To do this, you will have to either establish a remote connection in between the extension and the product, or have an agent on the same machine running the product in order for the extension to collect and send the metrics.

## Installation
1. Clone the "rabbitmq-monitoring-extension" repo using `git clone <repoUrl>` command.
2. Run 'mvn clean install' from "rabbitmq-monitoring-extension"
3. Unzip the `RabbitMQMonitor-<VERSION>.zip` from `target` folder to the "<MachineAgent_Dir>/monitors" directory
4. Edit the file config.yml as described below in Configuration Section, located in    <MachineAgent_Dir>/monitors/RabbitMQMonitor and update the RabbitMQ server(s) details.
5. All metrics to be reported are configured in metrics.xml. Users can remove entries from metrics.xml to stop the metric from reporting.
6. Restart the Machine Agent

Please place the extension in the **"monitors"** directory of your **Machine Agent** installation directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.

## Configuration

  1. Configure the "tier" under which the metrics need to be reported. This can be done by changing the value of `<TIER ID>` in
     metricPrefix: "Server|Component:`<TIER ID>`|Custom Metrics|RabbitMQ".
     For example,
     ```
     metricPrefix: "Server|Component:Extensions tier|Custom Metrics|RabbitMQ"
     ```
  2. Configure the RabbitMQ instances by specifying the name(required), host(required), port(required) of the RabbitMQ instance, password (only if authentication enabled),
     encryptedPassword(only if password encryption required). You can configure multiple instances as follows to report metrics
     For example,
     ```
     servers:
       - host: "localhost"
         port: 15672
         useSSL: false
         username: "guest"
         password: "guest"
         ##encryptedPassword : Encrypted Password to be used, In this case do not use normal password field as above
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
     ```
  3. Configure the encyptionKey for encryptionPasswords(only if password encryption required).
     For example,
     ```
     #Encryption key for Encrypted password.
     encryptionKey: "axcdde43535hdhdgfiniyy576"
     ```
  4. Configure the numberOfThreads
     For example,
     If number of servers that need to be monitored is 3, then number of threads required is 5 * 3 = 15
     ```
     numberOfThreads: 15
     ```
  5. Queue Group Configuration: The queue can be grouped and the metrics for the group of queues can be collected with this feature.
      The grouping can be used for a scenario where there was a large number of Queues(20+) and they were very short lived (hours to couple
      of days). Another use case if for example, there are 10 queues working on 'order placement' and 5 queues working on
      'user notification', then you can create a group for 'order placement' and get the collective stats.
      The queue stats will be grouped by the 'groupName' if the 'queueNameRegex' matches the name of the Queue.
      Example:
      ```
      queueGroups:
        # The stats from Queues matched by the 'queueNameRegex' will be reported under groupName
      - groupName: group1
        # A Regex to match the Queue Name
        queueNameRegex: queue.+
        # showIndividualStats  If set to false then the Individual Queue stats will not be reported.This will help if there are several short lived queues and an explosion of metrics in the controller can be avoided
        showIndividualStats: false

      - groupName: group2
        queueNameRegex: temp.+
        showIndividualStats: true
        ```
  6. Include Filters:  Use the regex in includes parameters of filters, to specify the nodes/queues you'd like to collect metrics on.
     Be default, the config.yml has includes filter set to include all nodes/queues.
     ```
     filter:
       nodes:
         includes: [".*"]
       queues:
         includes: [".*"]
     ```
  7. EndPoint Flags:  Use endpoint-flags to enable/disable(set flag to true/false) metrics for overview and federation-plugin of RabbitMQ.
     ```
     endpointFlags:
        federationPlugin: "false"
        overview: "true"
     ```

### Metrics

Please refer to metrics.xml file located at `<MachineAgentInstallationDirectory>/monitors/RabbitMQMonitor/` to view the metrics which this extension can report.

### Credentials Encryption

Please visit [this page](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

### Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130)

### Troubleshooting
1. Please ensure the RabbitMQ Management Plugin is enabled. Please check [this page](http://www.rabbitmq.com/management.html) for more details.
2. To test connectivity, execute curl command to the RabbitMQ Api endpoint from the same host where RabbitMQ monitor is deployed.
For E.g.,
```
#With Username and Password
curl -v -u <Username>:<Password> http://<host>:<port>/api/nodes

#Without Username and Password
curl -v http://<host>:<port>/api/nodes
```
3. Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension.

### Contributing

Always feel free to fork and contribute any changes directly here on [GitHub](https://github.com/Appdynamics/rabbitmq-monitoring-extension/).

### Version
|          Name            |  Version   |
|--------------------------|------------|
|Extension Version         |2.0.7       |
|Product Tested On         |3.2.0+      |
|Last Update               |11/10/2021 |
|Changes list              |[ChangeLog](https://github.com/Appdynamics/rabbitmq-monitoring-extension/blob/master/CHANGELOG.md)|

**Note**: While extensions are maintained and supported by customers under the open-source licensing model, they interact with agents and Controllers that are subject to [AppDynamics’ maintenance and support policy](https://docs.appdynamics.com/latest/en/product-and-release-announcements/maintenance-support-for-software-versions). Some extensions have been tested with AppDynamics 4.5.13+ artifacts, but you are strongly recommended against using versions that are no longer supported.
