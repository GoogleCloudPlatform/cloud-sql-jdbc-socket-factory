

# Connecting to SQL Server using R2DBC

## Setup and Usage

### Adding the library as a dependency


<!-- {x-release-please-start-version} -->

#### Maven
Include the following in the project's `pom.xml`:

##### Mysql
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-mysql</artifactId>
      <version>1.14.1</version>
    </dependency>
```

##### Postgres
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-postgres</artifactId>
      <version>1.14.1</version>
    </dependency>
```
##### SQL Server
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-sqlserver</artifactId>
      <version>1.14.1</version>
    </dependency>
```

#### Gradle
Include the following the project's `build.gradle`

##### Mysql

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-mysql:1.14.1'
```
*Note: Also include the R2DBC Driver for MySQL, `io.asyncer:r2dbc-mysql:<LATEST-VERSION>`

##### Postgres

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-postgres:1.14.1'
```
*Note: Also include the R2DBC Driver for PostgreSQL, `io.r2dbc:r2dbc-postgresql:<LATEST-VERSION>`

##### SQL Server

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-sqlserver:1.14.1'
```
*Note: Also include the R2DBC Driver for SQL Server, `io.r2dbc:r2dbc-mssql:<LATEST-VERSION>`

<!-- {x-release-please-end} -->

### Creating the R2DBC URL

Add the following parameters:

| Property         | Value         |
| ---------------- | ------------- |
| DATABASE_NAME   | The name of the database to connect to |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER         | MySQL username |
| DB_PASS         | MySQL user's password |

R2DBC URL template:

#### Mysql

```
r2dbc:gcp:mysql://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>
```

#### Postgres

```
r2dbc:gcp:postgres://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>
```

##### SQL Server

```
r2dbc:gcp:mssql://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>
```

#### Example

```java
// Set up URL parameters
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(DRIVER, "gcp")
    .option(PROTOCOL, "mysql") // OR "postgres" or "mssql"
    .option(PASSWORD, "<DB_PASSWORD>")
    .option(USER, "<DB_USER>")
    .option(DATABASE, "<DATABASE_NAME}")
    .option(HOST, "<CLOUD_SQL_CONNECTION_NAME>")
    .build();

ConnectionFactory connectionFactory = ConnectionFactories.get(options);
ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

ConnectionPool connectionPool = new ConnectionPool(configuration);
```

### IAM Authentication

*Note:* This feature is currently only supported for Mysql and Postgres drivers.

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

Note: a non-empty string value for the `password` property must be set. While this property will
be ignored when connecting with the Cloud SQL Connector using IAM auth, leaving it empty will cause
driver-level validations to fail.

#### Mysql

```java
    // Set up ConnectionFactoryOptions
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(DRIVER, "gcp")
    .option(PROTOCOL, "mysql")
    .option(PASSWORD, "password")
    .option(USER, "mysql-iam-user@gmail.com")
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


#### Postgres

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

#### SQL Server

Not Supported

## Service Account Delegation
The Java Connector supports service account impersonation with the
`TARGET_PRINCIPAL` option. When enabled, all API requests are made impersonating
the supplied service account. The IAM principal must have the
iam.serviceAccounts.getAccessToken permission or the role
roles/iam.serviceAccounts.serviceAccountTokenCreator.

#### Mysql

```java
// Set up ConnectionFactoryOptions
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(DRIVER, "gcp")
    .option(PROTOCOL, "mysql")
    .option(PASSWORD, "password")
    .option(USER, "mysql-iam-user@gmail.com")
    .option(DATABASE, "my_db")
    .option(HOST, "project:region:instance")
    .option(ENABLE_IAM_AUTH, true)
    .option(TARGET_PRINCIPAL, "mysql-iam-user@gmail.com")
    .build();

// Initialize connection pool
ConnectionFactory connectionFactory = ConnectionFactories.get(options);
ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

this.connectionPool = new ConnectionPool(configuration);
```


#### Postgres

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
    .option(TARGET_PRINCIPAL, "postgres-iam-user@gmail.com,db-service-account@iam.gooogle.com")
    .build();

// Initialize connection pool
ConnectionFactory connectionFactory = ConnectionFactories.get(options);
ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

this.connectionPool = new ConnectionPool(configuration);
```


#### SQL Server

Not Supported.

### Delegated Service Account Impersonation

In addition, the `DELEGATES` option controls impersonation delegation.
The value is a comma-separated list of service accounts containing chained
list of delegates required to grant the final access_token. If set,
the sequence of identities must have "Service Account Token Creator" capability
granted to the preceding identity. For example, if set to
`"serviceAccountB,serviceAccountC"`, the application default credentials must
have the Token Creator role on serviceAccountB. serviceAccountB must have
the Token Creator on serviceAccountC. Finally, C must have Token Creator on
target principal. If unset, the application default credential principal
must "Service Account Token Creator" capability granted that role on the
target principal service account.

#### Example 

```java
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(TARGET_PRINCIPAL, "TARGET_SERVICE_ACCOUNT");
    .option(DELEGATES, "SERVICE_ACCOUNT_1,SERVICE_ACCOUNT_2")
    // ...more connection options
    .build;
```

The environment's application default principal impersonates
SERVICE_ACCOUNT_1 which impersonates SERVICE_ACCOUNT_2 which then
impersonates the TARGET_SERVICE_ACCOUNT.

## Examples

Examples for using the Cloud SQL JDBC Connector can be found by looking at the integration tests in this repository.
* [Mysql usage example](../r2dbc/mysql/src/test/java/com/google/cloud/sql/core/R2dbcMysqlIntegrationTests.java)
* [Postgres usage example](../r2dbc/postgres/src/test/java/com/google/cloud/sql/core/R2dbcPostgresIntegrationTests.java)
* [SQL Sql usage example](../r2dbc/sqlserver/src/test/java/com/google/cloud/sql/core/R2dbcSqlServerIntegrationTests.java)