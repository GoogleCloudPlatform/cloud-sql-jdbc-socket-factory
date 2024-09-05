# Connecting to Cloud SQL using JDBC

## Setup and Usage

### Adding the library as a dependency

Include the following in the project's `pom.xml` if your project uses Maven,
or in `build.gradle` if your project uses Gradle.

##### MySQL

<!-- {x-version-update-start:mysql-socket-factory-connector-j-8:released} -->
Maven

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory-connector-j-8</artifactId>
    <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:mysql-socket-factory-connector-j-8:1.20.1'
```

<!-- {x-version-update-end} -->

##### Maria DB

<!-- {x-version-update-start:mariadb-socket-factory:released} -->
Maven

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mariadb-socket-factory</artifactId>
    <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:mariadb-socket-factory:1.20.1'
```

**Note:** Also include the JDBC Driver for
MariaDB, `org.mariadb.jdbc:mariadb-java-client:<LATEST-VERSION>`
<!-- {x-version-update-end} -->

##### Postgres

<!-- {x-version-update-start:postgres-socket-factory:released} -->
Maven

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:postgres-socket-factory:1.20.1'
```

**Note:**  Also include the JDBC Driver for
PostgreSQL, `org.postgresql:postgresql:<LATEST-VERSION>`
<!-- {x-version-update-end} -->

##### SQL Server

<!-- {x-version-update-start:cloud-sql-connector-jdbc-sqlserver:released} -->
Maven

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>cloud-sql-connector-jdbc-sqlserver</artifactId>
    <version>1.20.1</version>
</dependency>
```

Gradle

```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-jdbc-sqlserver:1.20.1'
```

**Note:**  Also include the JDBC Driver for SQL
Server, `com.microsoft.sqlserver:mssql-jdbc:<LATEST-VERSION>`.
<!-- {x-version-update-end} -->

### Creating the JDBC URL

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value                                                             |
|------------------|-------------------------------------------------------------------|
| socketFactory    | <SOCKET_FACTORY_CLASS>                                            |
| cloudSqlInstance | The instance connection name (found on the instance details page) |
| user             | Database username                                                 |
| password         | Database user's password                                          |

Replace <SOCKET_FACTORY_CLASS> with the class name specific to your database.

#### MySQL

Base JDBC URL: `jdbc:mysql:///<DATABASE_NAME>`

SOCKET_FACTORY_CLASS: `com.google.cloud.sql.mysql.SocketFactory`

The full JDBC URL should look like this:

```java
String jdbcUrl = "jdbc:mysql:///<DATABASE_NAME>?" 
    + "cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&socketFactory=com.google.cloud.sql.mysql.SocketFactory" 
    + "&user=<MYSQL_USER_NAME>" 
    + "&password=<MYSQL_USER_PASSWORD>";
```

**Note:** The host portion of the JDBC URL is currently unused, and has no
effect on the connection process. The SocketFactory will get your instances IP
address based on the provided `cloudSqlInstance` arg.

#### Maria DB

Base JDBC URL: `jdbc:mariadb:///igoreme:123/<DATABASE_NAME>`

SOCKET_FACTORY_CLASS: `com.google.cloud.sql.mariadb.SocketFactory`

**Note:** You have to provide a hostname and port, but they are ignored.

**Note:** You can use `mysql` as the scheme if you set `permitMysqlScheme` on
the URL.
Please refer to the
MariaDB [documentation](https://mariadb.com/kb/en/about-mariadb-connector-j/#jdbcmysql-scheme-compatibility).

The full JDBC URL should look like this:

```java
String jdbcUrl = "jdbc:mariadb://ignoreme:1234/<DATABASE_NAME>?" 
    + "cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&socketFactory=com.google.cloud.sql.mariadb.SocketFactory" 
    + "&user=<MYSQL_USER_NAME>" 
    + "&password=<MYSQL_USER_PASSWORD>";
```

Note: The host portion of the JDBC URL is currently unused, and has no effect on
the connection process. The SocketFactory will get your instances IP address
based on the provided `cloudSqlInstance` arg.

#### Postgres

Base JDBC URL: `jdbc:postgresql:///<DATABASE_NAME>`

SOCKET_FACTORY_CLASS: `com.google.cloud.sql.postgres.SocketFactory`

When specifying the JDBC connection URL, add the additional parameters:

The full JDBC URL should look like this:

```java
String jdbcUrl = "jdbc:postgresql:///<DATABASE_NAME>?" 
    + "cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&socketFactory=com.google.cloud.sql.postgres.SocketFactory" 
    + "&user=<POSTGRESQL_USER_NAME>" 
    + "&password=<POSTGRESQL_USER_PASSWORD>";
```

**Note:** The host portion of the JDBC URL is currently unused, and has no
effect on the connection process. The SocketFactory will get your instances IP
address based on the provided `cloudSqlInstance` arg.

#### SQL Server

Base JDBC URL: `jdbc:sqlserver://localhost;databaseName=<DATABASE_NAME>`

SOCKET_FACTORY_CLASS: `com.google.cloud.sql.sqlserver.SocketFactory`

The full JDBC URL should look like this:

```java
String jdbcUrl = "jdbc:sqlserver://localhost;" 
    + "databaseName=<DATABASE_NAME>;" 
    + "socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory;" 
    + "socketFactoryConstructorArg=<INSTANCE_CONNECTION_NAME>;" 
    + "user=<USER_NAME>;" 
    + "password=<PASSWORD>";
```

**Note:** The host portion of the JDBC URL is currently unused, and has no
effect on the connection process. The SocketFactory will get your instances IP
address based on the provided `socketFactoryConstructorArg` arg.

### Specifying IP Types

The `ipTypes` argument is used to specify a preferred order of IP types used
to connect via a comma delimited list. For example, `ipTypes=PUBLIC,PRIVATE`
will use the instance's Public IP if it exists, otherwise private. The
value `ipTypes=PRIVATE` will force the Cloud SQL instance to connect via
it's private IP. The value `ipTypes=PSC` will force the Cloud SQL instance to
connect to the database
via [Private Service Connect](https://cloud.google.com/vpc/docs/private-service-connect).
If not specified, the connector will default to `ipTypes=PUBLIC,PRIVATE`.

For more info on connecting using a private IP address,
see [Requirements for Private IP](https://cloud.google.com/sql/docs/mysql/private-ip#requirements_for_private_ip).

#### MySQL

```java
String jdbcUrl = "jdbc:mysql:///<DATABASE_NAME>?" 
    + "cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&ipTypes=PRIVATE" 
    + "&socketFactory=com.google.cloud.sql.mysql.SocketFactory" 
    + "&user=<MYSQL_USER_NAME>&password=<MYSQL_USER_PASSWORD>";
```

#### Maria DB

```java
String jdbcUrl = "jdbc:mariadb://ignoreme:1234/<DATABASE_NAME>?" 
    + "cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&ipTypes=PRIVATE" 
    + "&socketFactory=com.google.cloud.sql.mariadb.SocketFactory" 
    + "&user=<MYSQL_USER_NAME>" 
    + "&password=<MYSQL_USER_PASSWORD>";
```

#### Postgres

```java
String jdbcUrl = "jdbc:postgresql:///<DATABASE_NAME>?" 
    + "cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&ipTypes=PRIVATE" 
    + "&socketFactory=com.google.cloud.sql.postgres.SocketFactory" 
    + "&user=<POSTGRESQL_USER_NAME>" 
    + "&password=<POSTGRESQL_USER_PASSWORD>";
```

#### SQL Server

IP types can be specified by appending the ipTypes argument
to `socketFactoryConstructorArg` using query syntax, such as:

```java
String jdbcUrl = "jdbc:sqlserver://localhost;" 
    + "databaseName=<DATABASE_NAME>;" 
    + "socketFactoryClass=com.google.cloud.sql.sqlserver.SocketFactory;" 
    + "socketFactoryConstructorArg=<INSTANCE_CONNECTION_NAME>?ipTypes=PRIVATE;" 
    + "user=<USER_NAME>;" 
    + "password=<PASSWORD>";
```

Or in java code:

```java
String jdbcURL = String.format("jdbc:sqlserver://localhost;databaseName=%s","<DATABASE_NAME>");
Properties connProps = new Properties();
connProps.setProperty("user","<USER_NAME>");
connProps.setProperty("password","<PASSWORD>");
connProps.setProperty("encrypt","false");
connProps.setProperty("socketFactory","com.google.cloud.sql.sqlserver.SocketFactory");

connProps.setProperty("socketFactoryConstructorArg",
"<INSTANCE_CONNECTION_NAME>?ipTypes=PRIVATE");

// Initialize connection pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcURL);
config.setDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
config.setDataSourceProperties(connProps);
config.setConnectionTimeout(10000); // 10s

HikariDataSource connectionPool = new HikariDataSource(config);
```

### IAM Authentication

**Note:** This feature is currently only supported for MySQL and Postgres
drivers.

Connections using
[IAM database authentication](https://cloud.google.com/sql/docs/postgres/iam-logins)
are supported when connecting to MySQL or Postgres instances.
This feature is unsupported for SQL Server. First, make sure to
[configure your Cloud SQL Instance to allow IAM authentication](https://cloud.google.com/sql/docs/postgres/create-edit-iam-instances#configure-iam-db-instance)
and
[add an IAM database user](https://cloud.google.com/sql/docs/postgres/create-manage-iam-users#creating-a-database-user).
Now, you can connect using user or service
account credentials instead of a password.
When setting up the connection, set the `enableIamAuth` connection property
to `true` and `user`
to the email address associated with your IAM user.

You must shorten the full IAM user email into a database username. Due to
different constraints on allowed characters in the database username, MySQL and
postgres differ in how they shorten an IAM email address into a database
username.

* MySQL: Truncate the IAM email removing the `@` and everything that follows.
* Postgres: If the IAM email ends with `.gserviceaccount.com`, remove
  the `.gserviceaccount.com` suffix from the email.

For example, if the full IAM user account is
`my-sa@my-project.iam.gserviceaccount.com`, then the shortened database username
would be `my-sa` for MySQL, and `my-sa@my-project.iam` for Postgres.

**Note:** a non-empty string value for the `password` property must be set.
While this property will be ignored when connecting with the Cloud SQL Connector
using IAM auth, leaving it empty will cause driver-level validations to fail.

#### Example

Replace these parameters in the example based on your database type:

| Database | JDBC_URL                              | DRIVER_CLASS                                | IAM_DB_USER          |
|----------|---------------------------------------|---------------------------------------------|----------------------|
| MySQL    | jdbc:mysql:///<DB_NAME>               | com.google.cloud.sql.mysql.SocketFactory    | my-sa                |  
| MariaDB  | jdbc:mariadb://ignoreme:123/<DB_NAME> | com.google.cloud.sql.mariadb.SocketFactory  | my-sa                |  
| Postgres | jdbc:postgresql:///<DB_NAME>          | com.google.cloud.sql.postgres.SocketFactory | my-sa@my-project.iam |


```java
// Set up URL parameters
Properties connProps = new Properties();
connProps.setProperty("user","<IAM_DB_USER>");
connProps.setProperty("sslmode","disable");
connProps.setProperty("socketFactory","<DRIVER_CLASS>");
connProps.setProperty("cloudSqlInstance","project:region:instance");
connProps.setProperty("enableIamAuth","true");

// Initialize connection pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl("<JDBC_URL>");
config.setDataSourceProperties(connProps);
config.setConnectionTimeout(10000); // 10s

HikariDataSource connectionPool = new HikariDataSource(config);
```

### Service Account Impersonation

**Note:** Only MySQL and Postgres support service account impersonation. SQL
Server does not support Service Account Impersonation.

The Java Connector supports service account impersonation with the
`cloudSqlTargetPrincipal` JDBC connection property. When enabled,
all API requests are made impersonating the supplied service account. The
IAM principal must have the iam.serviceAccounts.getAccessToken permission or
the role roles/iam.serviceAccounts.serviceAccountTokenCreator.

You must enable IAM Authentication to use service account impersonation.
Set the `cloudSqlTargetPrincipal` property to the full IAM email. Set 
the `user` property to the shortened IAM email following the rules described 
in [IAM Authentication](#iam-authentication)

#### Example 

Replace these parameters in the example based on your database type:

| Database | JDBC_URL                              | DRIVER_CLASS                                | IAM_DB_USER          | IAM_EMAIL                                |
|----------|---------------------------------------|---------------------------------------------|----------------------|------------------------------------------|
| MySQL    | jdbc:mysql:///<DB_NAME>               | com.google.cloud.sql.mysql.SocketFactory    | my-sa                | my-sa@my-project.iam.gserviceaccount.com |  
| MariaDB  | jdbc:mariadb://ignoreme:123/<DB_NAME> | com.google.cloud.sql.mariadb.SocketFactory  | my-sa                | my-sa@my-project.iam.gserviceaccount.com |  
| Postgres | jdbc:postgresql:///<DB_NAME>          | com.google.cloud.sql.postgres.SocketFactory | my-sa@my-project.iam | my-sa@my-project.iam.gserviceaccount.com |

**Note:** a non-empty string value for the `password` property must be set.
While this property will be ignored when connecting with the Cloud SQL Connector
using IAM auth, leaving it empty will cause driver-level validations to fail.

```java
// Set up URL parameters
Properties connProps = new Properties();
connProps.setProperty("user","<IAM_DB_USER>");
connProps.setProperty("sslmode","disable");
connProps.setProperty("socketFactory","<DRIVER_CLASS>");
connProps.setProperty("cloudSqlInstance","project:region:instance");
connProps.setProperty("enableIamAuth","true");
connProps.setProperty("cloudSqlTargetPrincipal","<IAM_EMAIL>");

// Initialize connection pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl("<JDBC_URL>");
config.setDataSourceProperties(connProps);
config.setConnectionTimeout(10000); // 10s

HikariDataSource connectionPool = new HikariDataSource(config);
```

#### Delegated Service Account Impersonation

In addition, the `cloudSqlDelegates` property controls impersonation delegation.
The value is a comma-separated list of service accounts containing chained
list of delegates required to grant the final access_token. If set,
the sequence of identities must have "Service Account Token Creator" capability
granted to the preceding identity. For example, if set to
`"serviceAccountB,serviceAccountC"`, the application default credentials must
have the Token Creator role on serviceAccountB. serviceAccountB must have
the Token Creator on serviceAccountC. Finally, C must have Token Creator on
`cloudSqlTargetPrincipal`. If unset, the application default credential
principal
must "Service Account Token Creator" capability granted that role on the
cloudSqlTargetPrincipal service account.

```java
connProps.setProperty("cloudSqlTargetPrincipal","TARGET_SERVICE_ACCOUNT");
connProps.setProperty("cloudSqlDelegates","SERVICE_ACCOUNT_1,SERVICE_ACCOUNT_2");
```

In this example, the environment's application default principal impersonates
SERVICE_ACCOUNT_1 which impersonates SERVICE_ACCOUNT_2 which then
impersonates the TARGET_SERVICE_ACCOUNT.

### Connection via Unix Sockets

To connect using a Unix domain socket (such as the one created by the Cloud SQL
proxy), you can use the `unixSocketPath` property to specify a path to a local
file instead of connecting directly over TCP.

##### MySQL

```java 
String jdbcUrl = "jdbc:mysql:///<DATABASE_NAME>?" 
    + "unixSocketPath=</PATH/TO/UNIX/SOCKET>" 
    + "&cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&socketFactory=com.google.cloud.sql.mysql.SocketFactory" 
    + "&user=<MYSQL_USER_NAME>"
    + "&password=<MYSQL_USER_PASSWORD>";
```

##### Maria DB

Not Supported.

##### Postgres

```java
String jdbcUrl = "jdbc:postgresql:///<DATABASE_NAME>" 
    + "?unixSocketPath=</PATH/TO/UNIX/SOCKET>" 
    + "&cloudSqlInstance=<INSTANCE_CONNECTION_NAME>" 
    + "&socketFactory=com.google.cloud.sql.postgres.SocketFactory" 
    + "&user=<POSTGRESQL_USER_NAME>&password=<POSTGRESQL_USER_PASSWORD>"
```

##### SQL Server

Not Supported.

## SQL Admin API

### 

The Java Connector supports setting the SQL Admin API URL with the 
`cloudSqlAdminRootUrl` and `cloudSqlAdminServicePath` JDBC connection 
properties. This feature is used by applications that need to connect 
to a Google Cloud API other than the GCP public API.

The `cloudSqlAdminRootUrl` property specifies the URL-encoded root URL of the 
service, for example `"https://googleapis.example.com/"`. If the specified 
root URL does not end with a "/" then a "/" is added to the end.

The `cloudSqlAdminServicePath` property specifies the URL-encoded service path 
of the service, for example `"sqladmin/"`. It is allowed to be an empty 
string "" or a forward slash "/", if it is a forward slash then it is treated 
as an empty string. If the specified service path does not end with a "/" then 
a "/" is added to the end. If the specified service path begins with a "/" 
then the "/" is removed.

If these options are not set, the connector will use the public Google Cloud 
API as follows:

```
DEFAULT_ROOT_URL = "https://sqladmin.googleapis.com/"
DEFAULT_SERVICE_PATH = ""
```

For more information, see the [underlying driver class documentation](https://cloud.google.com/java/docs/reference/google-api-client/latest/com.google.api.client.googleapis.services.AbstractGoogleClient.Builder#com_google_api_client_googleapis_services_AbstractGoogleClient_Builder_setRootUrl_java_lang_String_).

#### Example

Replace these parameters in the example based on your database type:

| Database | JDBC_URL                              | DRIVER_CLASS                                |
|----------|---------------------------------------|---------------------------------------------|
| MySQL    | jdbc:mysql:///<DB_NAME>               | com.google.cloud.sql.mysql.SocketFactory    | 
| MariaDB  | jdbc:mariadb://ignoreme:123/<DB_NAME> | com.google.cloud.sql.mariadb.SocketFactory  |
| Postgres | jdbc:postgresql:///<DB_NAME>          | com.google.cloud.sql.postgres.SocketFactory |


```java
// Set up URL parameters
Properties connProps = new Properties();
connProps.setProperty("user","<IAM_DB_USER>");
connProps.setProperty("sslmode","disable");
connProps.setProperty("socketFactory","<DRIVER_CLASS>");
connProps.setProperty("cloudSqlInstance","project:region:instance");
connProps.setProperty("cloudSqlAdminRootUrl","https://googleapis.example.com/");
connProps.setProperty("cloudSqlAdminServicePath","sqladmin/");

// Initialize connection pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl("<JDBC_URL>");
config.setDataSourceProperties(connProps);
config.setConnectionTimeout(10000); // 10s

HikariDataSource connectionPool = new HikariDataSource(config);
```

### Quota project

The Java Connector supports setting the project ID for quota and billing
with the `cloudSqlAdminQuotaProject` property. If not specified, defaults to the
project sourced from environment.

For more information, see the [documentation][quota-project-doc].

[quota-project-doc]: https://cloud.google.com/docs/quota/set-quota-project

#### Example

```java
Properties connProps = new Properties();
connProps.setProperty("cloudSqlAdminQuotaProject", "PROJECT_NAME");
```

### Trusted Partner Cloud (TPC) support

The Java Connector supports setting the universe domain for the TPC environment
with the `cloudSqlUniverseDomain` property. If not specified, defaults to the
Google Default Universe (GDU): googleapis.com.

#### Example

```java
Properties connProps = new Properties();
connProps.setProperty("cloudSqlUniverseDomain", "test-universe.test");
```


### Refresh Strategy for Serverless Compute 

When the connector runs in Cloud Run, App Engine Standard, or Cloud Functions, 
the connector should be configured to use the `lazy` refresh
strategy instead of the default `background` strategy.

Cloud Run, App Engine Standard, and Cloud Functions throttle application CPU
in a way that interferes with the default `background` strategy used to refresh 
the client certificate and authentication token.

#### Example

```java
Properties connProps = new Properties();
connProps.setProperty("cloudSqlRefreshStrategy", "lazy");
```

## Configuration Reference

- See [Configuration Reference](configuration.md)

## Examples

Examples for using the Cloud SQL JDBC Connector for SQL Server can be found by
looking at the integration tests in this repository.

* [MySQL connector-j-8](../jdbc/mysql-j-8/src/test/java/com/google/cloud/sql/mysql/JdbcMysqlJ8IntegrationTests.java)
* [Maria DB Usage example](../jdbc/mariadb/src/test/java/com/google/cloud/sql/mariadb/JdbcMariaDBIntegrationTests.java)
* [Postgres Usage example](../jdbc/postgres/src/test/java/com/google/cloud/sql/postgres/JdbcPostgresIntegrationTests.java)
* [SQL Server Usage example](../jdbc/sqlserver/src/test/java/com/google/cloud/sql/sqlserver/JdbcSqlServerIntegrationTests.java)

## Reference Documentation

### MySQL & Maria DB

* [Connecting to Cloud SQL from App Engine Standard](https://cloud.google.com/sql/docs/mysql/connect-app-engine-standard)
* [Connecting to Cloud SQL from App Engine Flexible](https://cloud.google.com/sql/docs/mysql/connect-app-engine-flexible)
* [Connecting to Cloud SQL from Cloud Functions](https://cloud.google.com/sql/docs/mysql/connect-functions)
* [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/mysql/connect-run)

### Postgres

* [Connecting to Cloud SQL from App Engine Standard](https://cloud.google.com/sql/docs/postgres/connect-app-engine-standard)
* [Connecting to Cloud SQL from App Engine Flexible](https://cloud.google.com/sql/docs/postgres/connect-app-engine-flexible)
* [Connecting to Cloud SQL from Cloud Functions](https://cloud.google.com/sql/docs/postgres/connect-functions)
* [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/postgres/connect-run)

### SQL Server

* [Connecting to Cloud SQL from App Engine Standard](https://cloud.google.com/sql/docs/sqlserver/connect-app-engine-standard)
* [Connecting to Cloud SQL from App Engine Flexible](https://cloud.google.com/sql/docs/sqlserver/connect-app-engine-flexible)
* [Connecting to Cloud SQL from Cloud Functions](https://cloud.google.com/sql/docs/sqlserver/connect-functions)
* [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/sqlserver/connect-run)
