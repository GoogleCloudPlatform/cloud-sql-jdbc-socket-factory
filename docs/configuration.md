# Connector Configuration Reference

The Cloud SQL Java Connector internally manages one or more Connectors. Most
applications don't need to configure these connectors. However, when an  
application needs advanced configuration or lifecycle management of the 
Cloud SQL Connector, then the application may need to configure multiple 
Cloud SQL Java Connectors. 

Each Cloud SQL Java Connector is responsible for establishing connections to the
application's Cloud SQL instances. A Connector holds a distinct Google Cloud IAM 
credentials. When an application configures more than one connector, it can
create connections to multiple Cloud SQL instances using distinct Google
Cloud credentials and IAM configuration.   

## Unnamed Connectors

Most applications use unnamed connectors configured using JDBC connection 
properties. When the application establishes a new JDBC connection, the 
Cloud SQL Java Connector will use an internal, unnamed connector configured with
the Google Cloud credentials that match the JDBC connection properties, creating
a new Connector if necessary. Then, the Java connector handles the lifecycle
of this unnamed connector without requiring intervention from the application.

## Named Connectors

The Cloud SQL Java Connector allows applications to configure named connectors.
The application indicates that a JDBC connection should use a named connector by
setting the `cloudSqlNamedConnector` JDBC connection property when creating a
JDBC connection. 

An application may need to use named connectors if:

- It needs to connect to the Cloud SQL Admin API using credentials 
  other than the Application Default Credentials.
- It needs to connect to multiple Cloud SQL instances using different
  credentials.
- It uses a non-standard Cloud SQL Admin API service URL.
- It needs to precisely control when connectors start and stop. 
- It needs to reset the entire connector configuration without restarting 
  the application.

### Registering and Using a Named Connector

The application calls `ConnectorRegistry.register()` to register the named
connector configuration.

```java
GoogleCredentials myCredentials = GoogleCredentials.create(authToken);

ConnectorConfig config = new ConnectorConfig.Builder()
  .withTargetPrincipal("example@project.iam.googleapis.com")
  .withDelegates(Arrays.asList("delegate@project.iam.googleapis.com"))
  .withGoogleCredentials(myCredentials)
  .build();

ConnectorRegistry.register("my-connector",config);
```

Then the application tells a database connection to use a named connector by 
adding the `cloudSqlNamedConnector` to the JDBC connection properties by adding
`cloudSqlNamedConnector` to the JDBC URL:

```java
String jdbcUrl = "jdbc:mysql:///<DATABASE_NAME>?"+
    +"cloudSqlInstance=project:region:instance"
    +"&cloudSqlNamedConnector=my-connector"
    +"&socketFactory=com.google.cloud.sql.mysql.SocketFactory"
    +"&user=<DB_USER>&password=<PASSWORD>";
```

Or by adding `cloudSqlNamedConnector` to the JDBC connection properties:

```java
// Set up URL parameters
Properties connProps = new Properties();

connProps.setProperty("user","<DB_USER>");
connProps.setProperty("password","<PASSWORD>");
connProps.setProperty("sslmode","disable");
connProps.setProperty("socketFactory","<DRIVER_CLASS>");
connProps.setProperty("cloudSqlInstance","project:region:instance");

connProps.setProperty("cloudSqlNamedConnector","my-connector");

// Initialize connection pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:mysql:///<DB_NAME>");
config.setDataSourceProperties(connProps);
config.setConnectionTimeout(10000); // 10s

HikariDataSource connectionPool = new HikariDataSource(config);
```

In R2DBC, the application sets the `NAMED_CONNECTOR` ConnectionFactory option
to the name of the named connector.

When using a named connector, the JDBC connection uses the connector 
configuration from the named connector. It ignores all connector configuration 
properties in the JDBC connection properties. See the full list of
[connector configuration properties](#connector-configuration) below.

The test [JdbcPostgresNamedConnectorIntegrationTest.java](connector-example)
contains a working example showing how create and use a named connector.

[connector-example]: ../jdbc/postgres/src/test/java/com/google/cloud/sql/postgres/JdbcPostgresNamedConnectorIntegrationTests.java

### Closing Named Connectors

The application closes a named connector by calling `ConnectorRegistry.close()`.
This stops the certificate refresh process for that connector. Subsequent 
attempts to connect using the named connector will fail. Existing open database 
connections will continue work until they are closed.

```java
ConnectorRegistry.close("my-connector");
```



### Updating a Named Connector's Configuration

The application may update the configuration of a named connector.

The application first calls `ConnectorRegistry.close()` and 
then `ConnectorRegistry.register()` with the new configuration. This creates a 
new connector with the new credentials.

Existing open database connections will continue work until they are closed 
using the old connector configuration. Subsequent attempts to connect using
the named connector will use the new configuration.

#### Example

First, register a named connector called "my-connector", and create
a database connection pool using the named connector.

```java

// Define the ConnectorConfig
GoogleCredentials c1 = GoogleCredentials.create(authToken);
ConnectorConfig config = new ConnectorConfig.Builder()
  .withTargetPrincipal("example@project.iam.googleapis.com")
  .withDelegates(Arrays.asList("delegate@project.iam.googleapis.com"))
  .withGoogleCredentials(c1)
  .build();

// Register it with the name "my-connector"
ConnectorRegistry.register("my-connector", config);
    
// Configure the datbase connection pool.
String jdbcUrl = "jdbc:mysql:///<DATABASE_NAME>?"+
    +"cloudSqlInstance=project:region:instance"
    +"&cloudSqlNamedConnector=my-connector"
    +"&socketFactory=com.google.cloud.sql.mysql.SocketFactory"
    +"&user=<DB_USER>&password=<PASSWORD>";

HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcURL);
config.setConnectionTimeout(10000); // 10s
HikariDataSource connectionPool = new HikariDataSource(config);
```

When the application needs to update the connector configuration, create
the updated ConnectorConfig. Then close the existing connector and register
a new connector with the same name.

```java
// Update the named connector configuration with new credentials.
GoogleCredentials c2 = GoogleCredentials.create(newAuthToken);
ConnectorConfig config2 = new ConnectorConfig.Builder()
    .withTargetPrincipal("application@project.iam.googleapis.com")
    .withGoogleCredentials(c2)
    .build();

// Replace the old connector named "my-connector" with a new connector
// using the new config.
ConnectorRegistry.close("my-connector");
ConnectorRegistry.register("my-connector", config2);
```

No updates to the database connection pool are required.
Existing open connections in the pool will continue to work until they are
closed. New connections will be established using the new configuration.

### Reset The Connector Registry

The application may shut down the ConnectorRegistry. This closes all existing
named and unnamed connectors, and stops internal background threads.

```java
ConnectorRegistry.reset();
```

After calling `ConnectorRegistry.reset()`, the next attempt to connect to a
database using a SocketFactory or R2DBC ConnectionFactory, or
to `ConnectorRegistry.register()` will start a new connector registry, restart
the background threads, and create a new connector.

### Shutdown The Connector Registry

The application may shut down the ConnectorRegistry. This closes all existing
named and unnamed connectors, and stops internal background threads.

```java
ConnectorRegistry.shutdown();
```

After calling `ConnectorRegistry.shutdown()`, subsequent attempts to connect to
a database using a SocketFactory or R2DBC ConnectionFactory, or
to `ConnectorRegistry.register()` will fail, throwing `IllegalStateException`.

## Configuring Google Credentials

By default, connectors will use the Google Application Default credentials to
connect to Google Cloud SQL Admin API. The application can set specific
Google Credentials in the connector configuration.

### Unnamed Connectors 
For unnamed connectors, the application can set the JDBC connection property
`cloudSqlGoogleCredentialsPath`. This should hold the path to a file containing
Google Credentials JSON. When the application first opens a database connection,
the connector will load the credentials will load from this file.

```java
// Set up URL parameters
String jdbcURL = String.format("jdbc:postgresql:///%s", DB_NAME);
Properties connProps = new Properties();

// Configure Postgres driver properties
connProps.setProperty("user", DB_USER);
connProps.setProperty("password", "password");
connProps.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory");

// Configure Cloud SQL connector properties
connProps.setProperty("cloudSqlInstance", CONNECTION_NAME);
connProps.setProperty("enableIamAuth", "true");

// Configure path to the credentials file
connProps.setProperty("cloudSqlGoogleCredentialsPath", "/var/secrets/application.json");

// Initialize connection pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcURL);
config.setDataSourceProperties(connProps);
config.setConnectionTimeout(10000); // 10s

HikariDataSource connectionPool = new HikariDataSource(config);
```

### Named Connectors

For named connectors configured registered by calling
`ConnectorRegistry.register()`, there are multiple ways to supply
a `GoogleCredentials` instance to the connector:

- `withGoogleCredentialsPath(String path)` - Configure the connector to load
  the credentials from the file.
- `withGoogleCredentialsSupplier(Supplier<GoogleCredentials> s)` - Configure the
  connector to load GoogleCredentials from the supplier.
- `withGoogleCredentials(GoogleCredentials c)` - Configure the connector with
  an instance of GoogleCredentials.

Users may only set exactly one of these fields. If more than one field is set,
`ConnectorConfig.Builder.build()` will throw an IllegalStateException.

The credentials are loaded exactly once when the ConnectorConfig is
registered with `ConnectorRegistry.register()`.

## Configuration Property Reference

### Connector Configuration Properties

These properties configure the connector which loads Cloud SQL instance 
configuration using the Cloud SQL Admin API. 

| JDBC Connection Property      | R2DBC Property Name     | Description                                                                                                                                                                                                                                         | Example                                                                                      |
|-------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| cloudSqlTargetPrincipal       | TARGET_PRINCIPAL        | The service account to impersonate when connecting to the database and database admin API.                                                                                                                                                          | `db-user@my-project.iam.gserviceaccount.com`                                                 |
| cloudSqlDelegates             | DELEGATES               | A comma-separated list of service accounts delegates. See [Delegated Service Account Impersonation](jdbc.md#delegated-service-account-impersonation)                                                                                                | `application@my-project.iam.gserviceaccount.com,services@my-project.iam.gserviceaccount.com` |
| cloudSqlGoogleCredentialsPath | GOOGLE_CREDENTIALS_PATH | A file path to a JSON file containing a GoogleCredentials oauth token.                                                                                                                                                                              | `/home/alice/secrets/my-credentials.json`                                                    |
| cloudSqlAdminRootUrl          | ADMIN_ROOT_URL          | An alternate root url for the Cloud SQL admin API. Must end in '/' See [rootUrl](java-api-root-url)                                                                                                                                                 | `https://googleapis.example.com/`                                                            |
| cloudSqlAdminServicePath      | ADMIN_SERVICE_PATH      | An alternate path to the SQL Admin API endpoint. Must not begin with '/'. Must end with '/'. See [servicePath](java-api-service-path)                                                                                                               | `sqladmin/v1beta1/`                                                                          |
| cloudSqlAdminQuotaProject     | ADMIN_QUOTA_PROJECT     | A project ID for quota and billing. See [Quota Project][quota-project]                                                                                                                                                                              | `my-project`                                                                                 |
| cloudSqlUniverseDomain        | UNIVERSE_DOMAIN         | A universe domain for the TPC environment (default is googleapis.com). See [TPC][tpc]                                                                                                                                                               | test-universe.test                                                                           |
| cloudSqlRefreshStrategy       | REFRESH_STRATEGY        | The strategy used to refresh the Google Cloud SQL authentication tokens. Valid values: `background` - refresh credentials using a background thread, `lazy` - refresh credentials during connection attempts.  [Refresh Strategy][refresh-strategy] | `lazy`                                                                                       |  

[java-api-root-url]: https://github.com/googleapis/google-api-java-client/blob/main/google-api-client/src/main/java/com/google/api/client/googleapis/services/AbstractGoogleClient.java#L49
[java-api-service-path]: https://github.com/googleapis/google-api-java-client/blob/main/google-api-client/src/main/java/com/google/api/client/googleapis/services/AbstractGoogleClient.java#L52
[quota-project]: jdbc.md#quota-project
[tpc]: jdbc.md#trusted-partner-cloud-tpc-support
[refresh-strategy]: jdbc.md#refresh-strategy

### Connection Configuration Properties

These properties configure the connection to a specific Cloud SQL instance.

| JDBC Property Name          | R2DBC Property Name | Description                                                                                                                                                                                                                                                                      | Default Value    | Example                           |
|-----------------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|-----------------------------------|
| cloudSqlInstance (required) | HOST                | The Cloud SQL Instance database server.                                                                                                                                                                                                                                          |                  | `projectname:region:instancename` |
| cloudSqlNamedConnector      | NAMED_CONNECTOR     | The name of the named connector created using `ConnectorRegistry.register()`                                                                                                                                                                                                     |                  | `my-configuration`                |
| unixSocketPath              | UNIX_SOCKET         | The path to the local unix socket created by the [Cloud SQL Auth Proxy](https://github.com/GoogleCloudPlatform/cloud-sql-proxy/). This is only valid when the connector is used together with the Cloud SQL Auth Proxy. Cannot be used with `enableIamAuth` or `ipTypes`.        |                  | `/var/db/my-db-instance`          |
| enableIamAuth               | ENABLE_IAM_AUTH     | Enable IAM Authentication to authenticate to the database. Valid values: `true` - authenticate with the IAM principal,  `false` - authenticate with a database user and password. Cannot be used with `unixSocketPath`.                                                          | false            | `true`                            |
| ipTypes                     | IP_TYPES            | A comma-separated list of IP types, ordered by preference. Value values: `PUBLIC` - connect to the instance's public IP, `PRIVATE` - connect to the instances private IP, `PSC` - connect to the instance through Private Service Connect. Cannot be used with `unixSocketPath`. | `PUBLIC,PRIVATE` | `PSC,PRIVATE,PUBLIC`              |

