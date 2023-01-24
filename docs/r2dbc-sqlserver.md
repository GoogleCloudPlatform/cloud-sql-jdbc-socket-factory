# Connecting to SQL Server using R2DBC

## Setup and Usage

### Adding the library as a dependency

##### Maven
Include the following in the project's `pom.xml`: 
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-sqlserver</artifactId>
      <version>1.9.0</version>
    </dependency>
```
##### Gradle
Include the following the project's `build.gradle`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-sqlserver:1.9.0'
```
*Note: Also include the R2DBC Driver for SQL Server, `io.r2dbc:r2dbc-mssql:<LATEST-VERSION>`

### Creating the R2DBC URL

##### SQL Server
R2DBC URL template: `r2dbc:gcp:mssql://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>`

Add the following parameters:

| Property         | Value         |
| ---------------- | ------------- |
| DATABASE_NAME   | The name of the database to connect to |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER         | SQL Server username |
| DB_PASS         | SQL Server user's password |

## Examples

Examples for using the Cloud SQL JDBC Connector for Postgres can be found by looking at the integration tests in this repository.
* [Usage example](../r2dbc/sqlserver/src/test/java/com/google/cloud/sql/core/R2dbcSqlServerIntegrationTests.java)