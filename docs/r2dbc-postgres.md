# Connecting to Postgres using R2DBC

## Setup and Usage

### Adding the library as a dependency

##### Maven
Include the following in the project's `pom.xml`: 
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-postgres</artifactId>
      <version>1.9.0</version>
    </dependency>
```
##### Gradle
Include the following the project's `build.gradle`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-postgres:1.9.0'
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

### IAM Authentication
*Note:* This feature is currently only supported for Postgres drivers.
Connections using 
[IAM database authentication](https://cloud.google.com/sql/docs/postgres/iam-logins) 
are supported when connecting to Postgres instances.
This feature is unsupported for other drivers. First, make sure to
[configure your Cloud SQL Instance to allow IAM authentication](https://cloud.google.com/sql/docs/postgres/create-edit-iam-instances#configure-iam-db-instance)
and
[add an IAM database user](https://cloud.google.com/sql/docs/postgres/create-manage-iam-users#creating-a-database-user).
Now, you can connect using user or service
account credentials instead of a password. 
When setting up the connection, set the `ENABLE_IAM_AUTH` connection property to `true` and `user`
to the email address associated with your IAM user. 

Example:
```java
    // Set up ConnectionFactoryOptions
    ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
        .option(DRIVER, "gcp")
        .option(PROTOCOL, "postgresql")
        .option(PASSWORD, "password")
        .option(USER, "postgres-iam-user@gmail.com")
        .option(DATABASE, "my_db")
        .option(HOST, "project:region:instance")
        .option(ENABLE_IAM_AUTH, true)
        .build();

    // Initialize connection pool
    ConnectionFactory connectionFactory = ConnectionFactories.get(options);
    ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
        .builder(connectionFactory)
        .build();

    this.connectionPool = new ConnectionPool(configuration);
```

Note: a non-empty string value for the `password` property must be set. While this property will
be ignored when connecting with the Cloud SQL Connector using IAM auth, leaving it empty will cause
driver-level validations to fail.

## Examples

Examples for using the Cloud SQL JDBC Connector for Postgres can be found by looking at the integration tests in this repository.
* [Usage example](../r2dbc/postgres/src/test/java/com/google/cloud/sql/core/R2dbcPostgresIntegrationTests.java)