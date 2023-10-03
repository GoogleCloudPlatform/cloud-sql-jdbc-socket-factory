/*
 * Copyright 2016 Google Inc.
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO(berezv): add multithreaded test
@RunWith(JUnit4.class)
public class CoreSocketFactoryTest extends CloudSqlCoreTestingBase {
  private final long TEST_MAX_REFRESH_MS = 5000L;

  ListeningScheduledExecutorService defaultExecutor;

  @Before
  public void setUp() throws Exception {
    super.setup();
    defaultExecutor = CoreSocketFactory.getDefaultExecutor();
  }

  @After
  public void tearDown() throws Exception {
    defaultExecutor.shutdownNow();
  }

  @Test
  public void create_throwsErrorForInvalidInstanceName() throws IOException {
    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, 3307, TEST_MAX_REFRESH_MS, defaultExecutor);
    try {
      coreSocketFactory.createSslSocket(
          "myProject",
          Collections.singletonList("PRIMARY"),
          AuthType.PASSWORD,
          null,
          Collections.emptyList());
      fail();
    } catch (IllegalArgumentException | InterruptedException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    }

    try {
      coreSocketFactory.createSslSocket(
          "myProject:myRegion",
          Collections.singletonList("PRIMARY"),
          AuthType.PASSWORD,
          null,
          Collections.emptyList());
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void create_throwsErrorForInvalidInstanceRegion() throws IOException {
    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, 3307, TEST_MAX_REFRESH_MS, defaultExecutor);
    try {
      coreSocketFactory.createSslSocket(
          "myProject:notMyRegion:myInstance",
          Collections.singletonList("PRIMARY"),
          AuthType.PASSWORD,
          null,
          Collections.emptyList());
      fail();
    } catch (RuntimeException e) {
      assertThat(e)
          .hasMessageThat()
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

    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, port, TEST_MAX_REFRESH_MS, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance",
            Collections.singletonList("PRIVATE"),
            AuthType.PASSWORD,
            null,
            Collections.emptyList());

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_failOnEmptyTargetPrincipal() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, port, TEST_MAX_REFRESH_MS, defaultExecutor);
    try {

      coreSocketFactory.createSslSocket(
          "myProject:myRegion:myInstance",
          Collections.singletonList("PRIMARY"),
          AuthType.PASSWORD,
          null,
          Arrays.asList("delegate-service-principal@example.com"));
      fail("IllegalArgumentException expected.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains(CoreSocketFactory.CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY);
    }
  }

  @Test
  public void create_successfulConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, port, TEST_MAX_REFRESH_MS, defaultExecutor);

    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance",
            Collections.singletonList("PRIMARY"),
            AuthType.PASSWORD,
            null,
            Collections.emptyList());

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulDomainScopedConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, port, TEST_MAX_REFRESH_MS, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "example.com:myProject:myRegion:myInstance",
            Collections.singletonList("PRIMARY"),
            AuthType.PASSWORD,
            null,
            Collections.emptyList());
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_adminApiNotEnabled() throws IOException {
    ApiFetcherFactory factory = new StubApiFetcherFactory(fakeNotConfiguredException());
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, 3307, TEST_MAX_REFRESH_MS, defaultExecutor);
    try {
      // Use a different project to get Api Not Enabled Error.
      coreSocketFactory.createSslSocket(
          "NotMyProject:myRegion:myInstance",
          Collections.singletonList("PRIMARY"),
          AuthType.PASSWORD,
          null,
          Collections.emptyList());
      fail("Expected RuntimeException");
    } catch (RuntimeException | InterruptedException e) {
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
    ApiFetcherFactory factory = new StubApiFetcherFactory(fakeNotAuthorizedException());
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair, factory, credentialFactory, 3307, TEST_MAX_REFRESH_MS, defaultExecutor);
    try {
      // Use a different instance to simulate incorrect permissions.
      coreSocketFactory.createSslSocket(
          "myProject:myRegion:NotMyInstance",
          Collections.singletonList("PRIMARY"),
          AuthType.PASSWORD,
          null,
          Collections.emptyList());
      fail();
    } catch (RuntimeException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              String.format(
                  "[%s] The Cloud SQL Instance does not exist or your account is not authorized",
                  "myProject:myRegion:NotMyInstance"));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void supportsCustomCredentialFactoryWithIAM() throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory =
        new StubCredentialFactory("foo", Instant.now().plusSeconds(3600).toEpochMilli());

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair,
            factory,
            stubCredentialFactory,
            port,
            TEST_MAX_REFRESH_MS,
            defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance",
            Collections.singletonList("PRIMARY"),
            AuthType.IAM,
            null,
            Collections.emptyList());

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void supportsCustomCredentialFactoryWithNoExpirationTime()
      throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory = new StubCredentialFactory("foo", null);

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair,
            factory,
            stubCredentialFactory,
            port,
            TEST_MAX_REFRESH_MS,
            defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance",
            Collections.singletonList("PRIMARY"),
            AuthType.IAM,
            null,
            Collections.emptyList());

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

    ApiFetcherFactory factory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(
            clientKeyPair,
            factory,
            stubCredentialFactory,
            port,
            TEST_MAX_REFRESH_MS,
            defaultExecutor);
    assertThrows(
        RuntimeException.class,
        () ->
            coreSocketFactory.createSslSocket(
                "myProject:myRegion:myInstance",
                Collections.singletonList("PRIMARY"),
                AuthType.IAM,
                null,
                Collections.emptyList()));
  }

  @Test
  public void testGetApplicationNameWithApplicationName() {
    CoreSocketFactory.resetUserAgent();
    CoreSocketFactory.setApplicationName("sample-app");
    CoreSocketFactory.addArtifactId("unit-test");
    CoreSocketFactory.getInstance();
    assertThat(CoreSocketFactory.getUserAgents()).startsWith("unit-test/");
    assertThat(CoreSocketFactory.getUserAgents()).endsWith(" sample-app");
  }

  @Test
  public void testGetApplicationNameFailsAfterInitialization() {
    CoreSocketFactory.resetUserAgent();
    CoreSocketFactory.getInstance();
    assertThrows(
        IllegalStateException.class, () -> CoreSocketFactory.setApplicationName("sample-app"));
  }

  private String readLine(Socket socket) throws IOException {
    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    return bufferedReader.readLine();
  }
}
