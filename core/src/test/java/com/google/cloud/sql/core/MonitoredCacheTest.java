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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.cloud.sql.ConnectorConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import javax.naming.NameNotFoundException;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
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

    // Wait for the MonitoredCache task to clean up the socket, up to 500ms.
    for (int i = 0; cache.getOpenSocketCount() > 0 && i < 10; i++) {
      Thread.sleep(50);
    }

    // Ensure that the socket was removed from the cache.
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

  private GoogleJsonResponseException createGoogleJsonResponseException(int statusCode) {
    HttpHeaders headers = new HttpHeaders();
    GoogleJsonResponseException.Builder builder =
        new GoogleJsonResponseException.Builder(statusCode, "Error", headers);
    return new GoogleJsonResponseException(builder, null);
  }

  @Test
  public void testMonitoredCache_TransientDnsError_DoesNotClose() throws InterruptedException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(Duration.ofMillis(10)).build())
            .build();
    MockCache mockCache = new MockCache(config);

    MonitoredCache cache =
        new MonitoredCache(
            mockCache,
            timer,
            connectionConfig -> {
              throw new IllegalArgumentException(
                  "transient", new NameNotFoundException("not found"));
            });

    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);

    Thread.sleep(100);

    Assert.assertFalse("Cache should not be closed on transient error", mockCache.isClosed());
    Assert.assertFalse("Socket should not be closed on transient error", socket.closed);
    cache.close();
  }

  @Test
  public void testMonitoredCache_NonTransientDnsError_Closes() throws InterruptedException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(Duration.ofMillis(10)).build())
            .build();
    MockCache mockCache = new MockCache(config);

    MonitoredCache cache =
        new MonitoredCache(
            mockCache,
            timer,
            connectionConfig -> {
              throw new IllegalArgumentException("non-transient");
            });

    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);

    for (int i = 0; !mockCache.isClosed() && i < 10; i++) {
      Thread.sleep(50);
    }

    Assert.assertTrue("Cache should be closed on non-transient error", mockCache.isClosed());
    Assert.assertTrue("Socket should be closed on non-transient error", socket.closed);
  }

  @Test
  public void testMonitoredCache_GoogleJsonResponseException_403_Closes()
      throws InterruptedException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(Duration.ofMillis(10)).build())
            .build();
    MockCache mockCache = new MockCache(config);

    GoogleJsonResponseException gErr = createGoogleJsonResponseException(403);
    MonitoredCache cache =
        new MonitoredCache(
            mockCache,
            timer,
            connectionConfig -> {
              throw new RuntimeException("wrapped", gErr);
            });

    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);

    for (int i = 0; !mockCache.isClosed() && i < 10; i++) {
      Thread.sleep(50);
    }

    Assert.assertTrue("Cache should be closed on 403", mockCache.isClosed());
    Assert.assertTrue("Socket should be closed on 403", socket.closed);
  }

  @Test
  public void testMonitoredCache_GoogleJsonResponseException_404_Closes()
      throws InterruptedException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(Duration.ofMillis(10)).build())
            .build();
    MockCache mockCache = new MockCache(config);

    GoogleJsonResponseException gErr = createGoogleJsonResponseException(404);
    MonitoredCache cache =
        new MonitoredCache(
            mockCache,
            timer,
            connectionConfig -> {
              throw new RuntimeException("wrapped", gErr);
            });

    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);

    for (int i = 0; !mockCache.isClosed() && i < 10; i++) {
      Thread.sleep(50);
    }

    Assert.assertTrue("Cache should be closed on 404", mockCache.isClosed());
    Assert.assertTrue("Socket should be closed on 404", socket.closed);
  }

  @Test
  public void testMonitoredCache_GoogleJsonResponseException_500_DoesNotClose()
      throws InterruptedException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(Duration.ofMillis(10)).build())
            .build();
    MockCache mockCache = new MockCache(config);

    GoogleJsonResponseException gErr = createGoogleJsonResponseException(500);
    MonitoredCache cache =
        new MonitoredCache(
            mockCache,
            timer,
            connectionConfig -> {
              throw new RuntimeException("wrapped", gErr);
            });

    MockSslSocket socket = new MockSslSocket();
    cache.addSocket(socket);

    Thread.sleep(100);

    Assert.assertFalse("Cache should not be closed on 500", mockCache.isClosed());
    Assert.assertFalse("Socket should not be closed on 500", socket.closed);
    cache.close();
  }

  private static class SpyTimer extends Timer {
    boolean scheduled;

    @Override
    public void schedule(TimerTask task, long delay, long period) {
      this.scheduled = true;
    }
  }

  @Test
  public void testMonitoredCache_OptimizationForImmutableNames_CustomDns() {
    CloudSqlInstanceName name = new CloudSqlInstanceName("proj:reg:inst", "db.example.com");
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("db.example.com")
            .build();
    MockCache mockCache = new MockCache(config);
    SpyTimer spyTimer = new SpyTimer();

    MonitoredCache cache = new MonitoredCache(mockCache, spyTimer, connectionConfig -> name);
    Assert.assertTrue("Timer should be scheduled for custom DNS", spyTimer.scheduled);
  }

  @Test
  public void testMonitoredCache_OptimizationForImmutableNames_ImmutableInstanceDns() {
    CloudSqlInstanceName name =
        new CloudSqlInstanceName(
            "proj:reg:inst", "0123456789ab.fedcba9876543.us-central1.sql-psc.goog");
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("0123456789ab.fedcba9876543.us-central1.sql-psc.goog")
            .build();
    MockCache mockCache = new MockCache(config);
    SpyTimer spyTimer = new SpyTimer();

    MonitoredCache cache = new MonitoredCache(mockCache, spyTimer, connectionConfig -> name);
    Assert.assertFalse(
        "Timer should NOT be scheduled for immutable instance DNS", spyTimer.scheduled);
  }

  @Test
  public void testMonitoredCache_OptimizationForImmutableNames_MutableGlobalDns() {
    CloudSqlInstanceName name =
        new CloudSqlInstanceName("proj:reg:inst", "0123456789ab.fedcba9876543.global.sql-psc.goog");
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("proj:reg:inst")
            .withDomainName("0123456789ab.fedcba9876543.global.sql-psc.goog")
            .build();
    MockCache mockCache = new MockCache(config);
    SpyTimer spyTimer = new SpyTimer();

    MonitoredCache cache = new MonitoredCache(mockCache, spyTimer, connectionConfig -> name);
    Assert.assertTrue("Timer should be scheduled for mutable global DNS", spyTimer.scheduled);
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
    boolean closed;

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
    public void close() {
      this.closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    @Override
    public ConnectionConfig getConfig() {
      return config;
    }
  }
}
