# Connecting to SQL Server using R2DBC

## Setup and Usage

### Adding the library as a dependency


#### Maven

Include the following in the project's `pom.xml` if your project uses Maven,
or in `build.gradle` if your project uses Gradle.

##### Mysql
<!-- {x-version-update-start:cloud-sql-connector-r2dbc-mysql:released} -->
Maven

```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-mysql</artifactId>
      <version>1.14.1</version>
    </dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-mysql:1.14.1'
```

**Note:** Also include the R2DBC Driver for
MySQL, `io.asyncer:r2dbc-mysql:<LATEST-VERSION>`
<!-- {x-release-please-end} -->

##### Postgres

<!-- {x-version-update-start:cloud-sql-connector-r2dbc-postgres:released} -->
Maven

```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-postgres</artifactId>
      <version>1.14.1</version>
    </dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-postgres:1.14.1'
```

**Note:** Also include the R2DBC Driver for
PostgreSQL, `io.r2dbc:r2dbc-postgresql:<LATEST-VERSION>`
<!-- {x-release-please-end} -->

##### SQL Server

<!-- {x-version-update-start:cloud-sql-connector-r2dbc-sqlserver:released} -->
Maven

```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-sqlserver</artifactId>
      <version>1.14.1</version>
    </dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-sqlserver:1.14.1'
```

**Note:** Also include the R2DBC Driver for SQL
Server, `io.r2dbc:r2dbc-mssql:<LATEST-VERSION>`
<!-- {x-release-please-end} -->

### Creating the R2DBC URL

Add the following connection properties:

| Property                  | Value                                                             |
|---------------------------|-------------------------------------------------------------------|
| DATABASE_NAME             | The name of the database to connect to                            |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER                   | Database username                                                 |
| DB_PASS                   | Database user's password                                          |

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
ConnectionFactoryOptions options=ConnectionFactoryOptions.builder()
    .option(DRIVER,"gcp")
    .option(PROTOCOL,"mysql") // OR "postgres" or "mssql"
    .option(PASSWORD,"<DB_PASSWORD>")
    .option(USER,"<DB_USER>")
    .option(DATABASE,"<DATABASE_NAME}")
    .option(HOST,"<CLOUD_SQL_CONNECTION_NAME>")
    .build();

    ConnectionFactory connectionFactory=ConnectionFactories.get(options);
    ConnectionPoolConfiguration configuration=ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

    ConnectionPool connectionPool=new ConnectionPool(configuration);
```

### IAM Authentication

**Note:** This feature is currently only supported for Mysql and Postgres
drivers.

Connections using
[IAM database authentication](https://cloud.google.com/sql/docs/postgres/iam-logins)
are supported when connecting to Mysql or Postgres instances.
This feature is unsupported for SQL Server. First, make sure to
[configure your Cloud SQL Instance to allow IAM authentication](https://cloud.google.com/sql/docs/postgres/create-edit-iam-instances#configure-iam-db-instance)
and
[add an IAM database user](https://cloud.google.com/sql/docs/postgres/create-manage-iam-users#creating-a-database-user).
Now, you can connect using user or service
account credentials instead of a password.
When setting up the connection, set the `ENABLE_IAM_AUTH` connection property
to `true` and `user`
to the email address associated with your IAM user.

You must shorten the full IAM user email into a database username. Due to
different constraints on allowed characters in the database username, Mysql and
postgres differ in how they shorten an IAM email address into a database
username.

* Mysql: Truncate the IAM email removing the `@` and everything that follows.
* Postgres: If the IAM email ends with `.gserviceaccount.com`, remove
  the `.gserviceaccount.com` suffix from the email.

For example, if the full IAM user account
is `my-sa@my-project.iam.gserviceaccount.com`,
You would update the code, replacing these examples with the appropriate values
for your database engine:

|               | Mysql | Postgres             |
|---------------|-------|----------------------|
| <PROTOCOL>    | mysql | postgresql           |  
| <IAM_DB_USER> | my-sa | my-sa@my-project.iam |

**Note:** a non-empty string value for the `password` property must be set.
While this property will be ignored when connecting with the Cloud SQL Connector
using IAM auth, leaving it empty will cause driver-level validations to fail.

```java
// Set up ConnectionFactoryOptions
ConnectionFactoryOptions options=ConnectionFactoryOptions.builder()
    .option(DRIVER,"gcp")
    .option(PROTOCOL,"<PROTOCOL>")
    .option(PASSWORD,"password")
    .option(USER,"<IAM_DB_USER>")
    .option(DATABASE,"my_db")
    .option(HOST,"project:region:instance")
    .option(ENABLE_IAM_AUTH,true)
    .build();

// Initialize connection pool
    ConnectionFactory connectionFactory=ConnectionFactories.get(options);
    ConnectionPoolConfiguration configuration=ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

    this.connectionPool=new ConnectionPool(configuration);
```

## Service Account Impersonation

**Note:** Only Mysql and Postgres support service account impersonation. SQL 
Server does not support Service Account Impersonation.

The Java Connector supports service account impersonation with the
`TARGET_PRINCIPAL` option. When enabled, all API requests are made impersonating
the supplied service account. The IAM principal must have the
iam.serviceAccounts.getAccessToken permission or the role
roles/iam.serviceAccounts.serviceAccountTokenCreator.

For example, if the full IAM user account
is `my-sa@my-project.iam.gserviceaccount.com`,
You would update the code, replacing these examples with the appropriate values
for your database engine:

|               | Mysql                                    | Postgres                                 |
|---------------|------------------------------------------|------------------------------------------|
| <PROTOCOL>    | mysql                                    | postgresql                               |  
| <IAM_DB_USER> | my-sa                                    | my-sa@my-proeject.iam                    |
| <IAM_EMAIL>   | my-sa@my-project.iam.gserviceaccount.com | my-sa@my-project.iam.gserviceaccount.com |

**Note:** a non-empty string value for the `password` property must be set.
While this property will be ignored when connecting with the Cloud SQL Connector
using IAM auth, leaving it empty will cause driver-level validations to fail.

```java
// Set up ConnectionFactoryOptions
ConnectionFactoryOptions options=ConnectionFactoryOptions.builder()
    .option(DRIVER,"gcp")
    .option(PROTOCOL,"<PROTOCOL>")
    .option(PASSWORD,"password")
    .option(USER,"<IAM_DB_USER>")
    .option(DATABASE,"my_db")
    .option(HOST,"project:region:instance")
    .option(ENABLE_IAM_AUTH,true)
    .option(TARGET_PRINCIPAL,"<IAM_EMAIL>")
    .build();

// Initialize connection pool
    ConnectionFactory connectionFactory=ConnectionFactories.get(options);
    ConnectionPoolConfiguration configuration=ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

    this.connectionPool=new ConnectionPool(configuration);
```

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
ConnectionFactoryOptions options=ConnectionFactoryOptions.builder()
    .option(TARGET_PRINCIPAL,"TARGET_SERVICE_ACCOUNT");
    .option(DELEGATES,"SERVICE_ACCOUNT_1,SERVICE_ACCOUNT_2")
    // ...more connection options
    .build;
```

The environment's application default principal impersonates
SERVICE_ACCOUNT_1 which impersonates SERVICE_ACCOUNT_2 which then
impersonates the TARGET_SERVICE_ACCOUNT.

## Examples

Examples for using the Cloud SQL JDBC Connector can be found by looking at the
integration tests in this repository.

* [Mysql usage example](../r2dbc/mysql/src/test/java/com/google/cloud/sql/core/R2dbcMysqlIntegrationTests.java)
* [Postgres usage example](../r2dbc/postgres/src/test/java/com/google/cloud/sql/core/R2dbcPostgresIntegrationTests.java)
* [SQL Sql usage example](../r2dbc/sqlserver/src/test/java/com/google/cloud/sql/core/R2dbcSqlServerIntegrationTests.java)
