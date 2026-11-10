/*
 * Copyright 2024 Google LLC
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

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.IpType;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LazyRefreshConnectionInfoCacheTest {
  private ListenableFuture<KeyPair> keyPairFuture;
  private final StubCredentialFactory stubCredentialFactory =
      new StubCredentialFactory("my-token", System.currentTimeMillis() + 3600L);

  @Before
  public void setup() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    this.keyPairFuture = Futures.immediateFuture(mockAdminApi.getClientKeyPair());
  }

  @Test
  public void testCloudSqlInstanceDataLazyStrategyRetrievedSuccessfully()
      throws ExecutionException, InterruptedException {
    KeyPair kp = keyPairFuture.get();
    TestDataSupplier instanceDataSupplier = new TestDataSupplier(false);

    // initialize connectionInfoCache after mocks are set up
    LazyRefreshConnectionInfoCache connectionInfoCache =
        new LazyRefreshConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            instanceDataSupplier,
            stubCredentialFactory,
            kp);

    ConnectionMetadata gotMetadata = connectionInfoCache.getConnectionMetadata(300);
    ConnectionMetadata gotMetadata2 = connectionInfoCache.getConnectionMetadata(300);

    // Assert that the underlying ConnectionInfo was retrieved exactly once.
    assertThat(instanceDataSupplier.counter.get()).isEqualTo(1);

    // Assert that the ConnectionInfo fields are added to ConnectionMetadata
    assertThat(gotMetadata.getKeyManagerFactory())
        .isSameInstanceAs(instanceDataSupplier.response.getSslData().getKeyManagerFactory());
    assertThat(gotMetadata.getKeyManagerFactory())
        .isSameInstanceAs(gotMetadata2.getKeyManagerFactory());
  }

  @Test
  public void testLazyStrategy_probesPostgresIamInstanceWithPrincipals() throws Exception {
    KeyPair kp = keyPairFuture.get();
    Map<IpType, List<String>> ips =
        Collections.singletonMap(IpType.PUBLIC, Collections.singletonList("10.1.1.1"));
    ConnectionInfo postgresInfo =
        new ConnectionInfo(
            new InstanceMetadata(
                new CloudSqlInstanceName("project:region:instance"),
                ips,
                null,
                false,
                "",
                false,
                null,
                "POSTGRES_15"),
            new SslData(
                null, KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()), null),
            Instant.now().plus(1, ChronoUnit.HOURS));
    ConnectionInfoRepository repo =
        new StubConnectionInfoRepository() {
          @Override
          public ConnectionInfo getConnectionInfoSync(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              KeyPair keyPair) {
            return postgresInfo;
          }
        };

    AtomicInteger dialCount = new AtomicInteger();
    ByteArrayOutputStream probedBytes = new ByteArrayOutputStream();
    PostgresSocket.ProbeDialer dialer =
        (metadata, timeoutMs) -> {
          dialCount.incrementAndGet();
          return new ProbeRecordingSslSocket(new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0}, probedBytes);
        };

    LazyRefreshConnectionInfoCache cache =
        new LazyRefreshConnectionInfoCache(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("project:region:instance")
                .withAuthType(AuthType.IAM)
                .build(),
            repo,
            stubCredentialFactory,
            kp,
            dialer);

    // 1. Initial refresh without principals skips probe dial
    ConnectionMetadata metadata = cache.getConnectionMetadata(300);
    assertThat(metadata.isPostgres()).isTrue();
    assertThat(dialCount.get()).isEqualTo(0);
    assertThat(probedBytes.toByteArray()).isEmpty();

    // 2. Record principal and force refresh -> sends StartupMessage + Terminate
    cache.recordIamPrincipal("lazy-user@project.iam", "appdb");
    cache.forceRefresh();
    cache.getConnectionMetadata(300);
    assertThat(dialCount.get()).isEqualTo(1);
    byte[] expected = PostgresSocket.buildPostgresStartupPacket("lazy-user@project.iam", "appdb");
    assertThat(probedBytes.toByteArray().length)
        .isEqualTo(expected.length + PostgresSocket.buildTerminatePacket().length);
  }

  private static class ProbeRecordingSslSocket extends SSLSocket {
    private final ByteArrayInputStream in;
    private final ByteArrayOutputStream out;

    ProbeRecordingSslSocket(byte[] serverResp, ByteArrayOutputStream out) {
      this.in = new ByteArrayInputStream(serverResp);
      this.out = out;
    }

    @Override
    public InputStream getInputStream() {
      return in;
    }

    @Override
    public OutputStream getOutputStream() {
      return out;
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return new String[0];
    }

    @Override
    public String[] getEnabledCipherSuites() {
      return new String[0];
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {}

    @Override
    public String[] getSupportedProtocols() {
      return new String[0];
    }

    @Override
    public String[] getEnabledProtocols() {
      return new String[0];
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {}

    @Override
    public SSLSession getSession() {
      return null;
    }

    @Override
    public void addHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener) {}

    @Override
    public void removeHandshakeCompletedListener(
        javax.net.ssl.HandshakeCompletedListener listener) {}

    @Override
    public void startHandshake() {}

    @Override
    public void setUseClientMode(boolean mode) {}

    @Override
    public boolean getUseClientMode() {
      return false;
    }

    @Override
    public void setNeedClientAuth(boolean need) {}

    @Override
    public boolean getNeedClientAuth() {
      return false;
    }

    @Override
    public void setWantClientAuth(boolean want) {}

    @Override
    public boolean getWantClientAuth() {
      return false;
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {}

    @Override
    public boolean getEnableSessionCreation() {
      return false;
    }

    @Override
    public SSLParameters getSSLParameters() {
      return new SSLParameters();
    }

    @Override
    public void setSSLParameters(SSLParameters params) {}
  }
}
