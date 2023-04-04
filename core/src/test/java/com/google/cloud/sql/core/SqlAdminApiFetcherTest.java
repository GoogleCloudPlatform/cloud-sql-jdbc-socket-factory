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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.cloud.sql.AuthType;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.net.ssl.SSLContext;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Test;

public class SqlAdminApiFetcherTest {
  @Test
  public void fetchesInstanceData()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {
    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse("p:r:i", "34.1.2.3", "10.0.0.1", "POSTGRES14");
    mockAdminApi.addGenerateEphemeralCertResponse("p:r:i", Duration.ofHours(1));
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    ListenableFuture<InstanceData> instanceDataFuture =
        fetcher.getInstanceData(
            new CloudSqlInstanceName("p:r:i"),
            null,
            AuthType.PASSWORD,
            newTestExecutor(),
            Futures.immediateFuture(mockAdminApi.getClientKeyPair()));

    InstanceData instanceData = instanceDataFuture.get();
    assertThat(instanceData.getSslContext()).isInstanceOf(SSLContext.class);

    Map<String, String> ipAddrs = instanceData.getIpAddrs();
    assertThat(ipAddrs.get("PRIMARY")).isEqualTo("34.1.2.3");
    assertThat(ipAddrs.get("PRIVATE")).isEqualTo("10.0.0.1");
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    //noinspection UnstableApiUsage
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }

  @Test
  public void throwsErrorIamAuthNotSupported()
      throws GeneralSecurityException, OperatorCreationException, ExecutionException,
          InterruptedException {
    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        "p:r:i", "34.1.2.3", "10.0.0.1", "SQLSERVER_2019_STANDARD");
    mockAdminApi.addGenerateEphemeralCertResponse("p:r:i", Duration.ofHours(1));
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    ListenableFuture<InstanceData> instanceData =
        fetcher.getInstanceData(
            new CloudSqlInstanceName("p:r:i"),
            OAuth2CredentialsWithRefresh.newBuilder()
                .setRefreshHandler(mockAdminApi.getRefreshHandler())
                .setAccessToken(new AccessToken("my-token", Date.from(Instant.now())))
                .build(),
            AuthType.IAM,
            newTestExecutor(),
            Futures.immediateFuture(mockAdminApi.getClientKeyPair()));

    ExecutionException ex = assertThrows(ExecutionException.class, instanceData::get);
    assertThat(ex)
        .hasMessageThat()
        .contains("[p:r:i] " + "IAM Authentication is not supported for SQL Server instances");
  }
}
