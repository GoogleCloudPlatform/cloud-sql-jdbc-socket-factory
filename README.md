## Cloud SQL Connector for Java

The Cloud SQL Connector for Java is a library for the MySQL/PostgreSQL/SQL Server JDBC and R2DBC drivers that allows a user 
with the appropriate permissions to connect to a Cloud SQL database without having to deal with IP 
allowlisting or SSL certificates manually.

## Usage
For usage information specific to your database engine and driver, see the pages below:
* [Connecting to MySQL using JDBC](docs/jdbc-mysql)
* [Connecting to Postgres using JDBC](docs/jdbc-postgres.md)
* [Connecting to SQL Server using JDBC](docs/jdbc-sqlserver.md)
* [Connecting to MySQL using R2DBC](docs/r2dbc-mysql.md)
* [Connecting to Postgres using R2DBC](docs/r2dbc-postgres.md)
* [Connecting to SQL Server using R2DBC](docs/r2dbc-sqlserver.md)
### Authentication

This library uses [Application Default Credentials](
https://developers.google.com/identity/protocols/application-default-credentials) to authenticate
the connection to the Cloud SQL server. For more details, see the previously mentioned link.

To activate credentials locally, use the following [gcloud](https://cloud.google.com/sdk/gcloud/) 
command: 
```bash
gcloud auth application-default login
```
---

### Building the Drivers
To build a fat JAR containing the JDBC driver with the bundles Socket Factory dependencies you can issue the following Maven command from the location containing the project pom.xml:

```mvn -P jar-with-dependencies clean package -DskipTests```

This will create a *target* sub-folder within each of the module directories. Within these target directories you'll find the JDBC driver files.

Example:
```
mysql-socket-factory-connector-j-8–1.2.1-jar-with-dependencies.jar
postgres-socket-factory-1.2.1-jar-with-dependencies.jar
```

---

### Firewall configuration

The Cloud SQL proxy establishes connections to Cloud SQL instances using port 3307. Applications 
that are protected by a firewall may need to be configured to allow outgoing connections on TCP port
3307. A connection blocked by a firewall typically results in an error stating connection failure 
(e.g. `com.mysql.jdbc.exceptions.jdbc4.CommunicationsException: Communications link failure`).

### Connect with IntelliJ
 
In order to [connect IntelliJ](https://jetbrains.com/help/idea/connecting-to-a-database.html#mysql) 
to your Cloud SQL instance, you will need to add this library as a jar with dependencies in
"Additional Files" section on the driver settings page. Prebuilt fat jars can be found on the 
[Releases](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/releases) page for 
this purpose. 
 

## Examples

For examples of this library being used in the context of an application, check out the sample applications located 
[here](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/master/cloud-sql).

## Troubleshooting

Here are some troubleshooting tips to resolve common issues that come up when using the Java Connector:

1. Make sure you are using the latest version of the Cloud SQL Connector and your database driver to avoid incompatibilities. Some older versions of drivers are not supported
1. The Java connector provides authorization for connections, but it does not provide new paths to connectivity. For example, in order to connect to a Cloud SQL instance using a Private IP address, your application must already have VPC access. 
