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

import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.cloud.sql.AuthType;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import javax.net.ssl.SSLContext;
import org.junit.Before;
import org.junit.Test;

public class SqlAdminApiFetcherTest extends CloudSqlCoreTestingBase {

  private final ConnectSettings connectSettings = new ConnectSettings();

  ScheduledExecutorService defaultExecutor;

  private final SqlAdminApiFetcher fetcher =
      new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)))
          .create(credentialFactory.create());

  @Before
  public void setup() throws GeneralSecurityException {
    super.setup();
    connectSettings.setDatabaseVersion("SQLSERVER_2019_STANDARD");
    defaultExecutor = CoreSocketFactory.getDefaultExecutor();
  }

  @Test
  public void fetchesInstanceData() throws ExecutionException, InterruptedException {
    InstanceData instanceData =
        fetcher.getInstanceData(
            new CloudSqlInstanceName("myProject:myRegion:myInstance"),
            null,
            AuthType.PASSWORD,
            defaultExecutor,
            clientKeyPair);

    assertThat(instanceData.getSslContext()).isInstanceOf(SSLContext.class);

    Map<String, String> ipAddrs = instanceData.getIpAddrs();
    assertThat(ipAddrs.get("PRIMARY")).isEqualTo(PUBLIC_IP);
    assertThat(ipAddrs.get("PRIVATE")).isEqualTo(PRIVATE_IP);
  }

  @Test
  public void throwsErrorIamAuthNotSupported() {
    String connName = "my-project:region:my-instance";

    try {
      fetcher.checkDatabaseCompatibility(connectSettings, AuthType.IAM, connName);
    } catch (IllegalArgumentException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains(
              "[my-project:region:my-instance] "
                  + "IAM Authentication is not supported for SQL Server instances");
    }
  }
}
