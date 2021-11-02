# Connecting to SQL Server using JDBC

## Setup and Usage

### Adding the library as a dependency

##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>cloud-sql-connector-jdbc-sqlserver</artifactId>
    <version>1.4.1-dev</version>
</dependency>
```

##### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-jdbc-sqlserver:1.4.1-dev'
```
*Note*: Also include the JDBC Driver for SQL Server, `com.microsoft.sqlserver:mssql-jdbc:<LATEST-VERSION>`.

### Creating theJDBC URL

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

Examples for using the Cloud SQL JDBC Connector for SQL Server can be found by looking at the integration tests in this repository.
* [Usage example](../jdbc/sqlserver/src/test/java/com/google/cloud/sql/sqlserver/JdbcSqlServerIntegrationTests.java)

## Reference Documentation
* [Connecting to Cloud SQL from App Engine Standard](https://cloud.google.com/sql/docs/sqlserver/connect-app-engine-standard)
* [Connecting to Cloud SQL from App Engine Flexible](https://cloud.google.com/sql/docs/sqlserver/connect-app-engine-flexible)
* [Connecting to Cloud SQL from Cloud Functions](https://cloud.google.com/sql/docs/sqlserver/connect-functions)
* [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/sqlserver/connect-run)