# Connecting to MySQL using R2DBC

## Setup and Usage

### Adding the library as a dependency

<!-- {x-release-please-start-version} -->
##### Maven
Include the following in the project's `pom.xml`: 
```maven-pom
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>cloud-sql-connector-r2dbc-mysql</artifactId>
      <version>1.14.1</version>
    </dependency>
```

##### Gradle
Include the following the project's `build.gradle`
```gradle
compile 'com.google.cloud.sql:cloud-sql-connector-r2dbc-mysql:1.14.1'
```

*Note: Also include the R2DBC Driver for MySQL, `io.asyncer:r2dbc-mysql:<LATEST-VERSION>`
<!-- {x-release-please-end} -->

### Creating the R2DBC URL

R2DBC URL template: `r2dbc:gcp:mysql://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>`

Add the following parameters:

| Property         | Value         |
| ---------------- | ------------- |
| DATABASE_NAME   | The name of the database to connect to |
| CLOUD_SQL_CONNECTION_NAME | The instance connection name (found on the instance details page) |
| DB_USER         | MySQL username |
| DB_PASS         | MySQL user's password |

## Service Account Delegation


The Java Connector supports service account impersonation with the
`TARGET_PRINCIPAL` option. When enabled, all API requests are made impersonating
the supplied service account. The IAM principal must have the
iam.serviceAccounts.getAccessToken permission or the role
roles/iam.serviceAccounts.serviceAccountTokenCreator.

```java
// Set up ConnectionFactoryOptions
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(DRIVER, "gcp")
    .option(PROTOCOL, "mysql")
    .option(PASSWORD, "password")
    .option(USER, "mysql-iam-user@gmail.com")
    .option(DATABASE, "my_db")
    .option(HOST, "project:region:instance")
    .option(ENABLE_IAM_AUTH, true)
    .option(TARGET_PRINCIPAL, "mysql-iam-user@gmail.com")
    .build();

// Initialize connection pool
ConnectionFactory connectionFactory = ConnectionFactories.get(options);
ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
    .builder(connectionFactory)
    .build();

this.connectionPool = new ConnectionPool(configuration);
```

In addition, the `DELEGATES` option controls impersonation delegation.
The value is a comma-separated list of service accounts containing chained
list of delegates required to grant the final access_token. If set,
the sequence of identities must have "Service Account Token Creator" capability
granted to the preceding identity. For example, if set to 
`"serviceAccountB,serviceAccountC"`, the application default credentials must
have the Token Creator role on serviceAccountB. serviceAccountB must have
the Token Creator on serviceAccountC. Finally, C must have Token Creator on
target principal. If unset, the application default credential principal
must "Service Account Token Creator" capability granted that role on the
target principal service account.


For example:
```java
options.option(TARGET_PRINCIPAL, "TARGET_SERVICE_ACCOUNT");
options.option(DELEGATES, "SERVICE_ACCOUNT_1,SERVICE_ACCOUNT_2");
```

In this example, the environment's application default principal impersonates
SERVICE_ACCOUNT_1 which impersonates SERVICE_ACCOUNT_2 which then
impersonates the TARGET_SERVICE_ACCOUNT.

In addition, the `DELEGATES` option supports an impersonation delegation chain
where the value is a comma-separated list of service accounts. The first service
account in the list is the impersonation target. Each subsequent service
account is a delegate to the previous service account. When delegation is
used, each delegate must have the permissions named above on the service
account it is delegating to.


## Examples

Examples for using the Cloud SQL JDBC Connector for Postgres can be found by looking at the integration tests in this repository.
* [Usage example](../r2dbc/mysql/src/test/java/com/google/cloud/sql/core/R2dbcMysqlIntegrationTests.java)
