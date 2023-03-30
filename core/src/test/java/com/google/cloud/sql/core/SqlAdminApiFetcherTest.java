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

import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdmin.Builder;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.cloud.sql.AuthType;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.net.ssl.SSLContext;
import org.junit.Before;
import org.junit.Test;

public class SqlAdminApiFetcherTest {

  private ListeningScheduledExecutorService defaultExecutor;
  private CloudSqlAdminApiTestDouble cloudSqlAdminApiTestDouble;
  private SqlAdminApiFetcher fetcher;

  @Before
  public void setup() throws InvalidKeySpecException, NoSuchAlgorithmException {
    cloudSqlAdminApiTestDouble = new CloudSqlAdminApiTestDouble("project:region:instance");

    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(0);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    //noinspection UnstableApiUsage
    defaultExecutor =
        MoreExecutors.listeningDecorator(
            MoreExecutors.getExitingScheduledExecutorService(executor));

    HttpTransport mockTransport =
        cloudSqlAdminApiTestDouble.fakeSuccessHttpTransport(
            Duration.ofSeconds(0),
            CloudSqlAdminApiTestDouble.PUBLIC_IP,
            CloudSqlAdminApiTestDouble.PRIVATE_IP);
    SQLAdmin sqlAdmin =
        new Builder(
                mockTransport,
                GsonFactory.getDefaultInstance(),
                new MockGoogleCredential.Builder().build())
            .build();
    fetcher = new SqlAdminApiFetcher(sqlAdmin);
  }

  @Test
  public void fetchesInstanceData() throws ExecutionException, InterruptedException {
    ListenableFuture<InstanceData> instanceDataFuture =
        fetcher.getInstanceData(
            new CloudSqlInstanceName("project:region:instance"),
            null,
            AuthType.PASSWORD,
            defaultExecutor,
            cloudSqlAdminApiTestDouble.getClientKeyPair());

    InstanceData instanceData = instanceDataFuture.get();
    assertThat(instanceData.getSslContext()).isInstanceOf(SSLContext.class);

    Map<String, String> ipAddrs = instanceData.getIpAddrs();
    assertThat(ipAddrs.get("PRIMARY")).isEqualTo(CloudSqlAdminApiTestDouble.PUBLIC_IP);
    assertThat(ipAddrs.get("PRIVATE")).isEqualTo(CloudSqlAdminApiTestDouble.PRIVATE_IP);
  }

  @Test
  public void throwsErrorIamAuthNotSupported() {
    ConnectSettings connectSettings = new ConnectSettings();
    connectSettings.setDatabaseVersion("SQLSERVER_2019_STANDARD");

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            // TODO(enocom): This is a private interface. We should test through the public
            //   interface.
            () ->
                fetcher.checkDatabaseCompatibility(
                    connectSettings, AuthType.IAM, "my-project:region:my-instance"));

    assertThat(ex)
        .hasMessageThat()
        .contains(
            "[my-project:region:my-instance] "
                + "IAM Authentication is not supported for SQL Server instances");
  }

  @Test
  public void throwsErrorForInvalidInstanceRegion() {
    ListenableFuture<InstanceData> instanceDataFuture =
        fetcher.getInstanceData(
            new CloudSqlInstanceName("project:incorrect-region:instance"),
            null,
            AuthType.PASSWORD,
            defaultExecutor,
            cloudSqlAdminApiTestDouble.getClientKeyPair());

    ExecutionException ex = assertThrows(ExecutionException.class, instanceDataFuture::get);
    assertThat(ex)
        .hasMessageThat()
        .contains("The region specified for the Cloud SQL instance is incorrect");
  }
}
