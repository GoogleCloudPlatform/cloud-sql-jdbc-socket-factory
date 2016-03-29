[![Build
Status](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-mysql-socket-factory.svg?branch=master)](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-mysql-socket-factory)
## Cloud SQL MySQL Socket Factory

The Cloud SQL MySQL Socket Factory is a socket factory for the MySQL JDBC driver 
that allows a user with the appropriate permissions to connect to a Cloud SQL 
database without having to deal with IP whitelisting or SSL certificates 
manually. 

## Obtaining

The library is [available in Maven Central](http://search.maven.org/#artifactdetails%7Ccom.google.cloud.sql%7Cmysql-socket-factory%7C1.0.0-beta1%7Cjar).

Add a dependency using your favorite build tool. Maven and Gradle examples are shown below.

### Maven

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory</artifactId>
    <version>1.0.0-beta1</version>
</dependency>
```

### Gradle

```gradle
compile 'com.google.cloud.sql:mysql-socket-factory:1.0.0-beta1'
```

## Using

When specifying the JDBC connection URL, add two additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| cloudSqlInstance | The instance connection name (which is found on the instance details page in Google Developers Console)  |
| socketFactory    | com.google.cloud.sql.mysql.SocketFactory |

For example, if the instance connection name is `foo:bar:baz`, the JDBC URL 
would be 
`jdbc:mysql://google/mydb?cloudSqlInstance=foo:bar:baz&socketFactory=com.google.cloud.sql.mysql.SocketFactory`

