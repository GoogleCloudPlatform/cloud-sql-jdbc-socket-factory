# PostgreSQL sample for Google App Engine for Java 8
This sample demonstrates how to use [PostgreSQL](https://cloud.google.com/sql/) on Cloud SQL with Google App
Engine standard for Java 8.

## Setup

* If you haven't already, download and initialize the [Cloud SDK](https://cloud.google.com/sdk/):

    `gcloud init`

* If you haven't already, create an App Engine application within the current Google Cloud Project:

    `gcloud app create`

* If you haven't already, setup [Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials):

    `gcloud auth application-default login`


* [Create an instance](https://cloud.google.com/sql/docs/postgres/create-instance)

* [Create a database](https://cloud.google.com/sql/docs/postgres/create-manage-databases)

* [Create a user](https://cloud.google.com/sql/docs/postgres/create-manage-users)

* Note your **instance connection name** under **Overview > Properties**
It is in the format `Project:Region:Instance` and will be used below.

## Running locally
When running locally, the SocketFactory can use either the `cloud_sql_proxy`
or can connect directly to Cloud SQL with a certificate.

### Using cloud_sql_proxy
If you are using the cloud_sql_proxy to connect, follow these steps
before attempting the next section.
* `./cloud_sql_proxy -dir=/cloudsql &`
* `export GAE_RUNTIME=java8`

### Connect directly 
```bash
mvn clean appengine:run -DINSTANCE_CONNECTION_NAME=<YOUR_INSTANCE_CONNECTION_NAME> -Duser=root -Dpassword=myPassowrd -Ddatabase=myDatabase
```

## Deploying

```bash
mvn clean appengine:deploy -DINSTANCE_CONNECTION_NAME=<YOUR_INSTANCE_CONNECTION_NAME> -Duser=root -Dpassword=myPassword -Ddatabase=myDatabase
```


## Cleaning up

* [Delete Instance](https://cloud.google.com/sql/docs/postgres/delete-instance)
