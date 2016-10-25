[![Build
Status](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-mysql-socket-factory.svg?branch=master)](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-mysql-socket-factory)
## Cloud SQL MySQL Socket Factory

The Cloud SQL MySQL Socket Factory is a socket factory for the MySQL JDBC driver 
that allows a user with the appropriate permissions to connect to a Cloud SQL 
database without having to deal with IP whitelisting or SSL certificates 
manually. 

## Obtaining

The library is [available in Maven Central](http://search.maven.org/#artifactdetails%7Ccom.google.cloud.sql%7Cmysql-socket-factory%7C1.0.2%7Cjar).

Add a dependency using your favorite build tool. Maven and Gradle examples are shown below.

### Maven

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory</artifactId>
    <version>1.0.2</version>
</dependency>
```

### Gradle

```gradle
compile 'com.google.cloud.sql:mysql-socket-factory:1.0.2'
```

*Note*: If you wish to use the 6.x (development) version of the MySQL driver, use the artifact id
'mysql-socket-factory-connector-j-6'.

## Using

When specifying the JDBC connection URL, add two additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| cloudSqlInstance | The instance connection name (which is found on the instance details page in Google Developers Console)  |
| socketFactory    | com.google.cloud.sql.mysql.SocketFactory |

For example, if the instance connection name is `foo:bar:baz`, the JDBC URL 
would be 
`jdbc:mysql://google/mydb?cloudSqlInstance=foo:bar:baz&socketFactory=com.google.cloud.sql.mysql.SocketFactory`

A tool is available in `examples/getting-started` that can help you generate the right JDBC URL.

## Credentials

The library needs to obtain credentials in order to retrieve SSL certificates that are used to connect to the instance.
[Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials) are used for this purpose.

On Google Compute Engine and Google App Engine, the VM/application service account is used.

For local development, application default credentials written by gcloud are used, if present. 
You must run `gcloud auth application-default login` once for the credentials to become available to the library.
