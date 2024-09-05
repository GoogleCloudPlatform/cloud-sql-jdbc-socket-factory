# Connecting to Cloud SQL using R2DBC

## Setup and Usage

### Adding the library as a dependency


#### Maven

Include the following in the project's `pom.xml` if your project uses Maven,
or in `build.gradle` if your project uses Gradle.

##### MySQL
<!-- {x-version-update-start:cloud-sql-connector-r2dbc-mysql:released} -->
Maven

```maven-pom
<dependency>
  <groupId>com.google.cloud.sql</groupId>
  <artifactId>cloud-sql-connector-r2dbc-mysql</artifactId>
  <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-mysql:1.20.1'
```

**Note:** Also include the R2DBC Driver for
MySQL, `io.asyncer:r2dbc-mysql:<LATEST-VERSION>`
<!-- {x-version-update-end} -->

##### MariaDB
<!-- {x-version-update-start:cloud-sql-connector-r2dbc-mariadb:released} -->
Maven

```maven-pom
<dependency>
  <groupId>com.google.cloud</groupId>
  <artifactId>cloud-sql-connector-r2dbc-mariadb</artifactId>
  <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud:cloud-sql-connector-r2dbc-mariadb:1.20.1'
```

**Note:** Also include the R2DBC Driver for
MariaDB, `org.mariadb:r2dbc-mariadb:<LATEST-VERSION>`
<!-- {x-release-please-end} -->

##### Postgres

<!-- {x-version-update-start:cloud-sql-connector-r2dbc-postgres:released} -->
Maven

```maven-pom
<dependency>
  <groupId>com.google.cloud.sql</groupId>
  <artifactId>cloud-sql-connector-r2dbc-postgres</artifactId>
  <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-postgres:1.20.1'
```

**Note:** Also include the R2DBC Driver for
PostgreSQL, `io.r2dbc:r2dbc-postgresql:<LATEST-VERSION>`
<!-- {x-version-update-end} -->

##### SQL Server

<!-- {x-version-update-start:cloud-sql-connector-r2dbc-sqlserver:released} -->
Maven

```maven-pom
<dependency>
  <groupId>com.google.cloud.sql</groupId>
  <artifactId>cloud-sql-connector-r2dbc-sqlserver</artifactId>
  <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-sqlserver:1.20.1'
```

**Note:** Also include the R2DBC Driver for SQL
Server, `io.r2dbc:r2dbc-mssql:<LATEST-VERSION>`
<!-- {x-version-update-end} -->

### Creating the R2DBC URL

Add the following connection properties:

| Property                  | Value                                                             |
|---------------------------|-------------------------------------------------------------------|
| DATABASE_NAME             | The name of the database to connect to                            |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER                   | Database username                                                 |
| DB_PASS                   | Database user's password                                          |

R2DBC URL template:

#### MySQL

```
r2dbc:gcp:mysql://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>
```

#### MariaDB

```
r2dbc:gcp:mariadb://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>
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
    .option(DRIVER,"gcp")
    .option(PROTOCOL,"mysql") // OR "postgres" or "mssql" or "mariadb"
    .option(PASSWORD,"<DB_PASSWORD>")
    .option(USER,"<DB_USER>")
    .option(DATABASE,"<DATABASE_NAME}")
    .option(HOST,"<CLOUD_SQL_CONNECTION_NAME>")
    .build();

ConnectionFactory connectionFactory = ConnectionFactories.get(options);
ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

ConnectionPool connectionPool = new ConnectionPool(configuration);
```

### IAM Authentication

**Note:** This feature is currently only supported for MySQL, MariaDB and Postgres
drivers.

Connections using
[IAM database authentication](https://cloud.google.com/sql/docs/postgres/iam-logins)
are supported when connecting to MySQL, MariaDB or Postgres instances.
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
different constraints on allowed characters in the database username, MySQL and
postgres differ in how they shorten an IAM email address into a database
username.

* MySQL and MariaDB: Truncate the IAM email removing the `@` and everything that follows.
* Postgres: If the IAM email ends with `.gserviceaccount.com`, remove
  the `.gserviceaccount.com` suffix from the email.

For example, if the full IAM user account is 
`my-sa@my-project.iam.gserviceaccount.com`, then the shortened database username
would be `my-sa` for MySQL and MariaDB, and `my-sa@my-project.iam` for Postgres. 

**Note:** a non-empty string value for the `password` property must be set.
While this property will be ignored when connecting with the Cloud SQL Connector
using IAM auth, leaving it empty will cause driver-level validations to fail.

#### Example

Replace these parameters in the example based on your database type:

| Database | PROTOCOL   | IAM_DB_USER          |
|----------|------------|----------------------|
| MySQL    | mysql      | my-sa                |  
| MariaDB  | mariadb    | my-sa                |
| Postgres | postgresql | my-sa@my-project.iam |

```java
// Set up ConnectionFactoryOptions
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(DRIVER,"gcp")
    .option(PROTOCOL,"<PROTOCOL>")
    .option(PASSWORD,"password")
    .option(USER,"<IAM_DB_USER>")
    .option(DATABASE,"my_db")
    .option(HOST,"project:region:instance")
    .option(ENABLE_IAM_AUTH,true)
    .build();

// Initialize connection pool
ConnectionFactory connectionFactory = ConnectionFactories.get(options);
ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

ConnectionPool connectionPool = new ConnectionPool(configuration);
```

## Service Account Impersonation

**Note:** Only MySQL and Postgres support service account impersonation. SQL 
Server does not support Service Account Impersonation.

The Java Connector supports service account impersonation with the
`TARGET_PRINCIPAL` option. When enabled, all API requests are made impersonating
the supplied service account. The IAM principal must have the
iam.serviceAccounts.getAccessToken permission or the role
roles/iam.serviceAccounts.serviceAccountTokenCreator.

You must enable IAM Authentication to use service account impersonation.
Set the `TARGET_PRINCIPAL` property to the full IAM email. Set
the `USER` option to the shortened IAM email following the rules described
in [IAM Authentication](#iam-authentication)

#### Example

Replace these parameters in the example based on your database type:

| Database | PROTOCOL   | IAM_DB_USER          | IAM_EMAIL                                |
|----------|------------|----------------------|------------------------------------------|
| MySQL    | mysql      | my-sa                | my-sa@my-project.iam.gserviceaccount.com |
| MariaDB  | mariadb    | my-sa                | my-sa@my-project.iam.gserviceaccount.com |  
| Postgres | postgresql | my-sa@my-project.iam | my-sa@my-project.iam.gserviceaccount.com |

**Note:** a non-empty string value for the `password` property must be set.
While this property will be ignored when connecting with the Cloud SQL Connector
using IAM auth, leaving it empty will cause driver-level validations to fail.

```java
// Set up ConnectionFactoryOptions
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
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
ConnectionFactory connectionFactory = ConnectionFactories.get(options);
ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

ConnectionPool connectionPool = new ConnectionPool(configuration);
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
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(TARGET_PRINCIPAL,"TARGET_SERVICE_ACCOUNT");
    .option(DELEGATES,"SERVICE_ACCOUNT_1,SERVICE_ACCOUNT_2")
    // ...more connection options
    .build;
```

The environment's application default principal impersonates
SERVICE_ACCOUNT_1 which impersonates SERVICE_ACCOUNT_2 which then
impersonates the TARGET_SERVICE_ACCOUNT.

## SQL Admin API

### Root URL & Service Path

The Java Connector supports setting the SQL Admin API URL with the 
`ADMIN_ROOT_URL` and `ADMIN_SERVICE_PATH` options. This feature is used 
by applications that need to connect to a Google Cloud API other than 
the GCP public API.

The `ADMIN_ROOT_URL` option specifies the URL-encoded root URL of the 
service, for example `"https://googleapis.example.com/"`. If the specified 
root URL does not end with a "/" then a "/" is added to the end.

The `ADMIN_SERVICE_PATH` option specifies the URL-encoded service path of the 
service, for example `"sqladmin/"`. It is allowed to be an empty string "" 
or a forward slash "/", if it is a forward slash then it is treated as an 
empty string. If the specified service path does not end with a "/" then a 
"/" is added to the end. If the specified service path begins with a "/" then 
the "/" is removed.

If these options are not set, the connector will use the public Google Cloud 
API as follows:

```
DEFAULT_ROOT_URL = "https://sqladmin.googleapis.com/"
DEFAULT_SERVICE_PATH = ""
```

For more information, see the [underlying driver class documentation](https://cloud.google.com/java/docs/reference/google-api-client/latest/com.google.api.client.googleapis.services.AbstractGoogleClient.Builder#com_google_api_client_googleapis_services_AbstractGoogleClient_Builder_setRootUrl_java_lang_String_).

#### Example

```java
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(ADMIN_ROOT_URL, "https://googleapis.example.com/");
    .option(ADMIN_SERVICE_PATH, "sqladmin/")
    // ...more connection options
    .build;
```

### Quota project

The Java Connector supports setting the project ID for quota and billing
with the `ADMIN_QUOTA_PROJECT` option. If not specified, defaults to the
project sourced from environment.

For more information, see the [documentation][quota-project-doc].

[quota-project-doc]: https://cloud.google.com/docs/quota/set-quota-project

#### Example

```java
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(ADMIN_QUOTA_PROJECT, "PROJECT_NAME")
    .build;
```

## Configuration Reference

- See [Configuration Reference](configuration.md)

## Examples

Examples for the Cloud SQL R2DBC Connector can be found by looking at the
integration tests in this repository.

* [MariaDB usage example](../r2dbc/mariadb/src/test/java/com/google/cloud/sql/core/R2dbcMariadbIntegrationTests.java)
* [MySQL usage example](../r2dbc/mysql/src/test/java/com/google/cloud/sql/core/R2dbcMysqlIntegrationTests.java)
* [Postgres usage example](../r2dbc/postgres/src/test/java/com/google/cloud/sql/core/R2dbcPostgresIntegrationTests.java)
* [SQL Sql usage example](../r2dbc/sqlserver/src/test/java/com/google/cloud/sql/core/R2dbcSqlserverIntegrationTests.java)
