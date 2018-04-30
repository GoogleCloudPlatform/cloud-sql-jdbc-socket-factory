[![Build
Status](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory.svg?branch=master)](https://travis-ci.org/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory)
## Cloud SQL Socket Factory for JDBC drivers

The Cloud SQL Socket Factory is a library for the MySQL/Postgres JDBC drivers
that allows a user with the appropriate permissions to connect to a Cloud SQL
database without having to deal with IP whitelisting or SSL certificates
manually.

## Instructions

The library is [available in Maven Central](http://search.maven.org/#artifactdetails%7Ccom.google.cloud.sql%7Cmysql-socket-factory%7C1.0.4%7Cjar).

Add a dependency using your favorite build tool. Maven and Gradle examples are shown below.


### MySQL

**Note**: If you wish to use the 6.x (development) version of the MySQL driver, use the artifact id
'mysql-socket-factory-connector-j-6'.

#### Adding dependency (Maven)

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory</artifactId>
    <version>1.0.7</version>
</dependency>
```

#### Adding dependency (Gradle)

```gradle
compile 'com.google.cloud.sql:mysql-socket-factory:1.0.7'
```

#### Using

When specifying the JDBC connection URL, add two additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.mysql.SocketFactory |
| cloudSqlInstance | The instance connection name (which is found on the instance details page in Google Developers Console)  |

For example, if the instance connection name is `foo:bar:baz`, the JDBC URL
would be
`jdbc:mysql://google/mydb?socketFactory=com.google.cloud.sql.mysql.SocketFactory&cloudSqlInstance=foo:bar:baz`

A tool is available in `examples/getting-started` that can help generate the JDBC URL and verify that connectivity can be established.

### PostgreSQL

#### Adding dependency (Maven)

```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.0.7</version>
</dependency>
```

#### Adding dependency (Gradle)

```gradle
compile 'com.google.cloud.sql:postgres-socket-factory:1.0.7'
```

#### Using

When specifying the JDBC connection URL, add two additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| socketFactory    | com.google.cloud.sql.postgres.SocketFactory |
| socketFactoryArg | The instance connection name (which is found on the instance details page in Google Developers Console)  |

For example, if the instance connection name is `foo:bar:baz`, the JDBC URL
would be
`jdbc:postgresql://google/mydb?socketFactory=com.google.cloud.sql.postgres.SocketFactory&socketFactoryArg=foo:bar:baz`

A tool is available in `examples/getting-started` that can help generate the JDBC URL and verify that connectivity can be established.

## Credentials

The library needs to obtain credentials in order to retrieve SSL certificates that are used to connect to the instance.
[Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials) are used for this purpose.

On Google Compute Engine and Google App Engine, the VM/application service account is used.

For local development, application default credentials written by gcloud are used, if present.
You must run `gcloud auth application-default login` once for the credentials to become available to the library.
