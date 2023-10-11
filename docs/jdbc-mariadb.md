
## Setup and Usage

### Adding the library as a dependency

<!-- {x-release-please-start-version} -->
##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mariadb-socket-factory</artifactId>
    <version>1.14.1</version>
</dependency>
```

##### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:mariadb-socket-factory:1.14.1'
```
*Note*: Also include the JDBC Driver for MariaDB, `org.mariadb.jdbc:mariadb-java-client:<LATEST-VERSION>`
<!-- {x-release-please-end} -->

### Creating the JDBC URL

Base JDBC URL: `jdbc:mariadb:///igoreme:123/<DATABASE_NAME>`

**Note**: You have to provide a hostname and port but they are ignored.

**Note**: You can use `mysql` as the scheme if you set `permitMysqlScheme` on the URL.
Please refer to the MariaDB [documentation](https://mariadb.com/kb/en/about-mariadb-connector-j/#jdbcmysql-scheme-compatibility).

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value                                                          |
| ---------------- |----------------------------------------------------------------|
| socketFactory    | com.google.cloud.sql.mariadb.SocketFactory                     |
| cloudSqlInstance | The instance connection name (found on the instance details page) |
| user             | MariaDB username                                               |
| password         | MariaDB user's password                                        |

The full JDBC URL should look like this:
```
jdbc:mariadb://ignoreme:1234/<DATABASE_NAME>?cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.mariadb.SocketFactory&user=<MYSQL_USER_NAME>&password=<MYSQL_USER_PASSWORD>
```

Note: The host portion of the JDBC URL is currently unused, and has no effect on the connection process. The SocketFactory will get your instances IP address based on the provided `cloudSqlInstance` arg.

### Specifying IP Types

"The `ipTypes` argument is used to specify a preferred order of IP types used 
to connect via a comma delimited list. For example, `ipTypes=PUBLIC,PRIVATE` 
will use the instance's Public IP if it exists, otherwise private. The 
value `ipTypes=PRIVATE` will force the Cloud SQL instance to connect via 
it's private IP. The value `ipTypes=PSC` will force the Cloud SQL instance to 
connect to the database via [Private Service Connect](https://cloud.google.com/vpc/docs/private-service-connect). 
If not specified, the default used is `ipTypes=PUBLIC,PRIVATE`.

For more info on connecting using a private IP address, see [Requirements for Private IP](https://cloud.google.com/sql/docs/mysql/private-ip#requirements_for_private_ip).

### IAM Authentication
*Note:* This feature is currently only supported for MySQL j8 (+ MariaDB) and Postgres drivers.
Connections using
[IAM database authentication](https://cloud.google.com/sql/docs/mysql/iam-logins)
are supported when connecting to MySQL instances.
This feature is unsupported for other drivers. First, make sure to
[configure your Cloud SQL Instance to allow IAM authentication](https://cloud.google.com/sql/docs/mysql/create-edit-iam-instances#configure-iam-db-instance)
and
[add an IAM database user](https://cloud.google.com/sql/docs/mysql/create-manage-iam-users#creating-a-database-user).
Now, you can connect using user or service
account credentials instead of a password.
When setting up the connection, set the `enableIamAuth` connection property to `"true"` and `user`
to the email address associated with your IAM user.

Example:
```java
    // Set up URL parameters
    String jdbcURL = String.format("jdbc:mariadb://ignoreme:123/%s", DB_NAME);
    Properties connProps = new Properties();
    connProps.setProperty("user", "mysql-iam-user@gmail.com");
    connProps.setProperty("password", "password");
    connProps.setProperty("sslmode", "disable");
    connProps.setProperty("socketFactory", "com.google.cloud.sql.mariadb.SocketFactory");
    connProps.setProperty("cloudSqlInstance", "project:region:instance");
    connProps.setProperty("enableIamAuth", "true");

    // Initialize connection pool
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcURL);
    config.setDataSourceProperties(connProps);
    config.setConnectionTimeout(10000); // 10s

    HikariDataSource connectionPool = new HikariDataSource(config);
```

Note: a non-empty string value for the `password` property must be set. While this property will
be ignored when connecting with the Cloud SQL Connector using IAM auth, leaving it empty will cause
driver-level validations to fail.

### Service Account Impersonation

The Java Connector supports service account impersonation with the
`cloudSqlTargetPrincipal` JDBC connection property. When enabled,
all API requests are made impersonating the supplied service account. The
IAM principal must have the iam.serviceAccounts.getAccessToken permission or
the role roles/iam.serviceAccounts.serviceAccountTokenCreator.

Example:
```java
// Set up URL parameters
String jdbcURL = String.format("jdbc:mariadb://ignoreme:123/%s", DB_NAME);
Properties connProps = new Properties();
connProps.setProperty("user", "mysql-iam-user@gmail.com");
connProps.setProperty("password", "password");
connProps.setProperty("sslmode", "disable");
connProps.setProperty("socketFactory", "com.google.cloud.sql.mariadb.SocketFactory");
connProps.setProperty("cloudSqlInstance", "project:region:instance");
connProps.setProperty("enableIamAuth", "true");
connProps.setProperty("cloudSqlTargetPrincipal", "mysql-iam-user@gmail.com");

// Initialize connection pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcURL);
config.setDataSourceProperties(connProps);
config.setConnectionTimeout(10000); // 10s

HikariDataSource connectionPool = new HikariDataSource(config);
```

In addition, the `cloudSqlDelegates` property controls impersonation delegation. 
The value is a comma-separated list of service accounts containing chained 
list of delegates required to grant the final access_token. If set,
the sequence of identities must have "Service Account Token Creator" capability
granted to the preceding identity. For example, if set to 
`"serviceAccountB,serviceAccountC"`, the application default credentials must 
have the Token Creator role on serviceAccountB. serviceAccountB must have
the Token Creator on serviceAccountC. Finally, C must have Token Creator on 
`cloudSqlTargetPrincipal`. If unset, the application default credential principal
must "Service Account Token Creator" capability granted that role on the
targetPrincipal service account.


For example:
```java
connProps.setProperty("cloudSqlTargetPrincipal", "TARGET_SERVICE_ACCOUNT");
connProps.setProperty("cloudSqlDelegates", "SERVICE_ACCOUNT_1,SERVICE_ACCOUNT_2");
```

In this example, the environment's application default principal impersonates
SERVICE_ACCOUNT_1 which impersonates SERVICE_ACCOUNT_2 which then
impersonates the TARGET_SERVICE_ACCOUNT.

## Examples

Examples for using the Cloud SQL JDBC Connector for MySQL/MariaDB can be found by looking at the integration tests in this repository.
* [Usage example](../jdbc/mariadb/src/test/java/com/google/cloud/sql/mariadb/JdbcMariaDBIntegrationTests.java)

## Reference Documentation
* [Connecting to Cloud SQL from App Engine Standard](https://cloud.google.com/sql/docs/mysql/connect-app-engine-standard)
* [Connecting to Cloud SQL from App Engine Flexible](https://cloud.google.com/sql/docs/mysql/connect-app-engine-flexible)
* [Connecting to Cloud SQL from Cloud Functions](https://cloud.google.com/sql/docs/mysql/connect-functions)
* [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/mysql/connect-run)
