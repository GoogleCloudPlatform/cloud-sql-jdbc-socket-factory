## Cloud SQL Connector for Java

![CI Java 8][ci-badge-java-8]
![CI Java 11][ci-badge-java-11]

The Cloud SQL Connector for Java is a library that provides IAM-based authorization and encryption when connecting to a Cloud SQL instance. It can *not* provide a network path to a Cloud SQL instance if one is not already present.  See the [Connecting Overview](https://cloud.google.com/sql/docs/mysql/connect-overview) page for more information on connecting to a Cloud SQL instance.

## Usage
For usage information specific to your database engine and driver, see the pages below:

JDBC:
* [Connecting to MySQL using JDBC](docs/jdbc-mysql.md)
* [Connecting to Postgres using JDBC](docs/jdbc-postgres.md)
* [Connecting to SQL Server using JDBC](docs/jdbc-sqlserver.md)

R2DBC:
* [Connecting to MySQL using R2DBC](docs/r2dbc-mysql.md)
* [Connecting to Postgres using R2DBC](docs/r2dbc-postgres.md)
* [Connecting to SQL Server using R2DBC](docs/r2dbc-sqlserver.md)

For examples of this library being used in the context of an application, check out the sample applications located 
[here](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/master/cloud-sql).

### Authentication

This library uses [Application Default Credentials](
https://developers.google.com/identity/protocols/application-default-credentials) to authenticate
the connection to the Cloud SQL server. For more details, see the previously mentioned link.

To activate credentials locally, use the following [gcloud](https://cloud.google.com/sdk/gcloud/) 
command: 
```bash
gcloud auth application-default login
```
---

### Building the Drivers
To build a fat JAR containing the JDBC driver with the bundles Socket Factory dependencies you can issue the following Maven command from the location containing the project pom.xml:

```mvn -P jar-with-dependencies clean package -DskipTests```

This will create a *target* sub-folder within each of the module directories. Within these target directories you'll find the JDBC driver files.

Example:
```
mysql-socket-factory-connector-j-8–1.2.1-jar-with-dependencies.jar
postgres-socket-factory-1.2.1-jar-with-dependencies.jar
```

---

### Firewall configuration

The Cloud SQL proxy establishes connections to Cloud SQL instances using port 3307. Applications 
that are protected by a firewall may need to be configured to allow outgoing connections on TCP port
3307. A connection blocked by a firewall typically results in an error stating connection failure 
(e.g. `com.mysql.jdbc.exceptions.jdbc4.CommunicationsException: Communications link failure`).

### Connect with IntelliJ
 
In order to [connect IntelliJ](https://www.jetbrains.com/help/datagrip/connect-to-google-cloud-sql-instances.html) 
to your Cloud SQL instance, you will need to add this library as a jar with dependencies in
"Additional Files" section on the driver settings page. Prebuilt fat jars can be found on the 
[Releases](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/releases) page for 
this purpose. 
 
## Troubleshooting

Here are some troubleshooting tips to resolve common issues that come up when using the Java Connector:

1. Make sure you are using the latest version of the Cloud SQL Connector and your database driver to avoid incompatibilities. Some older versions of drivers are not supported
1. The Java connector provides authorization for connections, but it does not provide new paths to connectivity. For example, in order to connect to a Cloud SQL instance using a Private IP address, your application must already have VPC access. 

[ci-badge-java-8]: https://storage.googleapis.com/cloud-devrel-public/cloud-sql-connectors/java/java8_linux.svg
[ci-badge-java-11]: https://storage.googleapis.com/cloud-devrel-public/cloud-sql-connectors/java/java11_linux.svg

## Support policy

### Major version lifecycle
This project uses [semantic versioning](https://semver.org/), and uses the
following lifecycle regarding support for a major version:

**Active** - Active versions get all new features and security fixes (that
wouldn’t otherwise introduce a breaking change). New major versions are
guaranteed to be "active" for a minimum of 1 year.
**Deprecated** - Deprecated versions continue to receive security and critical
bug fixes, but do not receive new features. Deprecated versions will be publicly
supported for 1 year.
**Unsupported** - Any major version that has been deprecated for >=1 year is
considered publicly unsupported.

### Release cadence
This project aims for a minimum monthly release cadence. If no new
features or fixes have been added, a new PATCH version with the latest
dependencies is released.
