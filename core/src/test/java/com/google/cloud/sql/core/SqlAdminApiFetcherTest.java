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

import com.google.cloud.sql.AuthType;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.net.ssl.SSLContext;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Test;

public class SqlAdminApiFetcherTest {

  public static final String SAMPLE_PUBLIC_IP = "34.1.2.3";
  public static final String SAMPLE_PRIVATE_IP = "10.0.0.1";
  public static final String SAMPLE_PSC_IP = "10.0.0.2";
  public static final String SAMPLE_PCS_DNS_NAME = "abcde.12345.us-central1.sql.goog";
  public static final String INSTANCE_CONNECTION_NAME = "p:r:i";
  public static final String DATABASE_VERSION = "POSTGRES14";

  @Test
  public void testFetchInstanceData_returnsIpAddresses()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {
    MockAdminApi mockAdminApi = buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION);
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    InstanceData instanceData =
        fetcher
            .getInstanceData(
                new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                () -> Optional.empty(),
                AuthType.PASSWORD,
                newTestExecutor(),
                Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
            .get();
    assertThat(instanceData.getSslContext()).isInstanceOf(SSLContext.class);

    Map<String, String> ipAddrs = instanceData.getIpAddrs();
    assertThat(ipAddrs.get("PRIMARY")).isEqualTo(SAMPLE_PUBLIC_IP);
    assertThat(ipAddrs.get("PRIVATE")).isEqualTo(SAMPLE_PRIVATE_IP);
    assertThat(ipAddrs.get("PSC")).isEqualTo(SAMPLE_PCS_DNS_NAME);
  }

  @Test
  public void testFetchInstanceData_returnsPscForNonIpDatabase()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {

    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        INSTANCE_CONNECTION_NAME, null, null, DATABASE_VERSION, SAMPLE_PCS_DNS_NAME);
    mockAdminApi.addGenerateEphemeralCertResponse(INSTANCE_CONNECTION_NAME, Duration.ofHours(1));

    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    InstanceData instanceData =
        fetcher
            .getInstanceData(
                new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                () -> Optional.empty(),
                AuthType.PASSWORD,
                newTestExecutor(),
                Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
            .get();
    assertThat(instanceData.getSslContext()).isInstanceOf(SSLContext.class);

    Map<String, String> ipAddrs = instanceData.getIpAddrs();
    assertThat(ipAddrs.get("PSC")).isEqualTo(SAMPLE_PCS_DNS_NAME);
    assertThat(ipAddrs.size()).isEqualTo(1);
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
  public void testFetchInstanceData_throwsException_whenIamAuthnIsNotSupported()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi =
        buildMockAdminApi(INSTANCE_CONNECTION_NAME, "SQLSERVER_2019_STANDARD");
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () -> {
              fetcher.getInstanceData(
                  new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                  () -> Optional.empty(),
                  AuthType.IAM,
                  newTestExecutor(),
                  Futures.immediateFuture(mockAdminApi.getClientKeyPair()));
            });
    assertThat(ex)
        .hasMessageThat()
        .contains("[p:r:i] IAM Authentication is not supported for SQL Server instances");
  }

  @Test
  public void testFetchInstanceData_throwsException_whenRequestsTimeout()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION);
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(new BadConnectionFactory())
            .create(new StubCredentialFactory().create());

    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () -> {
              fetcher.getInstanceData(
                  new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                  () -> {
                    throw new IOException("Fake connect timeout");
                  },
                  AuthType.IAM,
                  newTestExecutor(),
                  Futures.immediateFuture(mockAdminApi.getClientKeyPair()));
            });

    assertThat(ex.getCause()).hasMessageThat().contains("Fake connect timeout");
  }

  @SuppressWarnings("SameParameterValue")
  private MockAdminApi buildMockAdminApi(String instanceConnectionName, String databaseVersion)
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        instanceConnectionName,
        SAMPLE_PUBLIC_IP,
        SAMPLE_PRIVATE_IP,
        databaseVersion,
        SAMPLE_PCS_DNS_NAME);
    mockAdminApi.addGenerateEphemeralCertResponse(instanceConnectionName, Duration.ofHours(1));
    return mockAdminApi;
  }
}
