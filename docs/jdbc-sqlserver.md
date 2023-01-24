# Connecting to SQL Server using JDBC

## Setup and Usage

### Adding the library as a dependency

##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>cloud-sql-connector-jdbc-sqlserver</artifactId>
    <version>1.9.0</version>
</dependency>
```

##### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-jdbc-sqlserver:1.9.0'
```
*Note*: Also include the JDBC Driver for SQL Server, `com.microsoft.sqlserver:mssql-jdbc:<LATEST-VERSION>`.

### Creating the JDBC URL

Base JDBC URL: `jdbc:sqlserver://localhost;databaseName=<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactoryClass    | com.google.cloud.sql.sqlserver.SocketFactory |
| socketFactoryConstructorArg | The instance connection name (found on the instance details page) |
| user             | SQL Server username |
| password         | SQL Server user's password |

The full JDBC URL should look like this:

```
jdbc:sqlserver://localhost;databaseName=<DATABASE_NAME>;socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory;socketFactoryConstructorArg=<INSTANCE_CONNECTION_NAME>;user=<USER_NAME>;password=<PASSWORD>
```

Note: The host portion of the JDBC URL is currently unused, and has no effect on the connection process. The SocketFactory will get your instances IP address based on the provided `socketFactoryConstructorArg` arg. 

### Specifying IP Types
 
"The `ipTypes` argument is used to specify a preferred order of IP types used to connect via a comma delimited list. For example, `ipTypes=PUBLIC,PRIVATE` will use the instance's Public IP if it exists, otherwise private. The value `ipTypes=PRIVATE` will force the Cloud SQL instance to connect via it's private IP. If not specified, the default used is `ipTypes=PUBLIC,PRIVATE`. 

IP types can be specified by appending the ipTypes argument to `socketFactoryConstructorArg` using query syntax, such as:

```
jdbc:sqlserver://localhost;databaseName=<DATABASE_NAME>;socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory;socketFactoryConstructorArg=<INSTANCE_CONNECTION_NAME>?ipTypes=PRIVATE;user=<USER_NAME>;password=<PASSWORD>
```

For more info on connecting using a private IP address, see [Requirements for Private IP](https://cloud.google.com/sql/docs/mysql/private-ip#requirements_for_private_ip).

## Examples

Examples for using the Cloud SQL JDBC Connector for SQL Server can be found by looking at the integration tests in this repository.
* [Usage example](../jdbc/sqlserver/src/test/java/com/google/cloud/sql/sqlserver/JdbcSqlServerIntegrationTests.java)

## Reference Documentation
* [Connecting to Cloud SQL from App Engine Standard](https://cloud.google.com/sql/docs/sqlserver/connect-app-engine-standard)
* [Connecting to Cloud SQL from App Engine Flexible](https://cloud.google.com/sql/docs/sqlserver/connect-app-engine-flexible)
* [Connecting to Cloud SQL from Cloud Functions](https://cloud.google.com/sql/docs/sqlserver/connect-functions)
* [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/sqlserver/connect-run)
