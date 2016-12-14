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

**Note**: If you wish to use MySQL Connector/J 6.x (development) driver, use the artifact id
`mysql-socket-factory-connector-j-6`.

### Maven

#### With MySQL Connector/J 5.x
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory</artifactId>
    <version>1.0.2</version>
</dependency>
```

#### With MySQL Connector/J 6.x (development)
```maven-pom
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>mysql-socket-factory-connector-j-6</artifactId>
    <version>1.0.2</version>
</dependency>
```

### Gradle

#### With MySQL Connector/J 5.x
```gradle
compile 'com.google.cloud.sql:mysql-socket-factory:1.0.2'
```

#### With MySQL Connector/J 6.x
```gradle
compile 'com.google.cloud.sql:mysql-socket-factory-connector-j-6:1.0.2'
```

## Using the Socket Factory & Configuring JDBC Connection URL

When specifying the JDBC connection URL, add two additional parameters:

| Property         | Value         |
| ---------------- | ------------- |
| cloudSqlInstance | The instance ID (which is found on the instance details page in Google Cloud Console)  |
| socketFactory    | com.google.cloud.sql.mysql.SocketFactory |

For example, if the Cloud SQL instance ID is `foo:bar:baz`, then the JDBC Connection URL is:
`jdbc:mysql://google/mydb?cloudSqlInstance=foo:bar:baz&socketFactory=com.google.cloud.sql.mysql.SocketFactory`'

**Note** The database you wish to use (e.g., `mydb` in the above example) must be created manually. See [Creating a database documentation](https://cloud.google.com/sql/docs/create-manage-mysql-databases#creating_a_database).

A tool is available in `examples/getting-started` that can help you generate the right JDBC Connection URL.

## Credentials

The library needs to obtain credentials in order to retrieve SSL certificates that are used to connect to the instance.
[Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials) are used for this purpose.

On Google Compute Engine and Google App Engine, the default VM/application service account is used.

For local development, application default credentials written by gcloud are used, if present. 
You must run `gcloud auth application-default login` once for the credentials to become available to the library.
