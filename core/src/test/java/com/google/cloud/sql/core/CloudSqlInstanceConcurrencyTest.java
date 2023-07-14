package com.google.cloud.sql.core;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.security.KeyPair;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class CloudSqlInstanceConcurrencyTest {

  private static final Logger logger = Logger.getLogger(
      CloudSqlInstanceConcurrencyTest.class.getName());

  static {
    ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter(new SimpleFormatter());
    handler.setLevel(Level.ALL);
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

    private TestDataSupplier(boolean flakey) {
      this.flakey = flakey;
    }


    @Override
    public InstanceData getInstanceData(CloudSqlInstanceName instanceName,
        AccessTokenSupplier accessTokenSupplier, AuthType authType,
        ListeningScheduledExecutorService executor, ListenableFuture<KeyPair> keyPair)
        throws ExecutionException, InterruptedException {
      int c = counter.incrementAndGet();
      Thread.sleep(100);

      if (flakey && c % 2 == 0) {
        throw new ExecutionException("Flaky", new Exception());
      }

      return response;
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
    // for (int i = 0; i < 10; i++) {
      runConcurrencyTest();
    // }
  }


  @Test
  public void testBasicHappyPath() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture = Futures.immediateFuture(
        mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = newTestExecutor();
    TestDataSupplier supplier = new TestDataSupplier(false);
    CloudSqlInstance instance = new CloudSqlInstance("a:b:c", supplier, AuthType.PASSWORD,
        new TestCredentialFactory(), executor, keyPairFuture);

    assertThat(supplier.counter.get()).isEqualTo(0);
    Thread.sleep(500);
    SslData data = instance.getSslData();
    assertThat(data).isNotNull();
  }

  public void runConcurrencyTest() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture = Futures.immediateFuture(
        mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = newTestExecutor();
    TestDataSupplier supplier = new TestDataSupplier(true);
    CloudSqlInstance instance = new CloudSqlInstance("a:b:c", supplier, AuthType.PASSWORD,
        new TestCredentialFactory(), executor, keyPairFuture);
    assertThat(supplier.counter.get()).isEqualTo(0);

    // Attempt to retrieve data, ensure we wait for success
    ListenableFuture<List<Object>> allData = Futures.allAsList(
        executor.submit(instance::getSslData),
        executor.submit(instance::getSslData),
        executor.submit(instance::getSslData));

    List<Object> d = allData.get();
    assertThat(d.get(0)).isNotNull();
    assertThat(d.get(1)).isNotNull();
    assertThat(d.get(2)).isNotNull();
    assertThat(supplier.counter.get()).isEqualTo(1);

    // Call forceRefresh simultaneously
    ListenableFuture<List<Object>> all = Futures.allAsList(
        executor.submit(instance::forceRefresh),
        executor.submit(instance::forceRefresh),
        executor.submit(instance::forceRefresh));
    all.get();

    ListenableFuture<List<Object>> allData2 = Futures.allAsList(
        executor.submit(instance::getSslData),
        executor.submit(instance::getSslData),
        executor.submit(instance::getSslData));
    allData2.get();
    assertThat(supplier.counter.get()).isEqualTo(2);

  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    //noinspection UnstableApiUsage
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }
}
