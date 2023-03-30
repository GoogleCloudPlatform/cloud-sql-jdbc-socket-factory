/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.time.Duration;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CoreSocketFactoryTest {

  private CoreSocketFactory coreSocketFactory;

  @Before
  public void setUp() throws Exception {
    FakeCloudSqlProxyServer sslServer = new FakeCloudSqlProxyServer();
    int port = sslServer.start("127.0.0.1");
    CloudSqlAdminApiTestDouble cloudSqlAdminApiTestDouble =
        new CloudSqlAdminApiTestDouble("project:region:instance");
    ListeningScheduledExecutorService defaultExecutor = CoreSocketFactory.getDefaultExecutor();
    SqlAdminApiFetcher apiClient =
        new SqlAdminApiFetcher(
            new SQLAdmin.Builder(
                    cloudSqlAdminApiTestDouble.fakeSuccessHttpTransport(
                        Duration.ofSeconds(0),
                        CloudSqlAdminApiTestDouble.PUBLIC_IP,
                        CloudSqlAdminApiTestDouble.PRIVATE_IP),
                    GsonFactory.getDefaultInstance(),
                    new StubCredentialFactory("", 0L).create())
                .build());
    coreSocketFactory =
        new CoreSocketFactory(
            cloudSqlAdminApiTestDouble.getClientKeyPair(),
            apiClient,
            new StubCredentialFactory("", 0L),
            port,
            defaultExecutor);
  }

  @Test
  public void testSslSocket_connectsSuccessfully() throws IOException, InterruptedException {
    Socket socket =
        coreSocketFactory.createSslSocket(
            "project:region:instance", Collections.singletonList("PRIMARY"));

    assertThat(socketIsConnected(socket)).isEqualTo(true);
  }

  private boolean socketIsConnected(Socket socket) throws IOException {
    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    return bufferedReader.readLine().equals("HELLO");
  }
}
