package com.google.cloud.sql.core;

import com.google.api.services.sqladmin.SQLAdmin;

/** CloudSqlInstance is used for retrieving and referencing info related to a Cloud SQL instance. */
class CloudSqlInstance {
  private final String connectionName;
  private final String projectId;
  private final String regionId;
  private final String instanceId;

  CloudSqlInstance(String connectionName, SQLAdmin apiClient) {
    String[] connFields = connectionName.split(":");
    if (connFields.length != 3) {
      throw new IllegalArgumentException(
          "[%s] Cloud SQL connection name is invalid, expected string in the form of "
              + "\"<PROJECT_ID>:<REGION_ID>:<INSTANCE_ID>\".");
    }
    this.connectionName = connectionName;
    this.projectId = connFields[0];
    this.regionId = connFields[1];
    this.instanceId = connFields[2];
  }
}
