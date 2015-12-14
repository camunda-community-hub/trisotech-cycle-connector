# Camunda Cycle Trisotech Connector

This is a Trisotech Connector for [camunda Cycle][1]. It contains the implementation of a connector which persists and syncronizes bmpn models between the a triotech repository and another location, like a fire repository.  
![Create Connector Screenshot][2]

## How to use it?

1. Checkout the project with Git
2. Build the project with maven
3. Deploy the jar file to a cycle distribution (see [installation guide][3])
4. Update the `connector-configurations.xml` file
4. Start Cycle, goto Connectors and add a new Example Connector

## Maintainers

[Trisotech][4]

[1]: https://docs.camunda.org/manual/7.4/webapps/cycle/
[2]: docs/screenshot.png
[3]: https://docs.camunda.org/manual/7.4/installation/cycle/#add-connectors
[4]: http://trisotech.com
