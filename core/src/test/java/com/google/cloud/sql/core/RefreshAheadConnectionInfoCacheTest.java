/*
 * Copyright 2022 Google LLC
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
import static org.junit.Assert.assertThrows;

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.IpType;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RefreshAheadConnectionInfoCacheTest {

  public static final long TEST_TIMEOUT_MS = 3000;

  public static final long MIN_REFERSH_DELAY_MS = 1;

  private final StubCredentialFactory stubCredentialFactory =
      new StubCredentialFactory("my-token", System.currentTimeMillis() + 3600L);
  private ListeningScheduledExecutorService executorService;
  private ListenableFuture<KeyPair> keyPairFuture;

  private final long RATE_LIMIT_BETWEEN_REQUESTS = 10L;

  @Before
  public void setup() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    this.keyPairFuture = Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    executorService = newTestExecutor();
  }

  @After
  public void teardown() {
    executorService.shutdownNow();
  }

  @Test
  public void testCloudSqlInstanceDataRetrievedSuccessfully() {
    TestDataSupplier instanceDataSupplier = new TestDataSupplier(false);
    // initialize connectionInfoCache after mocks are set up
    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            instanceDataSupplier,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    ConnectionMetadata gotMetadata = connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(gotMetadata.getKeyManagerFactory())
        .isSameInstanceAs(instanceDataSupplier.response.getSslData().getKeyManagerFactory());
    assertThat(instanceDataSupplier.counter.get()).isEqualTo(1);
  }

  @Test
  public void testInstanceFailsOnConnectionError() {

    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            throw new RuntimeException("always fails");
          }
        };

    // initialize connectionInfoCache after mocks are set up
    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS));
    assertThat(ex).hasMessageThat().contains("always fails");
  }

  @Test
  public void testInstanceFailsOnTooLongToRetrieve() {
    PauseCondition cond = new PauseCondition();
    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            // This is never allowed to proceed
            cond.pause();
            throw new RuntimeException("fake read timeout");
          }
        };

    // initialize connectionInfoCache after mocks are set up
    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            100);

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> connectionInfoCache.getConnectionMetadata(2000));
    assertThat(ex).hasMessageThat().contains("No refresh has completed");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    ConnectionInfo connectionInfo = newFutureConnectionInfo();
    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();
    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            int c = refreshCount.get();
            // Allow the first execution to complete immediately.
            // The second execution should pause until signaled.
            if (c == 1) {
              cond.pause();
            }
            refreshCount.incrementAndGet();
            return Futures.immediateFuture(connectionInfo);
          }
        };

    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Force refresh, which will start, but not finish the refresh process.
    connectionInfoCache.forceRefresh();

    // Then immediately getSslData() and assert that the refresh count has not changed.
    // Refresh count hasn't changed because we re-use the existing connection info.
    connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Allow the second refresh operation to complete
    cond.proceed();
    cond.waitForPauseToEnd(1000L);
    cond.waitForCondition(() -> refreshCount.get() >= 2, 1000L);

    // getSslData again, and assert the refresh operation completed.
    connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testCloudSqlInstanceRetriesOnInitialFailures() throws Exception {
    ConnectionInfo connectionInfo = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();

    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            int c = refreshCount.get();
            refreshCount.incrementAndGet();
            if (c == 0) {
              throw new RuntimeException("bad request 0");
            }
            return Futures.immediateFuture(connectionInfo);
          }
        };
    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first connectionInfo that is about to expire
    long until = System.currentTimeMillis() + 3000;
    while (connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS).getKeyManagerFactory()
            != connectionInfo.getSslData().getKeyManagerFactory()
        && System.currentTimeMillis() < until) {
      Thread.sleep(100);
    }
    assertThat(refreshCount.get()).isEqualTo(2);
    assertThat(connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS).getKeyManagerFactory())
        .isEqualTo(connectionInfo.getSslData().getKeyManagerFactory());
  }

  private static ConnectionInfo newFutureConnectionInfo() {
    return newConnectionInfo(1, ChronoUnit.HOURS);
  }

  private static ConnectionInfo newConnectionInfo(long amount, ChronoUnit unit) {
    Map<IpType, List<String>> ips =
        Collections.singletonMap(IpType.PUBLIC, Collections.singletonList("10.1.1.1"));
    try {
      return new ConnectionInfo(
          new InstanceMetadata(
              new CloudSqlInstanceName("project:region:instance"),
              ips,
              null,
              false,
              "",
              false,
              null),
          new SslData(
              null, KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()), null),
          Instant.now().plus(amount, unit));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testCloudSqlRefreshesExpiredData() throws Exception {
    ConnectionInfo initial = newConnectionInfo(2, ChronoUnit.SECONDS);
    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();
    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {

            int c = refreshCount.get();
            ConnectionInfo refreshResult = info;
            switch (c) {
              case 0:
                // refresh 0 should return initial immediately
                refreshResult = initial;
                break;
              case 1:
                // refresh 1 should pause
                refresh1.pause();
                break;
            }
            // refresh 2 and on should return data immediately
            refreshCount.incrementAndGet();
            return Futures.immediateFuture(refreshResult);
          }
        };
    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first data that is about to expire
    ConnectionMetadata d = connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d.getKeyManagerFactory())
        .isSameInstanceAs(initial.getSslData().getKeyManagerFactory());

    // Wait for the connectionInfoCache to expire
    while (Instant.now().isBefore(initial.getExpiration())) {
      Thread.sleep(10);
    }

    // Allow the second refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(1000L);

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(
        () ->
            connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS).getKeyManagerFactory()
                == info.getSslData().getKeyManagerFactory(),
        1000L);
  }

  @Test
  public void testThatForceRefreshBalksWhenAScheduledRefreshIsInProgress() throws Exception {
    // Set expiration 1 minute in the future, so that it will trigger a scheduled refresh
    // and won't expire during this testcase.
    ConnectionInfo expiresInOneMinute = newConnectionInfo(1, ChronoUnit.MINUTES);

    // Set the next refresh info expiration 1 hour in the future.
    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh0 = new PauseCondition();
    final PauseCondition refresh1 = new PauseCondition();

    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            int c = refreshCount.get();
            ConnectionInfo refreshResult = info;
            switch (c) {
              case 0:
                refresh0.pause();
                refreshResult = expiresInOneMinute;
                break;
              case 1:
                refresh1.pause();
                break;
            }
            refreshCount.incrementAndGet();
            return Futures.immediateFuture(refreshResult);
          }
        };
    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    refresh0.proceed();
    refresh0.waitForPauseToEnd(1000);
    refresh0.waitForCondition(() -> refreshCount.get() > 0, 1000);
    // Get the first info that is about to expire
    assertThat(refreshCount.get()).isEqualTo(1);
    ConnectionMetadata d = connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(d.getKeyManagerFactory())
        .isSameInstanceAs(expiresInOneMinute.getSslData().getKeyManagerFactory());

    // Because the info is about to expire, scheduled refresh will begin immediately.
    // Wait until refresh is in progress.
    refresh1.waitForPauseToStart(1000);

    // Then call forceRefresh(), which should balk because a refresh attempt is in progress.
    connectionInfoCache.forceRefresh();

    // Finally, allow the scheduled refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(5000);
    refresh1.waitForCondition(() -> refreshCount.get() > 1, 1000);

    // Now that the InstanceData has expired, this getSslData should pause until new info
    // has been retrieved.

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(
        () ->
            connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS).getKeyManagerFactory()
                == info.getSslData().getKeyManagerFactory(),
        1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testThatForceRefreshBalksWhenAForceRefreshIsInProgress() throws Exception {
    ConnectionInfo initialData = newFutureConnectionInfo();
    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();
    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            int c = refreshCount.get();
            switch (c) {
              case 0:
                refreshCount.incrementAndGet();
                return Futures.immediateFuture(initialData);
              case 1:
                refresh1.pause();
                refreshCount.incrementAndGet();
                return Futures.immediateFuture(info);
              default:
                return Futures.immediateFuture(info);
            }
          }
        };

    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first info that is about to expire
    ConnectionMetadata d = connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d.getKeyManagerFactory())
        .isSameInstanceAs(initialData.getSslData().getKeyManagerFactory());

    // call forceRefresh twice, this should only result in 1 refresh fetch
    connectionInfoCache.forceRefresh();
    connectionInfoCache.forceRefresh();

    // Allow the refresh operation to complete
    refresh1.proceed();

    // Now that the InstanceData has expired, this getSslData should pause until new info
    // has been retrieved.
    refresh1.waitForPauseToEnd(1000);
    refresh1.waitForCondition(() -> refreshCount.get() >= 2, 1000);

    // assert the refresh operation completed exactly once after
    // forceRefresh was called multiple times.
    refresh1.waitForCondition(
        () ->
            connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS).getKeyManagerFactory()
                == info.getSslData().getKeyManagerFactory(),
        1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testRefreshRetriesOnAfterFailedAttempts() throws Exception {
    ConnectionInfo aboutToExpire = newConnectionInfo(10, ChronoUnit.MILLIS);

    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition badRequest1 = new PauseCondition();
    final PauseCondition badRequest2 = new PauseCondition();
    final PauseCondition goodRequest = new PauseCondition();
    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            int c = refreshCount.get();
            switch (c) {
              case 0:
                refreshCount.incrementAndGet();
                return Futures.immediateFuture(aboutToExpire);
              case 1:
                badRequest1.pause();
                refreshCount.incrementAndGet();
                throw new RuntimeException("bad request 1");
              case 2:
                badRequest2.pause();
                refreshCount.incrementAndGet();
                throw new RuntimeException("bad request 2");
              default:
                goodRequest.pause();
                refreshCount.incrementAndGet();
                return Futures.immediateFuture(info);
            }
          }
        };

    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first info that is about to expire
    ConnectionMetadata d = connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d.getKeyManagerFactory())
        .isSameInstanceAs(aboutToExpire.getSslData().getKeyManagerFactory());

    // Don't force a refresh, this should automatically schedule a refresh right away because
    // the token returned in the first request had less than 4 minutes before it expired.

    // Wait for the current InstanceData to actually expire.
    while (Instant.now().isBefore(aboutToExpire.getExpiration())) {
      Thread.sleep(10);
    }

    // Orchestrate the failed attempts

    // Allow the second refresh operation to complete
    badRequest1.proceed();
    badRequest1.waitForPauseToEnd(5000);
    badRequest1.waitForCondition(() -> refreshCount.get() == 2, 2000);

    // Allow the second bad request completes
    badRequest2.proceed();
    badRequest2.waitForCondition(() -> refreshCount.get() == 3, 2000);

    // Allow the final good request to complete
    goodRequest.proceed();
    goodRequest.waitForCondition(() -> refreshCount.get() == 4, 2000);

    // Try getSslData() again, and assert the refresh operation eventually completes.
    goodRequest.waitForCondition(
        () ->
            connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS).getKeyManagerFactory()
                == info.getSslData().getKeyManagerFactory(),
        2000);
  }

  @Test
  public void testGetPreferredIpTypes() {
    SslData sslData = new SslData(null, null, null);
    ConnectionInfo info =
        new ConnectionInfo(
            new InstanceMetadata(
                new CloudSqlInstanceName("project:region:instance"),
                ImmutableMap.of(
                    IpType.PUBLIC, Collections.singletonList("10.1.2.3"),
                    IpType.PRIVATE, Collections.singletonList("10.10.10.10"),
                    IpType.PSC, Collections.singletonList("abcde.12345.us-central1.sql.goog")),
                null,
                false,
                "",
                false,
                null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            refreshCount.incrementAndGet();
            return Futures.immediateFuture(info);
          }
        };

    Map<List<IpType>, String> values = new LinkedHashMap<>();
    values.put(Arrays.asList(IpType.PUBLIC, IpType.PRIVATE), "10.1.2.3");
    values.put(Collections.singletonList(IpType.PUBLIC), "10.1.2.3");
    values.put(Arrays.asList(IpType.PRIVATE, IpType.PUBLIC), "10.10.10.10");
    values.put(Collections.singletonList(IpType.PRIVATE), "10.10.10.10");
    values.put(Collections.singletonList(IpType.PSC), "abcde.12345.us-central1.sql.goog");
    values.put(Collections.singletonList(IpType.SQL_DATA), "10.10.10.10");

    values.forEach(
        (ipTypes, wantsIp) -> {
          // initialize connectionInfoCache after mocks are set up
          RefreshAheadConnectionInfoCache connectionInfoCache =
              new RefreshAheadConnectionInfoCache(
                  new ConnectionConfig.Builder()
                      .withCloudSqlInstance("project:region:instance")
                      .withIpTypes(ipTypes)
                      .build(),
                  connectionInfoRepository,
                  stubCredentialFactory,
                  executorService,
                  keyPairFuture,
                  MIN_REFERSH_DELAY_MS);

          assertThat(
                  connectionInfoCache
                      .getConnectionMetadata(TEST_TIMEOUT_MS)
                      .getPreferredIpAddresses())
              .containsExactly(wantsIp);
        });
  }

  @Test
  public void testGetPreferredIpTypes_SqlDataPreferenceOrder() {
    SslData sslData = new SslData(null, null, null);

    // 1. When PSC and PUBLIC are available (no PRIVATE), prefers PSC
    ConnectionInfo pscAndPublicInfo =
        new ConnectionInfo(
            new InstanceMetadata(
                new CloudSqlInstanceName("project:region:instance"),
                ImmutableMap.of(
                    IpType.PUBLIC, Collections.singletonList("10.1.2.3"),
                    IpType.PSC, Collections.singletonList("abcde.12345.us-central1.sql.goog")),
                null,
                false,
                "",
                false,
                null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));

    RefreshAheadConnectionInfoCache pscCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("project:region:instance")
                .withIpTypes(Collections.singletonList(IpType.SQL_DATA))
                .build(),
            new StubConnectionInfoRepository() {
              @Override
              public ListenableFuture<ConnectionInfo> getConnectionInfo(
                  CloudSqlInstanceName instanceName,
                  AccessTokenSupplier accessTokenSupplier,
                  AuthType authType,
                  ListeningScheduledExecutorService executor,
                  ListenableFuture<KeyPair> keyPair) {
                return Futures.immediateFuture(pscAndPublicInfo);
              }
            },
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    assertThat(pscCache.getConnectionMetadata(TEST_TIMEOUT_MS).getPreferredIpAddresses())
        .containsExactly("abcde.12345.us-central1.sql.goog");

    // 2. When only PUBLIC is available (no PRIVATE or PSC), prefers PUBLIC
    ConnectionInfo publicOnlyInfo =
        new ConnectionInfo(
            new InstanceMetadata(
                new CloudSqlInstanceName("project:region:instance"),
                ImmutableMap.of(IpType.PUBLIC, Collections.singletonList("10.1.2.3")),
                null,
                false,
                "",
                false,
                null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));

    RefreshAheadConnectionInfoCache publicCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("project:region:instance")
                .withIpTypes(Collections.singletonList(IpType.SQL_DATA))
                .build(),
            new StubConnectionInfoRepository() {
              @Override
              public ListenableFuture<ConnectionInfo> getConnectionInfo(
                  CloudSqlInstanceName instanceName,
                  AccessTokenSupplier accessTokenSupplier,
                  AuthType authType,
                  ListeningScheduledExecutorService executor,
                  ListenableFuture<KeyPair> keyPair) {
                return Futures.immediateFuture(publicOnlyInfo);
              }
            },
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    assertThat(publicCache.getConnectionMetadata(TEST_TIMEOUT_MS).getPreferredIpAddresses())
        .containsExactly("10.1.2.3");
  }

  @Test
  public void testGetPreferredIpTypesThrowsException() {
    SslData sslData = new SslData(null, null, null);
    ConnectionInfo info =
        new ConnectionInfo(
            new InstanceMetadata(
                new CloudSqlInstanceName("project:region:instance"),
                ImmutableMap.of(IpType.PUBLIC, Collections.singletonList("10.1.2.3")),
                null,
                false,
                "",
                false,
                null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            refreshCount.incrementAndGet();
            return Futures.immediateFuture(info);
          }
        };

    // initialize connectionInfoCache after mocks are set up
    RefreshAheadConnectionInfoCache connectionInfoCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("project:region:instance")
                .withIpTypes(Collections.singletonList(IpType.PRIVATE))
                .build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);
    assertThrows(
        IllegalArgumentException.class,
        () -> connectionInfoCache.getConnectionMetadata(TEST_TIMEOUT_MS));
  }

  @Test
  public void testClosedCloudSqlInstanceDataThrowsException() throws Exception {
    TestDataSupplier instanceDataSupplier = new TestDataSupplier(false);
    // initialize instance after mocks are set up
    RefreshAheadConnectionInfoCache instance =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            instanceDataSupplier,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);
    instance.close();

    assertThrows(
        IllegalStateException.class, () -> instance.getConnectionMetadata(TEST_TIMEOUT_MS));
    assertThrows(IllegalStateException.class, () -> instance.forceRefresh());
  }

  @Test
  public void testClosedCloudSqlInstanceDataStopsRefreshTasks() throws Exception {
    ConnectionInfo initialData =
        new ConnectionInfo(
            null, new SslData(null, null, null), Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh0 = new PauseCondition();

    ConnectionInfoRepository connectionInfoRepository =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            int c = refreshCount.get();
            if (c == 0) {
              refresh0.pause();
            }
            refreshCount.incrementAndGet();
            return Futures.immediateFuture(initialData);
          }
        };
    RefreshAheadConnectionInfoCache instance =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            connectionInfoRepository,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    // Wait for the first refresh attempt to complete.
    refresh0.proceed();
    refresh0.waitForPauseToEnd(TEST_TIMEOUT_MS);

    // Assert that refresh gets instance data before it is closed
    refresh0.waitForCondition(() -> refreshCount.get() == 1, TEST_TIMEOUT_MS);

    // Assert that the next refresh task is scheduled in the future
    assertThat(instance.getRefreshStrategy().getNext().isDone()).isFalse();

    // Close the instance
    instance.close();

    // Assert that the next refresh task is canceled
    assertThat(instance.getRefreshStrategy().getNext().isDone()).isTrue();
    assertThat(instance.getRefreshStrategy().getNext().isCancelled()).isTrue();
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }

  @Test
  public void testRefreshAhead_probesPostgresIamAndSkipsNonPostgres() throws Exception {
    ConnectionInfo pgInfo = newConnectionInfoWithVersion("POSTGRES_15");
    ConnectionInfo mysqlInfo = newConnectionInfoWithVersion("MYSQL_8_0");

    AtomicInteger dialCount = new AtomicInteger();
    CountDownLatch probeLatch = new CountDownLatch(1);
    ByteArrayOutputStream probedBytes = new ByteArrayOutputStream();
    PostgresSocket.ProbeDialer recordingDialer =
        (metadata, timeoutMs) -> {
          dialCount.incrementAndGet();
          return new SSLSocket() {
            private final InputStream in =
                new ByteArrayInputStream(new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0});

            @Override
            public InputStream getInputStream() {
              return in;
            }

            @Override
            public OutputStream getOutputStream() {
              return probedBytes;
            }

            @Override
            public void close() {
              probeLatch.countDown();
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
            public void addHandshakeCompletedListener(
                javax.net.ssl.HandshakeCompletedListener listener) {}

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
          };
        };

    ConnectionInfoRepository pgRepo =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            return Futures.immediateFuture(pgInfo);
          }
        };

    RefreshAheadConnectionInfoCache pgCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("project:region:instance")
                .withAuthType(AuthType.IAM)
                .build(),
            pgRepo,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            0,
            recordingDialer);

    // 1. Initial refresh without principals skips probe dial
    ConnectionMetadata pgMetadata = pgCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(pgMetadata.isPostgres()).isTrue();
    assertThat(dialCount.get()).isEqualTo(0);
    assertThat(probedBytes.toByteArray()).isEmpty();

    // 2. Record principal and force refresh -> sends StartupMessage + Terminate
    pgCache.recordIamPrincipal("refresh-user@iam", "pgdb");
    pgCache.forceRefresh();
    assertThat(probeLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    pgCache.getConnectionMetadata(TEST_TIMEOUT_MS);
    assertThat(dialCount.get()).isEqualTo(1);
    byte[] expected = PostgresSocket.buildPostgresStartupPacket("refresh-user@iam", "pgdb");
    assertThat(probedBytes.toByteArray().length)
        .isEqualTo(expected.length + PostgresSocket.buildTerminatePacket().length);

    pgCache.close();

    // 3. Refresh where probeDialer throws IOException still succeeds and returns metadata
    AtomicInteger failedDialAttempts = new AtomicInteger();
    CountDownLatch failedProbeLatch = new CountDownLatch(1);
    PostgresSocket.ProbeDialer failingDialer =
        (metadata, timeoutMs) -> {
          failedDialAttempts.incrementAndGet();
          failedProbeLatch.countDown();
          throw new IOException("simulated transient probe failure");
        };
    RefreshAheadConnectionInfoCache failingProbeCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("project:region:instance")
                .withAuthType(AuthType.IAM)
                .build(),
            pgRepo,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            0,
            failingDialer);
    failingProbeCache.recordIamPrincipal("fail-user@iam", "pgdb");
    failingProbeCache.forceRefresh();
    assertThat(failedProbeLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(failingProbeCache.getConnectionMetadata(TEST_TIMEOUT_MS)).isNotNull();
    assertThat(failedDialAttempts.get()).isEqualTo(1);
    failingProbeCache.close();

    // 4. MySQL + AuthType.IAM must NOT invoke probeDialer even when a principal is recorded
    int dialsBeforeMysql = dialCount.get();
    ConnectionInfoRepository mysqlRepo =
        new StubConnectionInfoRepository() {
          @Override
          public ListenableFuture<ConnectionInfo> getConnectionInfo(
              CloudSqlInstanceName instanceName,
              AccessTokenSupplier accessTokenSupplier,
              AuthType authType,
              ListeningScheduledExecutorService executor,
              ListenableFuture<KeyPair> keyPair) {
            return Futures.immediateFuture(mysqlInfo);
          }
        };
    RefreshAheadConnectionInfoCache mysqlCache =
        new RefreshAheadConnectionInfoCache(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("project:region:instance")
                .withAuthType(AuthType.IAM)
                .build(),
            mysqlRepo,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            0,
            recordingDialer);
    mysqlCache.recordIamPrincipal("mysql-user@iam", "mysqldb");
    mysqlCache.forceRefresh();
    assertThat(mysqlCache.getConnectionMetadata(TEST_TIMEOUT_MS).isPostgres()).isFalse();
    assertThat(dialCount.get()).isEqualTo(dialsBeforeMysql);
    mysqlCache.close();
  }

  private static ConnectionInfo newConnectionInfoWithVersion(String databaseVersion) {
    Map<IpType, List<String>> ips =
        Collections.singletonMap(IpType.PUBLIC, Collections.singletonList("10.1.1.1"));
    try {
      return new ConnectionInfo(
          new InstanceMetadata(
              new CloudSqlInstanceName("project:region:instance"),
              ips,
              null,
              false,
              "",
              false,
              null,
              databaseVersion),
          new SslData(
              null, KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()), null),
          Instant.now().plus(1, ChronoUnit.HOURS));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
