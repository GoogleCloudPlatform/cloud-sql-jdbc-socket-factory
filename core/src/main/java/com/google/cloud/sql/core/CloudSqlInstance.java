package com.google.cloud.sql.core;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpMapping;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** CloudSqlInstance is used for retrieving and referencing info related to a Cloud SQL instance. */
class CloudSqlInstance {
  private final String connectionName;
  private final String projectId;
  private final String regionId;
  private final String instanceId;
  private Map<String, String> ipAddrs = new HashMap<>();

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
    this.updateInstanceMetadata(apiClient);
  }

  /**
   * updateInstanceMetadata uses the Cloud SQL Admin API to fetch Instance Info.
   *
   * @param apiClient {@link SQLAdmin} client used to make the requests needed.
   */
  private void updateInstanceMetadata(SQLAdmin apiClient) {
    DatabaseInstance instanceMetadata;
    try {
      instanceMetadata = apiClient.instances().get(projectId, instanceId).execute();
    } catch (IOException ex) {
      throw addExceptionContext(
          ex,
          String.format("[%s] Failed to refresh metadata for Cloud SQL instance.", connectionName));
    }

    if (!instanceMetadata.getBackendType().equals("SECOND_GEN")) {
      throw new IllegalArgumentException(
          "[%s] Connections to Cloud SQL instance not supported - not a Second Generation "
              + "instance.");
    }
    if (!instanceMetadata.getRegion().equals(regionId)) {
      throw new IllegalArgumentException(
          "[%s] The region specified for the Cloud SQL instance is"
              + " incorrect. Please verify the instance connection name.");
    }
    synchronized (this) {
      for (IpMapping addr : instanceMetadata.getIpAddresses()) {
        ipAddrs.put(addr.getType(), addr.getIpAddress());
      }

      if (ipAddrs.isEmpty()) {
        throw new IllegalStateException(
            "[%s] Unable to connect to Cloud SQL instance: instance "
                + "does not have an assigned IP address.");
      }
    }
  }

  // createApiExceptions checks for common api errors and adds additional context.
  private RuntimeException addExceptionContext(IOException ex, String fallbackDesc) {
    // Verify we are able to extract a reason from an exception, or fallback to a generic desc
    GoogleJsonResponseException gjrEx =
        ex instanceof GoogleJsonResponseException ? (GoogleJsonResponseException) ex : null;
    if (gjrEx == null
        || gjrEx.getDetails() == null
        || gjrEx.getDetails().getErrors() == null
        || gjrEx.getDetails().getErrors().isEmpty()) {
      return new RuntimeException(fallbackDesc, ex);
    }
    // Check for commonly occurring user errors and add additional context
    String reason = gjrEx.getDetails().getErrors().get(0).getReason();
    if ("accessNotConfigured".equals(reason)) {
      // This error occurs when the project doesn't have the "Cloud SQL Admin API" enabled
      String apiLink =
          "https://console.cloud.google.com/apis/api/sqladmin/overview?project=" + projectId;
      return new RuntimeException(
          String.format(
              "[%s] The Google Cloud SQL Admin API is not enabled for the project \"%s\". Please "
                  + "use the Google Developers Console to enable it: %s",
              connectionName, projectId, apiLink));
    } else if ("notAuthorized".equals(reason)) {
      // This error occurs if the instance doesn't exist or the account isn't authorized
      // TODO(kvg): Add credential account name to error string.
      return new RuntimeException(
          String.format(
              "[%s] The Cloud SQL Instance does not exist or your account is not authorized to "
                  + "access it. Please verify the instance connection name and check the IAM "
                  + "permissions for project \"%s\" ",
              connectionName, projectId));
    }
    // Fallback to the generic description
    return new RuntimeException(fallbackDesc, ex);
  }
}
