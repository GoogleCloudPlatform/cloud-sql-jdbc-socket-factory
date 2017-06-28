An example showing how to connect and perform a query against a MySQL Cloud SQL instance.
The sample will connect to a MySQL Cloud SQL instance and list all the tables in the database.

The following variables in the code need to be updated to run against your instance:

| Variable               | Description   |
| ---------------------- | ------------- |
| instanceConnectionName | Can be found on the instance details page in Google Developers Console or by running `gcloud sql instances describe <instance> | grep connectionName`  |
| databaseName           | Name of the MySQL database |
| username               | MySQL username to use to connect to the instance |
| password               | MySQL password to use to connect to the instance |

The sample can be executed using `mvn compile exec:java`.
