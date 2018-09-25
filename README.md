[![Build
Status](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory.svg?branch=master)](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory)
## Cloud SQL Socket Factory for JDBC drivers

The Cloud SQL Socket Factory is a library for the MySQL/Postgres JDBC drivers that allows a user 
with the appropriate permissions to connect to a Cloud SQL database without having to deal with IP 
whitelisting or SSL certificates manually.

## Instructions

This library is available 

[Maven Central](http://search.maven.org/#artifactdetails%7Ccom.google.cloud.sql%7Cmysql-socket-factory%7C1.0.4%7Cjar).


### Authentication

This library uses the [Application Default Credentials](
https://developers.google.com/identity/protocols/application-default-credentials) to authenticate
the connection to the Cloud SQL server. For more details, see the previously mentioned link.

To activate credentials locally, use the following [gcloud](https://cloud.google.com/sdk/gcloud/) 
command: 
```bash
gcloud auth application-default login
```

### Add library as a dependency

#### MySQL

**Note**: Use the correct version to match your JDBC driver:

| JDBC Driver Version        | Cloud SQL Socket Factory Version         |
| -------------------------- | ---------------------------------------- |
| mysql-connector-java:8.x   | mysql-socket-factory-connector-j-8:1.0.11 |
| mysql-connector-java:6.x   | mysql-socket-factory-connector-j-6:1.0.11 |
| mysql-connector-java:5.1.x | mysql-socket-factory:1.0.11              |


##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory-connector-j-8</artifactId>
    <version>1.0.11</version>
</dependency>
```

#### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:mysql-socket-factory-connector-j-8:1.0.11'
```

#### PostgreSQL

##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.0.11</version>
</dependency>
```

#### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:postgres-socket-factory:1.0.11'
```


#### Creating the JDBC URL

##### MySQL

Base JDBC url: `jdbc:mysql://google/<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.mysql.SocketFactory |
| cloudSqlInstance | The instance connection name (found on the instance details page) |
| useSSL           | False |
| user             | MySQL username |
| password         | MySQL user's password |

The full JDBC url should look like this:
```
jdbc:mysql://google/<DATABASE_NAME>?cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.mysql.SocketFactory&useSSL=false&user=<MYSQL_USER_NAME>&password=<MYSQL_USER_PASSWORD>
```

##### Postgres

Base JDBC url: `jdbc:postgres://google/<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.postgres.SocketFactory |
| cloudSqlInstance | The instance connection name (found on the instance details page) |
| user             | Postgres username |
| password         | Postgres user's password |

The full JDBC url should look like this:
```
jdbc:postgresql://google/<DATABASE_NAME>?cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<POSTGRESQL_USER_NAME>&password=<POSTGRESQL_USER_PASSWORD>
```

## Additional Information

### Specifying IP Type
 
The `ipTypes` argument can be used to specify a comma delimited list of preferred IP types for
connecting to a Cloud SQL instance. The argument `ipTypes=PRIVATE` will force the 
SocketFactory to connect with an instance's associated private IP. Default value is 
`PUBLIC,PRIVATE`.

### Firewall configuration

Cloud SQL instance listens on port 3307 for incoming Cloud SQL proxy connections. This may require whitelisting on the side of the connecting application. If the connection is blocked by a firewall, create a rule to allow egress traffic from your application on TCP port 3307.

Typical symptoms of a firewall misconfiguration are as follows:

```
INFO  [main] c.z.h.HikariDataSource: HikariPool-1 - Starting...
INFO  [main] c.g.c.s.m.SocketFactory: Connecting to Cloud SQL instance [xxxx.yyyy.zzzz].
INFO  [main] c.g.c.s.c.SslSocketFactory: First Cloud SQL connection, generating RSA key pair.
INFO  [main] c.g.c.s.c.SslSocketFactory: Obtaining ephemeral certificate for Cloud SQL instance [xxxx.yyyy.zzzz].
INFO  [main] c.g.c.s.c.SslSocketFactory: Connecting to Cloud SQL instance [xxxx.yyyy.zzzz] on IP [aaa.bbb.ccc.ddd].
ERROR [main] c.z.h.p.HikariPool: HikariPool-1 - Exception during pool initialization.
com.mysql.jdbc.exceptions.jdbc4.CommunicationsException: Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago. The driver has not received any packets from the server.
```

### Connect with IntelliJ
 
In order to [connect IntelliJ](https://jetbrains.com/help/idea/connecting-to-a-database.html#mysql) 
to your Cloud SQL instance, you will need to add this library as a jar with dependencies in
"Additional Files" section on the driver settings page. Prebuilt fat jars can be found on the 
[Releases](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/releases) page for 
this purpose. 
 
### Connection via Unix Sockets

The library will automatically detect when it is running on GAE Standard, and will connect via the 
 provided unix socket for reduced latency.

To force the library to connect to a unix socket (typically created by the Cloud SQL proxy) when 
running outside of the GAE-Standard environment, set the environment variable 
`CLOUD_SQL_FORCE_UNIX_SOCKET` to any value.
