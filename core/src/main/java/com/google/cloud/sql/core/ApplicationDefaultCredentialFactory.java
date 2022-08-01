package com.google.cloud.sql.core;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.sqladmin.SQLAdminScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.CredentialFactory;
import java.io.IOException;
import java.util.Arrays;

class ApplicationDefaultCredentialFactory implements CredentialFactory {

  @Override
  public HttpRequestInitializer create() {
    GoogleCredentials credentials;
    try {
      credentials = GoogleCredentials.getApplicationDefault();
    } catch (IOException err) {
      throw new RuntimeException(
          "Unable to obtain credentials to communicate with the Cloud SQL API", err);
    }
    if (credentials.createScopedRequired()) {
      credentials =
          credentials.createScoped(Arrays.asList(
              SQLAdminScopes.SQLSERVICE_ADMIN,
              SQLAdminScopes.CLOUD_PLATFORM)
          );
    }
    return new HttpCredentialsAdapter(credentials);
  }
}
