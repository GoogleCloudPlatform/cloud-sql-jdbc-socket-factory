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
| mysql-connector-java:8.x   | mysql-socket-factory-connector-j8:1.0.10 |
| mysql-connector-java:6.x   | mysql-socket-factory-connector-j6:1.0.10 |
| mysql-connector-java:5.1.x | mysql-socket-factory:1.0.10              |


##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory-connector-j8</artifactId>
    <version>1.0.10</version>
</dependency>
```

#### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:mysql-socket-factory-connector-j8:1.0.10'
```

#### PostgreSQL

##### Maven
Include the following in the project's `pom.xml`:
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.0.10</version>
</dependency>
```

#### Gradle
Include the following the project's `gradle.build`
```gradle
compile 'com.google.cloud.sql:postgres-socket-factory:1.0.10'
```


#### Creating the JDBC URL

##### MySQL

Base JDBC url: `jdbc:mysql://google/<DATABASE_NAME>`

When specifying the JDBC connection URL, add the additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.postgres.SocketFactory |
| cloudSqlInstance | The instance connection name (found on the instance details) |
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
| socketFactoryArg | The instance connection name (which is found on the instance details page in Google Developers Console)  |
| user             | Postgres username |
| password         | Postgres user's password |

The full JDBC url should look like this:
```
jdbc:postgresql://google/<DATABASE_NAME>?useSSL=false&socketFactoryArg=<INSTANCE_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=<POSTGRESQL_USER_NAME>&password=<POSTGRESQL_USER_PASSWORD>
```

## Additional Information

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
