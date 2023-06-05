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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.cloud.sql.AuthType;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {

  public static final String SAMPLE_PUBLIC_IP = "34.1.2.3";
  public static final String SAMPLE_PRIVATE_IP = "10.0.0.1";
  public static final String INSTANCE_CONNECTION_NAME = "p:r:i";
  public static final String DATABASE_VERSION = "POSTGRES14";

  @Mock private GoogleCredentials googleCredentials;
  @Mock private GoogleCredentials scopedCredentials;
  @Mock private OAuth2Credentials oAuth2Credentials;

  private ListeningScheduledExecutorService executor;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(googleCredentials.createScoped("https://www.googleapis.com/auth/sqlservice.login"))
        .thenReturn(scopedCredentials);
    executor = CoreSocketFactory.getDefaultExecutor();
  }

  @After
  public void teardown() {
    executor.shutdownNow();
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
  public void testFetchInstanceData_throwsException_whenTokenIsExpired()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION);

    OAuth2Credentials cred =
        OAuth2CredentialsWithRefresh.newBuilder()
            .setRefreshHandler(
                mockAdminApi.getRefreshHandler(
                    "refresh-token",
                    Date.from(Instant.now().minus(1, ChronoUnit.HOURS)) /* 1 hour ago */))
            .setAccessToken(new AccessToken("original-token", Date.from(Instant.now())))
            .build();

    StubCredentialFactory credFactory = new StubCredentialFactory(cred);

    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    try {
      CloudSqlInstance instance =
          new CloudSqlInstance(
              INSTANCE_CONNECTION_NAME,
              fetcher,
              AuthType.IAM,
              credFactory,
              executor,
              Futures.immediateFuture(mockAdminApi.getClientKeyPair()));
      instance.getCurrentInstanceData().get();
      Assert.fail("Exception expected");
    } catch (InterruptedException | ExecutionException ex) {
      assertThat(ex).hasMessageThat().contains("Access Token expiration time is in the past");
    }
  }

  @Test
  public void
      testFetchInstanceData_throwsException_whenRequestsTimeout_andAttemptsImmediateRefresh()
          throws GeneralSecurityException, OperatorCreationException {

    MockAdminApi mockAdminApi = buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION);

    OAuth2Credentials cred =
        OAuth2CredentialsWithRefresh.newBuilder()
            .setRefreshHandler(
                mockAdminApi.getRefreshHandler(
                    "refresh-token",
                    Date.from(Instant.now().plus(1, ChronoUnit.HOURS)) /* 1 hour from now */))
            .setAccessToken(new AccessToken("original-token", Date.from(Instant.now())))
            .build();

    StubCredentialFactory credFactory = new StubCredentialFactory(cred);

    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(new BadConnectionFactory())
            .create(new StubCredentialFactory().create());

    CloudSqlInstance instance =
        new CloudSqlInstance(
            INSTANCE_CONNECTION_NAME,
            fetcher,
            AuthType.IAM,
            credFactory,
            executor,
            Futures.immediateFuture(mockAdminApi.getClientKeyPair()));

    ListenableFuture<InstanceData> firstFuture = instance.getCurrentInstanceData();
    try {
      firstFuture.get();
      Assert.fail("Exception expected");
    } catch (InterruptedException | ExecutionException ex) {
      Throwable t = ex;
      while (t.getCause() != t && t != null) {
        if (t.getMessage().contains("Fake connect timeout")) {
          return;
        }
        t = t.getCause();
      }
      Assert.fail("Expected exception cause with \"Fake connect timeout\" message");
    }

    // assert that the next future was scheduled because the first one failed.
    ListenableFuture<InstanceData> next = instance.getNextInstanceData();
    assertThat(next.isDone()).isFalse();

    // attempt to cancel and then make sure this is running
    next.cancel(false);
    assertThat(next.isCancelled()).isFalse();
  }

  @SuppressWarnings("SameParameterValue")
  private MockAdminApi buildMockAdminApi(String instanceConnectionName, String databaseVersion)
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        instanceConnectionName, SAMPLE_PUBLIC_IP, SAMPLE_PRIVATE_IP, databaseVersion);
    mockAdminApi.addGenerateEphemeralCertResponse(instanceConnectionName, Duration.ofHours(1));
    return mockAdminApi;
  }
}
