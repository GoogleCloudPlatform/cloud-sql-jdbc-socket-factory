/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.Credential.AccessMethod;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;

public class CredentialFactoryTest {

  @Test
  public void testDefaultGetCredentialsWorksForGoogleCredentials() {
    GoogleCredentials googleCredentials = new GoogleCredentials(new AccessToken("my-token", null));
    CredentialFactory factory = new GoogleCredentialsFactory(googleCredentials);

    // Expected behavior from old implementation before getCredential()
    HttpRequestInitializer httpInit = factory.create();
    assertThat(httpInit).isInstanceOf(HttpCredentialsAdapter.class);
    assertThat(((HttpCredentialsAdapter) httpInit).getCredentials())
        .isSameInstanceAs(googleCredentials);

    // Expected behavior of getCredential()
    assertThat(factory.getCredentials().getAccessToken().getTokenValue()).isEqualTo("my-token");
  }

  @Test
  public void testDefaultGetCredentialsWorksForCredential() {
    Credential credential =
        new Credential.Builder(
                new AccessMethod() {
                  @Override
                  public void intercept(HttpRequest request, String accessToken)
                      throws IOException {
                    // do nothing
                  }

                  @Override
                  public String getAccessTokenFromRequest(HttpRequest request) {
                    return "my-refreshed-token";
                  }
                })
            .setTransport(
                new HttpTransport() {
                  @Override
                  protected LowLevelHttpRequest buildRequest(String method, String url)
                      throws IOException {
                    return new LowLevelHttpRequest() {
                      @Override
                      public void addHeader(String name, String value) throws IOException {
                        // do nothing
                      }

                      @Override
                      public LowLevelHttpResponse execute() throws IOException {
                        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                        response.setHeaderNames(Collections.singletonList("WWW-Authenticate"));
                        response.setHeaderValues(
                            Collections.singletonList("Bearer my-refreshed-token"));

                        return response;
                      }
                    };
                  }
                })
            .build();
    credential.setAccessToken("my-token");

    CredentialFactory factory = new Oauth2CredentialFactory(credential);

    // Expected behavior from old implementation before getCredential()
    HttpRequestInitializer httpInit = factory.create();
    assertThat(httpInit).isInstanceOf(Credential.class);
    assertThat(httpInit).isSameInstanceAs(credential);

    // Expected behavior of getCredential()
    assertThat(factory.getCredentials().getAccessToken().getTokenValue()).isEqualTo("my-token");
  }

  @Test
  public void testDefaultGetCredentialsThrowsExceptionForUnknownCredential() {
    OAuth2Credentials creds = OAuth2Credentials.create(new AccessToken("abc", null));

    CredentialFactory factory = new Oauth2BadCredentialFactory(creds);

    // Expected behavior from old implementation before getCredential()
    HttpRequestInitializer httpInit = factory.create();
    assertThat(httpInit).isInstanceOf(HttpCredentialsAdapter.class);
    assertThat(((HttpCredentialsAdapter) httpInit).getCredentials()).isSameInstanceAs(creds);

    // Expected behavior of getCredential()
    assertThrows(RuntimeException.class, factory::getCredentials);
  }

  private static class Oauth2CredentialFactory implements CredentialFactory {
    private final Credential credential;

    private Oauth2CredentialFactory(Credential credential) {
      this.credential = credential;
    }

    @Override
    public HttpRequestInitializer create() {
      return credential;
    }
  }

  private static class Oauth2BadCredentialFactory implements CredentialFactory {
    private final OAuth2Credentials credential;

    private Oauth2BadCredentialFactory(OAuth2Credentials credential) {
      this.credential = credential;
    }

    @Override
    public HttpRequestInitializer create() {
      return new HttpCredentialsAdapter(credential);
    }
  }

  private static class GoogleCredentialsFactory implements CredentialFactory {
    private final GoogleCredentials credentials;

    private GoogleCredentialsFactory(GoogleCredentials credentials) {
      this.credentials = credentials;
    }

    @Override
    public HttpRequestInitializer create() {
      return new HttpCredentialsAdapter(credentials);
    }

    @Override
    public GoogleCredentials getCredentials() {
      return credentials;
    }
  }
}
