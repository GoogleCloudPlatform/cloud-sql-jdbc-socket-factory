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

import static com.google.cloud.sql.core.InternalConnectorRegistry.DEFAULT_SERVER_PROXY_PORT;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.cloud.sql.ConnectorConfig;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConnectorTest extends CloudSqlCoreTestingBase {
  ListeningScheduledExecutorService defaultExecutor;
  private final long TEST_MAX_REFRESH_MS = 5000L;

  @Before
  public void setUp() throws Exception {
    super.setup();
    defaultExecutor = InternalConnectorRegistry.getDefaultExecutor();
  }

  @After
  public void tearDown() throws Exception {
    defaultExecutor.shutdownNow();
  }

  @Test
  public void create_throwsErrorForInvalidInstanceName() throws IOException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject")
            .withIpTypes("PRIMARY")
            .build();

    ConnectionConfig config2 =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion")
            .withIpTypes("PRIMARY")
            .build();

    Connector c = newConnector(config.getConnectorConfig(), DEFAULT_SERVER_PROXY_PORT);
    try {
      c.connect(config);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    }

    try {
      c.connect(config2);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    }
  }

  /**
   * Start an SSL server on the private IP, and verifies that specifying a preference for private IP
   * results in a connection to the private IP.
   */
  @Test
  public void create_successfulPrivateConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIVATE")
            .build();

    int port = sslServer.start(PRIVATE_IP);

    Connector connector = newConnector(config.getConnectorConfig(), port);

    Socket socket = connector.connect(config);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  private Connector newConnector(ConnectorConfig config, int port) {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    Connector connector =
        new Connector(
            config,
            factory.create(credentialFactory.create(), config),
            credentialFactory,
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port);
    return connector;
  }

  private String readLine(Socket socket) throws IOException {
    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    return bufferedReader.readLine();
  }
}
