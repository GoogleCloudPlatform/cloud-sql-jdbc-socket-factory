package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import dev.failsafe.RateLimiter;
import java.io.IOException;
import java.security.KeyPair;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class CloudSqlInstanceConcurrencyTest {
  private static final long start = System.currentTimeMillis();

  private static final Logger logger =
      Logger.getLogger(CloudSqlInstanceConcurrencyTest.class.getName());

  static {
    ConsoleHandler handler =
        new ConsoleHandler() {
          {
            setOutputStream(System.out);
            setFormatter(
                new Formatter() {
                  @Override
                  public String format(LogRecord record) {
                    return String.format(
                        "%5d %s (%s): %s\n",
                        record.getMillis() - start,
                        record.getLevel(),
                        threadName(record.getThreadID()),
                        record.getMessage());
                  }

                  private String threadName(long threadID) {
                    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
                    for (Thread t : threadSet) {
                      if (t.getId() == threadID) {
                        return t.getName();
                      }
                    }
                    return "unknown";
                  }
                });
            setLevel(Level.ALL);
          }
        };

    Logger.getLogger("").addHandler(handler);
    Logger l = Logger.getLogger(CloudSqlInstance.class.getName());
    l.setLevel(Level.ALL);
    logger.info("Hello");
  }

  private static class TestDataSupplier implements InstanceDataSupplier {

    private final boolean flakey;

    private AtomicInteger counter = new AtomicInteger();
    private InstanceData response =
        new InstanceData(
            new Metadata(
                ImmutableMap.of(
                    "PUBLIC", "10.1.2.3",
                    "PRIVATE", "10.10.10.10",
                    "PSC", "abcde.12345.us-central1.sql.goog"),
                null),
            new SslData(null, null, null),
            Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));
    private final ListeningScheduledExecutorService executor;

    private TestDataSupplier(boolean flakey, ListeningScheduledExecutorService executor) {
      this.flakey = flakey;
      this.executor = executor;
    }

    @Override
    public InstanceData getInstanceData(
        CloudSqlInstanceName instanceName,
        AccessTokenSupplier accessTokenSupplier,
        AuthType authType,
        ListeningScheduledExecutorService executor,
        ListenableFuture<KeyPair> keyPair)
        throws ExecutionException, InterruptedException {

      ListenableFuture<InstanceData> f =
          executor.submit(
              () -> {
                int c = counter.incrementAndGet();
                Thread.sleep(100);

                if (flakey && c % 2 == 0 && c > 10) {
                  Thread.sleep(500);
                  throw new ExecutionException("Flaky", new Exception());
                }

                return response;
              });

      return f.get();
    }
  }

  private static class TestCredentialFactory implements CredentialFactory, HttpRequestInitializer {

    @Override
    public HttpRequestInitializer create() {
      return this;
    }

    public void initialize(HttpRequest var1) throws IOException {
      // do nothing
    }
  }

  @Test
  public void testCloudSqlInstanceConcurrency() throws Exception {
    // Run the test 10 times to ensure we don't have race conditions
    for (int i = 0; i < 10; i++) {
      runConcurrencyTest();
    }
  }

  @Test
  public void testBasicHappyPath() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture =
        Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = newTestExecutor();
    TestDataSupplier supplier = new TestDataSupplier(false, executor);
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "a:b:c",
            supplier,
            AuthType.PASSWORD,
            new TestCredentialFactory(),
            executor,
            keyPairFuture,
            newRateLimiter());

    assertThat(supplier.counter.get()).isEqualTo(0);
    Thread.sleep(500);
    SslData data = instance.getSslData();
    assertThat(data).isNotNull();
  }

  @Test
  public void testPermanentFailure() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture =
        Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = newTestExecutor();

    InstanceDataSupplier supplier =
        (CloudSqlInstanceName instanceName,
            AccessTokenSupplier accessTokenSupplier,
            AuthType authType,
            ListeningScheduledExecutorService exec,
            ListenableFuture<KeyPair> keyPair) -> {
          ListenableFuture<?> f =
              exec.submit(
                  () -> {
                    throw new RuntimeException("always fails");
                  });
          f.get(); // this will throw an ExecutionException
          return null;
        };

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "a:b:c",
            supplier,
            AuthType.PASSWORD,
            new TestCredentialFactory(),
            executor,
            keyPairFuture,
            newRateLimiter());

    Thread.sleep(500);
    Assert.assertThrows(RuntimeException.class, () -> instance.getSslData());
    // Note: refresh attempts will continue the background. This instance.getSslData() throws an
    // exception after the first refresh attempt fails.
  }

  public void runConcurrencyTest() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture =
        Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = newTestExecutor();
    TestDataSupplier supplier = new TestDataSupplier(true, executor);
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "a:b:c",
            supplier,
            AuthType.PASSWORD,
            new TestCredentialFactory(),
            executor,
            keyPairFuture,
            newRateLimiter());
    assertThat(supplier.counter.get()).isEqualTo(0);

    // Attempt to retrieve data, ensure we wait for success
    ListenableFuture<List<Object>> allData =
        Futures.allAsList(
            executor.submit(instance::getSslData),
            executor.submit(instance::getSslData),
            executor.submit(instance::getSslData));

    List<Object> d = allData.get();
    assertThat(d.get(0)).isNotNull();
    assertThat(d.get(1)).isNotNull();
    assertThat(d.get(2)).isNotNull();
    assertThat(supplier.counter.get()).isEqualTo(1);

    for (int i = 0; i < 10; i++) {
      // Call forceRefresh simultaneously
      ListenableFuture<List<Object>> all =
          Futures.allAsList(
              executor.submit(instance::forceRefresh),
              executor.submit(instance::forceRefresh),
              executor.submit(instance::forceRefresh));
      try {
        all.get();
      } catch (Exception e) {
      }

      ListenableFuture<List<Object>> allData2 =
          Futures.allAsList(
              executor.submit(instance::getSslData),
              executor.submit(instance::getSslData),
              executor.submit(instance::getSslData));
      try {
        allData2.get();
      } catch (Exception e) {
      }
      assertThat(supplier.counter.get()).isEqualTo(2 + i);
      Thread.sleep(100);
    }
  }

  @Test
  public void testForceRefreshDeadlock() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture =
        Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = newTestExecutor();
    TestDataSupplier supplier = new TestDataSupplier(true, executor);
    List<CloudSqlInstance> instances = new ArrayList<>();

    final int instanceCount = 5;

    for (int i = 0; i < instanceCount; i++) {
      instances.add(
          new CloudSqlInstance(
              "a:b:instance" + i,
              supplier,
              AuthType.PASSWORD,
              new TestCredentialFactory(),
              executor,
              keyPairFuture,
              newRateLimiter()));
    }

    assertThat(supplier.counter.get()).isEqualTo(0);

    // Get SSL Data for each instance, forcing the first refresh
    instances.forEach(i -> i.getSslData());

    assertThat(supplier.counter.get()).isEqualTo(instanceCount);

    List<Thread> threads =
        instances.stream()
            .map(
                (inst) -> {
                  Thread t =
                      new Thread(
                          () -> {
                            for (int i = 0; i < 50; i++) {
                              try {
                                inst.forceRefresh();
                                inst.forceRefresh();
                                Thread.yield();
                                inst.forceRefresh();
                                inst.getSslData();
                                Thread.sleep(10);
                              } catch (Exception e) {
                                logger.info("Exception in force refresh loop.");
                              }
                            }
                            logger.info("Done spamming");
                          });
                  t.setName("test-" + inst.getInstanceName());
                  t.start();
                  return t;
                })
            .collect(Collectors.toList());

    for (Thread t : threads) {
      t.join();
    }

    // Check if there is a scheduled future
    int brokenLoop = 0;
    for (CloudSqlInstance inst : instances) {
      if (inst.getNext().isDone() && inst.getCurrent().isDone()) {
        logger.warning("No future scheduled thing for instance " + inst.getInstanceName());
        brokenLoop++;
      }
    }
    assertThat(brokenLoop).isEqualTo(0);
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(8);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    //noinspection UnstableApiUsage
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }

  private RateLimiter<Object> newRateLimiter() {
    return RateLimiter.burstyBuilder(2, Duration.ofMillis(50)).build();
  }
}
