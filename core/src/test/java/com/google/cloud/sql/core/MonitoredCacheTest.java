/*
 * Copyright 2025 Google LLC
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

import com.google.cloud.sql.ConnectorConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.Timer;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

public class MonitoredCacheTest {
  private static final Timer timer = new Timer(true);

  @AfterClass
  public static void afterClass() {
    timer.cancel();
  }

  @Test
  public void testMonitoredCacheHoldsSocketsWithDomainName() {
    CloudSqlInstanceName name = new CloudSqlInstanceName("proj:reg:inst", "db.example.com");
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .build();
    MockCache mockCache = new MockCache(config);

    MonitoredCache cache = new MonitoredCache(mockCache, timer, connectionConfig -> name);
    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);
    Assert.assertEquals("1 socket in cache", 1, cache.getOpenSocketCount());
    cache.close();
    Assert.assertTrue("socket closed", socket.closed);
  }

  @Test
  public void testMonitoredCachePurgesClosedSockets() throws InterruptedException {
    CloudSqlInstanceName name = new CloudSqlInstanceName("proj:reg:inst", "db.example.com");
    // Purge sockets every 10ms.
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(Duration.ofMillis(10)).build())
            .build();
    MockCache mockCache = new MockCache(config);

    MonitoredCache cache = new MonitoredCache(mockCache, timer, connectionConfig -> name);
    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);
    Assert.assertEquals("1 socket in cache", 1, cache.getOpenSocketCount());
    socket.close();
    Thread.sleep(20);
    Assert.assertEquals("0 socket in cache", 0, cache.getOpenSocketCount());
  }

  @Test
  public void testMonitoredCacheWithoutDomainNameIgnoresSockets() {
    CloudSqlInstanceName name = new CloudSqlInstanceName("proj:reg:inst");
    ConnectionConfig config =
        new ConnectionConfig.Builder().withCloudSqlInstance("proj:reg:inst").build();
    MockCache mockCache = new MockCache(config);

    MonitoredCache cache = new MonitoredCache(mockCache, timer, connectionConfig -> name);
    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);
    Assert.assertEquals("0 socket in cache", 0, cache.getOpenSocketCount());
  }

  private static class MockSslSocket extends SSLSocket {
    boolean closed;

    @Override
    public synchronized boolean isClosed() {
      return closed;
    }

    @Override
    public synchronized void close() {
      this.closed = true;
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
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {}

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {}

    @Override
    public void startHandshake() throws IOException {}

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
  }

  private static class MockCache implements ConnectionInfoCache {
    private final ConnectionConfig config;

    MockCache(ConnectionConfig config) {
      this.config = config;
    }

    @Override
    public ConnectionMetadata getConnectionMetadata(long timeoutMs) {
      return null;
    }

    @Override
    public void forceRefresh() {}

    @Override
    public void refreshIfExpired() {}

    @Override
    public void close() {}

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public ConnectionConfig getConfig() {
      return config;
    }
  }
}
