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

package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.Credential.AccessMethod;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DefaultAccessTokenSupplierTest {

  private final Date now = new Date();
  private final Date past = Date.from(now.toInstant().plus(-1, ChronoUnit.HOURS));
  private final Date future = Date.from(now.toInstant().plus(1, ChronoUnit.HOURS));

  private GoogleCredentials scopedCredentials;
  private volatile int refreshCounter = 0;
  @Mock private OAuth2Credentials oAuth2Credentials;

  @Before
  public void setup() throws IOException {
    refreshCounter = 0;

    MockitoAnnotations.openMocks(this);
    // Scoped credentials can't be refreshed.
    scopedCredentials =
        new GoogleCredentials(new AccessToken("my-scoped-token", null)) {
          @Override
          public AccessToken refreshAccessToken() throws IOException {
            refreshCounter++;
            throw new IllegalStateException("Refresh not supported");
          }
        };
  }

  @Test
  public void testEmptyTokenOnEmptyCredentials() throws IOException {
    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(Optional.empty(), 1, Duration.ofMillis(10));
    assertThat(supplier.get()).isEqualTo(Optional.empty());
  }

  @Test
  public void testWithValidToken() throws Exception {
    // Google credentials can be refreshed
    GoogleCredentials googleCredentials =
        new GoogleCredentials(new AccessToken("my-token", future)) {
          @Override
          public GoogleCredentials createScoped(String... scopes) {
            return scopedCredentials;
          }

          @Override
          public AccessToken refreshAccessToken() throws IOException {
            refreshCounter++;
            return super.refreshAccessToken();
          }
        };

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(
            Optional.of(new HttpCredentialsAdapter(googleCredentials)), 1, Duration.ofMillis(10));
    Optional<AccessToken> token = supplier.get();

    assertThat(token.isPresent()).isTrue();
    assertThat(token.get().getTokenValue()).isEqualTo("my-scoped-token");
    assertThat(refreshCounter).isEqualTo(0);
  }

  @Test
  public void testInvalidWithUnknownRequestInitializer() throws Exception {
    HttpRequestInitializer bad =
        new HttpRequestInitializer() {
          @Override
          public void initialize(HttpRequest request) throws IOException {
            // ignore
          }
        };

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(Optional.of(bad), 1, Duration.ofMillis(10));
    RuntimeException ex = assertThrows(RuntimeException.class, supplier::get);
    assertThat(ex).hasMessageThat().contains("Unsupported credentials of type");
  }

  @Test
  public void testThrowsOnExpiredTokenRefreshNotSupported() throws Exception {

    GoogleCredentials expiredGoogleCredentials =
        new GoogleCredentials(new AccessToken("my-expired-token", past)) {
          @Override
          public GoogleCredentials createScoped(String... scopes) {
            return scopedCredentials;
          }

          @Override
          public AccessToken refreshAccessToken() throws IOException {
            refreshCounter++;
            return super.refreshAccessToken();
          }
        };

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(
            Optional.of(new HttpCredentialsAdapter(expiredGoogleCredentials)),
            1,
            Duration.ofMillis(10));
    IllegalStateException ex = assertThrows(IllegalStateException.class, supplier::get);
    assertThat(ex).hasMessageThat().contains("Error refreshing credentials");
    assertThat(refreshCounter).isEqualTo(1);
  }

  @Test
  public void testThrowsOnExpiredTokenRefreshStillExpired() throws Exception {

    GoogleCredentials refreshGetsExpiredToken =
        new GoogleCredentials(new AccessToken("my-expired-token", past)) {
          @Override
          public GoogleCredentials createScoped(String... scopes) {
            return scopedCredentials;
          }

          @Override
          public AccessToken refreshAccessToken() throws IOException {
            refreshCounter++;
            return new AccessToken("my-still-expired-token", past);
          }
        };

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(
            Optional.of(new HttpCredentialsAdapter(refreshGetsExpiredToken)),
            1,
            Duration.ofMillis(10));
    IllegalStateException ex = assertThrows(IllegalStateException.class, supplier::get);
    assertThat(ex).hasMessageThat().contains("expiration time is in the past");
    assertThat(refreshCounter).isEqualTo(1);
  }

  @Test
  public void testValidOnRefreshSucceeded() throws Exception {
    GoogleCredentials refreshableCredentials =
        new GoogleCredentials(new AccessToken("my-expired-token", past)) {
          @Override
          public GoogleCredentials createScoped(String... scopes) {
            return scopedCredentials;
          }

          @Override
          public AccessToken refreshAccessToken() throws IOException {
            refreshCounter++;
            return new AccessToken("my-refreshed-token", future);
          }
        };

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(
            Optional.of(new HttpCredentialsAdapter(refreshableCredentials)),
            1,
            Duration.ofMillis(10));
    Optional<AccessToken> token = supplier.get();

    assertThat(token.isPresent()).isTrue();
    assertThat(token.get().getTokenValue()).isEqualTo("my-scoped-token");

    assertThat(refreshCounter).isEqualTo(1);
  }

  @Test
  public void testValidOnRefreshFailsSometimes() throws Exception {
    GoogleCredentials refreshableCredentials =
        new GoogleCredentials(new AccessToken("my-expired-token", past)) {
          @Override
          public GoogleCredentials createScoped(String... scopes) {
            return scopedCredentials;
          }

          @Override
          public AccessToken refreshAccessToken() throws IOException {
            refreshCounter++;
            // fail the first request and every other request after it
            if (refreshCounter % 2 == 1) {
              throw new IOException("Fake Connect IOException");
            }
            return new AccessToken("my-refreshed-token", future);
          }
        };

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(
            Optional.of(new HttpCredentialsAdapter(refreshableCredentials)),
            3,
            Duration.ofMillis(10));
    Optional<AccessToken> token = supplier.get();

    assertThat(token.isPresent()).isTrue();
    assertThat(token.get().getTokenValue()).isEqualTo("my-scoped-token");

    assertThat(refreshCounter).isEqualTo(2);
  }

  @Test
  public void downscopesGoogleCredentials() {
    // Google credentials can be refreshed
    GoogleCredentials googleCredentials =
        new GoogleCredentials(new AccessToken("my-token", future)) {
          @Override
          public GoogleCredentials createScoped(String... scopes) {
            return scopedCredentials;
          }
        };

    GoogleCredentials downscoped =
        DefaultAccessTokenSupplier.getDownscopedCredentials(googleCredentials);
    assertThat(downscoped).isEqualTo(scopedCredentials);
  }

  @Test
  public void throwsErrorForWrongCredentialType() {
    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(
            Optional.of(new HttpCredentialsAdapter(oAuth2Credentials)), 1, Duration.ofMillis(10));
    RuntimeException ex = assertThrows(RuntimeException.class, supplier::get);

    assertThat(ex)
        .hasMessageThat()
        .contains("HttpCredentialsAdapter did not create valid credentials");
  }

  @Test
  public void testWithCredential() throws Exception {
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
                        response.setHeaderNames(Arrays.asList("WWW-Authenticate"));
                        response.setHeaderValues(Arrays.asList("Bearer my-refreshed-token"));

                        return response;
                      }
                    };
                  }
                })
            .build();
    credential.setAccessToken("my-token");
    credential.setExpirationTimeMilliseconds(future.getTime());

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(Optional.of(credential), 1, Duration.ofMillis(10));
    Optional<AccessToken> token = supplier.get();

    assertThat(token.isPresent()).isTrue();
    assertThat(token.get().getTokenValue()).isEqualTo("my-token");
    assertThat(refreshCounter).isEqualTo(0);
  }

  @Test
  public void testWithRefreshableCredential() throws Exception {
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

                        TokenResponse tr = new TokenResponse();
                        tr.setAccessToken("my-refreshed-token");
                        tr.setTokenType("access");
                        tr.setExpiresInSeconds(3600L);
                        tr.setScope("https://www.googleapis.com/auth/sqlservice.login");
                        String content = GsonFactory.getDefaultInstance().toString(tr);
                        response.setContent(content);

                        refreshCounter++;

                        return response;
                      }
                    };
                  }
                })
            .setJsonFactory(GsonFactory.getDefaultInstance())
            .setClientAuthentication(
                new HttpExecuteInterceptor() {
                  @Override
                  public void intercept(HttpRequest request) throws IOException {
                    // todo nothing
                  }
                })
            .setTokenServerUrl(new GenericUrl("https://example.com/"))
            .build();
    credential.setAccessToken("my-token");
    credential.setRefreshToken("refresh-token");
    credential.setExpirationTimeMilliseconds(past.getTime());

    DefaultAccessTokenSupplier supplier =
        new DefaultAccessTokenSupplier(Optional.of(credential), 1, Duration.ofMillis(10));
    Optional<AccessToken> token = supplier.get();

    assertThat(token.isPresent()).isTrue();
    assertThat(token.get().getTokenValue()).isEqualTo("my-refreshed-token");
    assertThat(refreshCounter).isEqualTo(1);
  }

  @Test
  public void testGetTokenExpiration() {
    assertThat(DefaultAccessTokenSupplier.getTokenExpirationTime(Optional.empty()))
        .isEqualTo(Optional.empty());
    assertThat(
            DefaultAccessTokenSupplier.getTokenExpirationTime(
                Optional.of(new AccessToken("", null))))
        .isEqualTo(Optional.empty());
    assertThat(
            DefaultAccessTokenSupplier.getTokenExpirationTime(
                Optional.of(new AccessToken("", past))))
        .isEqualTo(Optional.of(past));
  }
}
