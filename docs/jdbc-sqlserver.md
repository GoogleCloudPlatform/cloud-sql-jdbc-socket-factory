# Connecting to SQL Server using JDBC

## Setup and Usage

### Add library as a dependency

##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>cloud-sql-connector-jdbc-sqlserver</artifactId>
    <version>1.2.1</version>
</dependency>
```

##### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-jdbc-sqlserver:1.2.1'
```
*Note*: Also include the JDBC Driver for SQL Server, `com.microsoft.sqlserver:mssql-jdbc:<LATEST-VERSION>`.
The minimum compatible version is 9.1.0.
### Create the JDBC URL

Base JDBC URL: `jdbc:sqlserver://localhost;databaseName=<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactoryClass    | com.google.cloud.sql.mysql.SocketFactory |
| socketFactoryConstructorArg | The instance connection name (found on the instance details page) |
| user             | SQL Server username |
| password         | SQL Server user's password |

The full JDBC URL should look like this:

```
jdbc:sqlserver://localhost;databaseName=<DATABASE_NAME>;socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory;socketFactoryConstructorArg=<INSTANCE_CONNECTION_NAME>;user=<USER_NAME>;password=<PASSWORD>
```

Note: The host portion of the JDBC URL is currently unused, and has no effect on the connection process. The SocketFactory will get your instances IP address based on the provided `socketFactoryConstructorArg` arg. 

## Examples

Examples for using the Cloud SQL JDBC Connector for Postgres can be found by looking at the integration tests in this repository.
* [Usage example](../sqlserver/src/test/java/com/google/cloud/sql/mysql/JdbcSqlServerIntegrationTests.java)
