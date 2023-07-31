package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.sql.CredentialFactory;
import java.util.Arrays;
import org.junit.Test;

public class ApplicationDefaultCredentialFactoryTest {

  @Test
  public void testProducesGoogleCredentials() {
    ApplicationDefaultCredentialFactory factory = new ApplicationDefaultCredentialFactory();
    Credentials credentials = newCredentials(factory);
    assertThat(credentials).isInstanceOf(GoogleCredentials.class);
  }

  @Test
  public void testImpersonatedCredentials() {
    ApplicationDefaultCredentialFactory factory = new ApplicationDefaultCredentialFactory();
    Credentials credentials = newCredentials(factory);

    CredentialFactory impersonatedFactory =
        factory.withDelegates(
            Arrays.asList(
                "first@serviceaccount.com",
                "second@serviceaccount.com",
                "third@serviceaccount.com"));
    Credentials impersonatedCredentials = newCredentials(impersonatedFactory);
    assertThat(impersonatedCredentials).isInstanceOf(ImpersonatedCredentials.class);
    ImpersonatedCredentials ic = (ImpersonatedCredentials) impersonatedCredentials;

    assertThat(ic.getAccount()).isEqualTo("first@serviceaccount.com");
    assertThat(ic.getSourceCredentials()).isEqualTo(credentials);
  }

  private static Credentials newCredentials(CredentialFactory factory) {
    HttpRequestInitializer initializer = factory.create();
    assertThat(initializer).isInstanceOf(HttpCredentialsAdapter.class);
    HttpCredentialsAdapter adapter = (HttpCredentialsAdapter) initializer;
    Credentials credentials = adapter.getCredentials();
    return credentials;
  }
}
