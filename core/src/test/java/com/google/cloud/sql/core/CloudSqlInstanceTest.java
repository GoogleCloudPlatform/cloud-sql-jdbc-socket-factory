/*
 * Copyright 2022 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {

  @Mock private GoogleCredentials googleCredentials;
  @Mock private GoogleCredentials scopedCredentials;
  @Mock private OAuth2Credentials oAuth2Credentials;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(googleCredentials.createScoped("https://www.googleapis.com/auth/sqlservice.login"))
        .thenReturn(scopedCredentials);
  }

  @Test
  public void downscopesGoogleCredentials() {
    GoogleCredentials downscoped = CloudSqlInstance.getDownscopedCredentials(googleCredentials);
    assertThat(downscoped).isEqualTo(scopedCredentials);
    verify(googleCredentials, times(1))
        .createScoped("https://www.googleapis.com/auth/sqlservice.login");
  }

  @Test
  public void throwsErrorForWrongCredentialType() {
    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> CloudSqlInstance.getDownscopedCredentials(oAuth2Credentials));

    assertThat(ex)
        .hasMessageThat()
        .contains("Failed to downscope credentials for IAM Authentication");
  }

  @Test
  public void timeUntilRefreshImmediate() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofMinutes(3)));
    assertThat(CloudSqlInstance.secondsUntilRefresh(expiration)).isEqualTo(0L);
  }

  @Test
  public void timeUntilRefresh1Hr() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofMinutes(59)));
    long expected = Duration.ofMinutes(59).minus(Duration.ofMinutes(4)).getSeconds();
    Assert.assertEquals(
        (float) CloudSqlInstance.secondsUntilRefresh(expiration), (float) expected, 1);
  }

  @Test
  public void timeUntilRefresh24Hr() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofHours(23)));
    long expected = Duration.ofHours(23).dividedBy(2).getSeconds();
    Assert.assertEquals(
        (float) CloudSqlInstance.secondsUntilRefresh(expiration), (float) expected, 1);
  }

  @Test
  public void testNew_whenCredentialsAreNonGoogle_throwsException() {
    class BasicAuthCredentialFactory implements CredentialFactory {

      @Override
      public HttpRequestInitializer create() {
        return new BasicAuthentication("user", "password");
      }
    }
    BasicAuthCredentialFactory credentialFactory = new BasicAuthCredentialFactory();
    SqlAdminApiFetcher fetcher =
        new SqlAdminApiFetcher(
            new SQLAdmin.Builder(
                    new MockHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credentialFactory.create())
                .build());

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                new CloudSqlInstance(
                    "myProject:myRegion:myInstance",
                    fetcher,
                    AuthType.IAM,
                    credentialFactory,
                    null,
                    null));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Not supporting credentials of type com.google.api.client.http.BasicAuthentication");
  }

  @Test
  public void testGetPreferredIps_returnsMatchingIp() {
    // TODO(enocom): Test happy path
  }

  @Test
  public void testGetPreferredIps_whenNoMatches_throwsException() {
    // TODO(enocom): Test when e.g. private IP is requested on an instance that doesn't have one.
  }
}
