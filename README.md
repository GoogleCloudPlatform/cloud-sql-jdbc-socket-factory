## Cloud SQL Connector for Java
[![Build
Status](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory.svg?branch=master)](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory)

The Cloud SQL Connector for Java is a library for the MySQL/Postgres JDBC and R2DBC drivers that allows a user 
with the appropriate permissions to connect to a Cloud SQL database without having to deal with IP 
whitelisting or SSL certificates manually.

## Instructions

### Examples

For examples of this library being used in the context of an application, check out the sample 
applications located 
[here](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/master/cloud-sql).

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

#### MySQL JDBC

**Note**: Use your JDBC driver version to figure out which SocketFactory you should use. If you 
are unsure, it is recommended to use the latest version of `mysql-connector-java:8.x`.

| JDBC Driver Version        | Cloud SQL Socket Factory Version         |
| -------------------------- | ---------------------------------------- |
| mysql-connector-java:8.x   | mysql-socket-factory-connector-j-8:1.0.16 |
| mysql-connector-java:6.x   | mysql-socket-factory-connector-j-6:1.0.16 |
| mysql-connector-java:5.1.x | mysql-socket-factory:1.0.16              |

[//]: # ({x-version-update-start:cloud-sql-java-connector:released})

##### Maven
Include the following in the project's `pom.xml`: 
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory-connector-j-8</artifactId>
    <version>1.1.0</version>
</dependency>
```

##### Gradle
Include the following the project's `build.gradle`
```gradle
compile 'com.google.cloud.sql:mysql-socket-factory-connector-j-8:1.1.0'
```
#### MySQL R2DBC

##### Maven
Include the following in the project's `pom.xml`: 
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-mysql</artifactId>
      <version>1.1.0</version>
    </dependency>
    <dependency>
      <groupId>dev.miku</groupId>
      <artifactId>r2dbc-mysql</artifactId>
      <version>0.8.2.RELEASE</version>
    </dependency>
```

##### Gradle
Include the following the project's `build.gradle`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-mysql:1.1.0'
compile 'dev.miku:r2dbc-mysql:0.8.2.RELEASE'
```

#### PostgreSQL JDBC

##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.1.0</version>
</dependency>
```

#### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:postgres-socket-factory:1.1.0'
```

#### PostgreSQL R2DBC
Include the following in the project's `pom.xml`: 
```maven-pom
    <dependency>
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-postgres</artifactId>
      <version>1.1.0</version>
    </dependency>
    <dependency>
      <groupId>io.r2dbc</groupId>
      <artifactId>r2dbc-postgresql</artifactId>
      <version>0.8.5.RELEASE</version>
    </dependency>
```

##### Gradle
Include the following the project's `build.gradle`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-postgres:1.1.0'
compile 'io.r2dbc:r2dbc-postgresql:0.8.5.RELEASE'
```

[//]: # ({x-version-update-end})

---

#### Creating the JDBC URL

##### MySQL

Base JDBC URL: `jdbc:mysql:///<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.mysql.SocketFactory |
| cloudSqlInstance | The instance connection name (found on the instance details page) |
| user             | MySQL username |
| password         | MySQL user's password |

The full JDBC URL should look like this:
```
jdbc:mysql:///<DATABASE_NAME>?cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.mysql.SocketFactory&user=<MYSQL_USER_NAME>&password=<MYSQL_USER_PASSWORD>
```

Note: The host portion of the JDBC URL is currently unused, and has no effect on the connection process. The SocketFactory will get your instances IP address base on the provided `cloudSqlInstance` arg. 

##### Postgres

Base JDBC URL: `jdbc:postgresql:///<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.postgres.SocketFactory |
| cloudSqlInstance | The instance connection name (found on the instance details page) |
| user             | Postgres username |
| password         | Postgres user's password |

The full JDBC URL should look like this:
```
jdbc:postgresql:///<DATABASE_NAME>?cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<POSTGRESQL_USER_NAME>&password=<POSTGRESQL_USER_PASSWORD>
```

Note: The host portion of the JDBC URL is currently unused, and has no effect on the connection process. The SocketFactory will get your instances IP address base on the provided `cloudSqlInstance` arg. 

---
#### Creating the R2DBC URL

##### MySQL
R2DBC URL template: `r2dbc:gcp:mysql//<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>`

Add the following parameters:

| Property         | Value         |
| ---------------- | ------------- |
| DATABASE_NAME   | The name of the database to connect to |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER         | MySQL username |
| DB_PASS         | MySQL user's password |


##### Postgres
R2DBC URL template: `r2dbc:gcp:postgres//<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>`

Add the following parameters:

| Property         | Value         |
| ---------------- | ------------- |
| DATABASE_NAME   | The name of the database to connect to |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER         | Postgres username |
| DB_PASS         | Postgres user's password |

---

## Building the Drivers
To build a fat JAR containing the JDBC driver with the bundles Socket Factory dependencies you can issue the following Maven command from the location containing the project pom.xml:

```mvn -P jar-with-dependencies clean package -DskipTests```

This will create a *target* sub-folder within each of the module directories. Within these target directories you'll find the JDBC driver files.

Example:
```
mysql-socket-factory-connector-j-8–1.0.16-jar-with-dependencies.jar
postgres-socket-factory-1.0.16-jar-with-dependencies.jar
```

---

## Additional Information

### Specifying IP Types
 
The `ipTypes` argument can be used to specify a comma delimited list of preferred IP types for
connecting to a Cloud SQL instance. The argument `ipTypes=PRIVATE` will force the 
SocketFactory to connect with an instance's associated private IP. Default value is 
`PUBLIC,PRIVATE`.

### Firewall configuration

The Cloud SQL proxy establishes connections to Cloud SQL instances using port 3307. Applications 
that are protected by a firewall may need to be configured to allow outgoing connections on TCP port
3307. A connection blocked by a firewall typically results in an error stating connection failure 
(e.g. `com.mysql.jdbc.exceptions.jdbc4.CommunicationsException: Communications link failure`).

### Connect with IntelliJ
 
In order to [connect IntelliJ](https://jetbrains.com/help/idea/connecting-to-a-database.html#mysql) 
to your Cloud SQL instance, you will need to add this library as a jar with dependencies in
"Additional Files" section on the driver settings page. Prebuilt fat jars can be found on the 
[Releases](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/releases) page for 
this purpose. 
 
### Connection via Unix Sockets

To connect using a Unix domain socket (such as the one created by the Cloud SQL 
proxy), you can use the `unixSocketPath` property to specify a path to a local 
file instead of connecting directly over TCP.

Example using MySQL:
```
jdbc:mysql:///<DATABASE_NAME>?unixSocketPath=</PATH/TO/UNIX/SOCKET>&cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.mysql.SocketFactory&user=<MYSQL_USER_NAME>&password=<MYSQL_USER_PASSWORD>
```

Example using PostgreSQL:
```
jdbc:postgresql:///<DATABASE_NAME>?unixSocketPath=</PATH/TO/UNIX/SOCKET>&cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<POSTGRESQL_USER_NAME>&password=<POSTGRESQL_USER_PASSWORD>
```
