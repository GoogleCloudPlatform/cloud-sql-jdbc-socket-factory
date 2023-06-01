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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO(berezv): add multithreaded test
@RunWith(JUnit4.class)
public class CoreSocketFactoryTest extends CloudSqlCoreTestingBase {

  ListeningScheduledExecutorService defaultExecutor;
  KeyPair key;

  @Before
  public void setUp() throws Exception {
    super.setup();
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    defaultExecutor =
        MoreExecutors.listeningDecorator(
            MoreExecutors.getExitingScheduledExecutorService(executor));
    key = clientKeyPair.get();
  }

  @After
  public void tearDown() throws Exception {
    defaultExecutor.shutdownNow();
  }

  private ConnectionConfig basicConfig(String name, List<String> ipType) {
    return new ConnectionConfig(
        name, AuthType.PASSWORD, null, null, null, String.join(",", ipType), null);
  }

  private ConnectionConfig basicConfig(String name, List<String> ipType, int port) {
    return new ConnectionConfig(
        name, AuthType.PASSWORD, null, null, null, String.join(",", ipType), null, port);
  }

  private ConnectionConfig basicConfig(String name, List<String> ipType, AuthType authType) {
    return new ConnectionConfig(name, authType, null, null, null, String.join(",", ipType), null);
  }

  private ConnectionConfig basicConfig(
      String name, List<String> ipType, AuthType authType, int port) {
    return new ConnectionConfig(
        name, authType, null, null, null, String.join(",", ipType), null, port);
  }

  @Test
  public void create_throwsErrorForInvalidInstanceName() throws IOException {
    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());

    try {
      coreSocketFactory.connect(basicConfig("myProject", Collections.singletonList("PRIMARY")));
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    }

    try {
      coreSocketFactory.connect(
          basicConfig("myProject:myRegion", Collections.singletonList("PRIMARY")));
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    }
  }

  @Test
  public void create_throwsErrorForInvalidInstanceRegion() throws IOException {
    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());
    try {
      coreSocketFactory.connect(
          basicConfig("myProject:notMyRegion:myInstance", Collections.singletonList("PRIMARY")));
      fail();
    } catch (RuntimeException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("The region specified for the Cloud SQL instance is incorrect");
    }
  }

  /**
   * Start an SSL server on the private IP, and verifies that specifying a preference for private IP
   * results in a connection to the private IP.
   */
  @Test
  public void create_successfulPrivateConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PRIVATE_IP);

    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());
    Socket socket =
        coreSocketFactory.connect(
            basicConfig(
                "myProject:myRegion:myInstance", Collections.singletonList("PRIVATE"), port));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());
    Socket socket =
        coreSocketFactory.connect(
            basicConfig(
                "myProject:myRegion:myInstance", Collections.singletonList("PRIMARY"), port));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulDomainScopedConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());
    Socket socket =
        coreSocketFactory.connect(
            basicConfig(
                "example.com:myProject:myRegion:myInstance",
                Collections.singletonList("PRIMARY"),
                port));
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_adminApiNotEnabled() throws IOException {
    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeNotConfiguredException());
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());
    try {
      // Use a different project to get Api Not Enabled Error.
      coreSocketFactory.connect(
          basicConfig("NotMyProject:myRegion:myInstance", Collections.singletonList("PRIMARY")));
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              String.format(
                  "[%s] The Google Cloud SQL Admin API is not enabled for the project",
                  "NotMyProject:myRegion:myInstance"));
    }
  }

  @Test
  public void create_notAuthorized() throws IOException {
    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeNotAuthorizedException());
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());
    try {
      // Use a different instance to simulate incorrect permissions.
      coreSocketFactory.connect(
          basicConfig("myProject:myRegion:NotMyInstance", Collections.singletonList("PRIMARY")));
      fail();
    } catch (RuntimeException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              String.format(
                  "[%s] The Cloud SQL Instance does not exist or your account is not authorized",
                  "myProject:myRegion:NotMyInstance"));
    }
  }

  @Test
  public void supportsCustomCredentialFactoryWithIAM() throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory =
        new StubCredentialFactory("foo", Instant.now().plusSeconds(3600).toEpochMilli());

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, stubCredentialFactory);
    Socket socket =
        coreSocketFactory.connect(
            basicConfig(
                "myProject:myRegion:myInstance",
                Collections.singletonList("PRIMARY"),
                AuthType.IAM,
                port));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void supportsCustomCredentialFactoryWithNoExpirationTime()
      throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory = new StubCredentialFactory("foo", null);

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, stubCredentialFactory);
    Socket socket =
        coreSocketFactory.connect(
            basicConfig(
                "myProject:myRegion:myInstance",
                Collections.singletonList("PRIMARY"),
                AuthType.IAM,
                port));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void doesNotSupportNonGoogleCredentialWithIAM() throws InterruptedException {
    class BasicAuthStubCredentialFactory implements CredentialFactory {

      @Override
      public HttpRequestInitializer create() {
        return new BasicAuthentication("user", "password");
      }
    }

    CredentialFactory stubCredentialFactory = new BasicAuthStubCredentialFactory();

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(defaultExecutor, key, apiClientFactory, new StubCredentialFactory());
    assertThrows(
        RuntimeException.class,
        () ->
            coreSocketFactory.connect(
                basicConfig(
                    "myProject:myRegion:myInstance",
                    Collections.singletonList("PRIMARY"),
                    AuthType.IAM)));
  }

  private String readLine(Socket socket) throws IOException {
    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    return bufferedReader.readLine();
  }
}
