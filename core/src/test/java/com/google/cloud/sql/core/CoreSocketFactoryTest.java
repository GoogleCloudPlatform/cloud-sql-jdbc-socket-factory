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
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.cloud.sql.CredentialFactory;
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

// TODO(berezv): add multithreaded test
@RunWith(JUnit4.class)
public class CoreSocketFactoryTest extends CloudSqlCoreTestingBase {

  ListeningScheduledExecutorService defaultExecutor;

  @Before
  public void setUp() throws Exception {
    super.setup();
    defaultExecutor = CoreSocketFactory.getDefaultExecutor();
  }

  @Test
  public void create_throwsErrorForInvalidInstanceName() throws IOException {
    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        credentialFactory, 3307, defaultExecutor);
    try {
      coreSocketFactory.createSslSocket("myProject", Collections.singletonList("PRIMARY"));
      fail();
    } catch (IllegalArgumentException | InterruptedException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    }

    try {
      coreSocketFactory.createSslSocket("myProject:myRegion", Collections.singletonList("PRIMARY"));
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void create_throwsErrorForInvalidInstanceRegion() throws IOException {
    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        credentialFactory, 3307, defaultExecutor);
    try {
      coreSocketFactory.createSslSocket(
          "myProject:notMyRegion:myInstance", Collections.singletonList("PRIMARY"));
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat()
          .contains("The region specified for the Cloud SQL instance is incorrect");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
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

    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        credentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Collections.singletonList("PRIVATE"));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        credentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Collections.singletonList("PRIMARY"));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulDomainScopedConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        credentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "example.com:myProject:myRegion:myInstance", Collections.singletonList("PRIMARY"));
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_adminApiNotEnabled() throws IOException {
    SQLAdmin apiClient = new StubApiClientFactory(fakeNotConfiguredException()).create(
        credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        credentialFactory, 3307, defaultExecutor);
    try {
      // Use a different project to get Api Not Enabled Error.
      coreSocketFactory.createSslSocket(
          "NotMyProject:myRegion:myInstance", Collections.singletonList("PRIMARY"));
      fail("Expected RuntimeException");
    } catch (RuntimeException | InterruptedException e) {
      assertThat(e).hasMessageThat().contains(
          String.format("[%s] The Google Cloud SQL Admin API is not enabled for the project",
              "NotMyProject:myRegion:myInstance"));
    }
  }

  @Test
  public void create_notAuthorized() throws IOException {
    SQLAdmin apiClient = new StubApiClientFactory(fakeNotAuthorizedException()).create(
        credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        credentialFactory, 3307, defaultExecutor);
    try {
      // Use a different instance to simulate incorrect permissions.
      coreSocketFactory.createSslSocket(
          "myProject:myRegion:NotMyInstance", Collections.singletonList("PRIMARY"));
      fail();
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains(String.format(
          "[%s] The Cloud SQL Instance does not exist or your account is not authorized",
          "myProject:myRegion:NotMyInstance"));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void supportsCustomCredentialFactoryWithIAM() throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory = new StubCredentialFactory("foo", 6000L);

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        stubCredentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Collections.singletonList("PRIMARY"), true);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void supportsCustomCredentialFactoryWithNoExpirationTime()
      throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory = new StubCredentialFactory("foo", null);

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        stubCredentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Collections.singletonList("PRIMARY"), true);

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

    SQLAdmin apiClient = new StubApiClientFactory(
        fakeSuccessHttpTransport(Duration.ofSeconds(0))).create(credentialFactory.create());
    CoreSocketFactory coreSocketFactory = new CoreSocketFactory(clientKeyPair, apiClient,
        stubCredentialFactory, port, defaultExecutor);
    assertThrows(RuntimeException.class, () -> coreSocketFactory.createSslSocket(
        "myProject:myRegion:myInstance", Collections.singletonList("PRIMARY"), true));
  }

  private String readLine(Socket socket) throws IOException {
    BufferedReader bufferedReader = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), UTF_8));
    return bufferedReader.readLine();
  }


}
