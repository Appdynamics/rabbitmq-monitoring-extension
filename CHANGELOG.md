# AppDynamics Extensions RabbitMQ CHANGELOG

##2.0.0 - Mar 20, 2018
1. Moved Rabbit Mq to 2.0.0 framework
2. Moved metrics configurations from config.yml to metrics.xml
3. Introduced endPointFlags in config.yml to allow users to enable/disable federation and overview metrics
4. Added includes filter for nodes and queues, instead of previously used excludeQueueRegex
5. Added HeartBeat metric, to monitor successful connection to RabbitMQ instance.