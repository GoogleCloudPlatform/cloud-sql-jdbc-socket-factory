package com.google.cloud.sql.core;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;

class MockGoogleCredentials extends GoogleCredentials {
  MockGoogleCredentials(AccessToken token) {
    super(token);
  }

  @Override
  public void refresh() throws IOException {
    // noop refresh token
  }
}
