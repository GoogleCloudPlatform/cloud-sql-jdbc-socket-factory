
## Setup and Usage

### Adding the library as a dependency

##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.3.0</version>
</dependency>
```

##### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:postgres-socket-factory:1.3.0'
```
*Note*: Also include the JDBC Driver for PostgreSQL, `org.postgresql:postgresql:<LATEST-VERSION>`

### Creating theJDBC URL

Base JDBC URL: `jdbc:postgresql:///<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.postgres.SocketFactory |
| cloudSqlInstance | The instance connection name (found on the instance details page) |
| user             | PostgreSQL username |
| password         | PostgreSQL user's password |

The full JDBC URL should look like this:
```
jdbc:postgresql:///<DATABASE_NAME>?cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<POSTGRESQL_USER_NAME>&password=<POSTGRESQL_USER_PASSWORD>
```

Note: The host portion of the JDBC URL is currently unused, and has no effect on the connection process. The SocketFactory will get your instances IP address based on the provided `cloudSqlInstance` arg. 

### Specifying IP Types
 
"The `ipTypes` argument is used to specify a preferred order of IP types used to connect via a comma delimited list. For example, `ipTypes=PUBLIC,PRIVATE` will use the instance's Public IP if it exists, otherwise private. The value `ipTypes=PRIVATE` will force the Cloud SQL instance to connect via it's private IP. If not specified, the default used is `ipTypes=PUBLIC,PRIVATE`. 

For more info on connecting using a private IP address, see [Requirements for Private IP](https://cloud.google.com/sql/docs/mysql/private-ip#requirements_for_private_ip).

### Connection via Unix Sockets

To connect using a Unix domain socket (such as the one created by the Cloud SQL 
proxy), you can use the `unixSocketPath` property to specify a path to a local 
file instead of connecting directly over TCP.

```
jdbc:postgresql:///<DATABASE_NAME>?unixSocketPath=</PATH/TO/UNIX/SOCKET>&cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<POSTGRESQL_USER_NAME>&password=<POSTGRESQL_USER_PASSWORD>
```

## Examples

Examples for using the Cloud SQL JDBC Connector for Postgres can be found by looking at the integration tests in this repository.
* [Usage example](../jdbc/postgres/src/test/java/com/google/cloud/sql/postgres/JdbcPostgresIntegrationTests.java)

## Reference Documentation
* [Connecting to Cloud SQL from App Engine Standard](https://cloud.google.com/sql/docs/postgres/connect-app-engine-standard)
* [Connecting to Cloud SQL from App Engine Flexible](https://cloud.google.com/sql/docs/postgres/connect-app-engine-flexible)
* [Connecting to Cloud SQL from Cloud Functions](https://cloud.google.com/sql/docs/postgres/connect-functions)
* [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/postgres/connect-run)