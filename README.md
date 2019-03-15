# Camunda Cycle Trisotech Connector

This is a Trisotech Connector for [camunda Cycle][1]. It contains the implementation of a connector which persists and synchronizes BPMN models between the a Trisotech repository and another location, like a file repository.  

![Create Connector Screenshot][2]

**It is important to note that the username could be anything but the password must be a valid authentication token obtained from accessing the url:{trisotechBaseUrl}/publicapi/login from a browser and copy/pasting the value of the authToken variable in the response.**
The trisotechBaseUrl should only be modified if your company have a private Digital Enterprise Server.


## How to use it?

1. Checkout the project with Git
2. Build the project with maven: `mvn install`
3. Deploy the jar file to a cycle distribution (see [installation guide][3])
4. Update the `connector-configurations.xml` file by adding:
```
  <bean name="trisotechConnectorDefinition" class="org.camunda.bpm.cycle.entity.ConnectorConfiguration">
    <property name="name" value="Trisotech Connector"/>
    <property name="connectorClass" value="org.camunda.bpm.cycle.connector.trisotech.TrisotechConnector"/>
    <property name="properties">
      <map>
        <entry key="trisotechBaseUrl" value="https://cloud.trisotech.com"></entry>
        <entry key="proxyUrl" value=""></entry>
        <entry key="proxyUsername" value=""></entry>
        <entry key="proxyPassword" value=""></entry>
      </map>
    </property>
  </bean>

```
5. In the previous xml file, you can directly modify your trisotechBaseUrl attribute to directly insert your own instance URL
6. Start Cycle, goto Connectors and add a new Example Connector

## Maintainers

[Trisotech][4]

To enable logging, you can modify the logging.properties file and add: `org.camunda.bpm.cycle.connector.trisotech.level = FINE`


[1]: https://docs.camunda.org/manual/7.10/webapps/cycle/
[2]: docs/screenshot.png
[3]: https://docs.camunda.org/manual/7.10/installation/cycle/#add-connectors
[4]: http://trisotech.com
