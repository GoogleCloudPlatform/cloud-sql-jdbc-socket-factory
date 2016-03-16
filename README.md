
## Cloud SQL MySQL Socket Factory

The Cloud SQL MySQL Socket Factory is a socket factory for the MySQL JDBC driver 
that allows a user with the appropriate permissions to connect to a Cloud SQL 
database without having to deal with IP whitelisting or SSL certificates 
manually. 

## Use

You must specify two additional properties in your JDBC URL:

| Property         | Value         |
| ---------------- | ------------- |
| cloudSqlInstance | The instance connection name (which is found on the instance details page in Google Developers Console)  |
| socketFactory    | com.google.cloud.sql.mysql.SocketFactory |

For example, if the instance connection name is `foo:bar:baz`, the JDBC URL 
would be 
`jdbc:mysql://google/mydb?cloudSqlInstance=foo:bar:baz&socketFactory=com.google.cloud.sql.mysql.SocketFactory`

