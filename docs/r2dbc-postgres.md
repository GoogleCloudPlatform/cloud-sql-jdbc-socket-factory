# Connecting to Postgres using R2DBC

## Setup and Usage

### Adding the library as a dependency

##### Maven
Include the following in the project's `pom.xml`: 
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-postgres</artifactId>
      <version>1.2.1</version>
    </dependency>
```
##### Gradle
Include the following the project's `build.gradle`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-postgres:1.2.1'
```
*Note: Also include the R2DBC Driver for PostgreSQL, `io.r2dbc:r2dbc-postgresql:<LATEST-VERSION>`

### Creating the R2DBC URL

R2DBC URL template: `r2dbc:gcp:postgres://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>`

Add the following parameters:

| Property         | Value         |
| ---------------- | ------------- |
| DATABASE_NAME   | The name of the database to connect to |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER         | PostgreSQL username |
| DB_PASS         | PostgreSQL user's password |

## Examples

Examples for using the Cloud SQL JDBC Connector for Postgres can be found by looking at the integration tests in this repository.
* [Usage example](../r2dbc/postgres/src/test/java/com/google/cloud/sql/core/R2dbcPostgresIntegrationTests.java)