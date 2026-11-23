/*
 * Copyright 2026 Google LLC
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

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.RefreshStrategy;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public class CloudSqlConnectionFactoryTest extends CloudSqlCoreTestingBase {

  private static final String TEST_INSTANCE = "myProject:myRegion:myInstance";

  // The lazy refresh strategy calls the Admin API again only after the cached connection info
  // was invalidated by a forced refresh, so the request count shows whether one happened.
  private final ConnectionConfig config =
      new ConnectionConfig.Builder()
          .withCloudSqlInstance(TEST_INSTANCE)
          .withIpTypes("PRIMARY")
          .withConnectorConfig(
              new ConnectorConfig.Builder().withRefreshStrategy(RefreshStrategy.LAZY).build())
          .build();
  private final AtomicInteger adminApiRequests = new AtomicInteger();
  private ListeningScheduledExecutorService defaultExecutor;
  private InternalConnectorRegistry registry;

  @Before
  public void setUp() throws Exception {
    super.setup();
    defaultExecutor = InternalConnectorRegistry.getDefaultExecutor();
    MockHttpTransport transport = fakeSuccessHttpTransport(Duration.ofSeconds(0));
    MockHttpTransport countingTransport =
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            adminApiRequests.incrementAndGet();
            return transport.buildRequest(method, url);
          }
        };
    registry =
        new InternalConnectorRegistry(
            clientKeyPair,
            new StubConnectionInfoRepositoryFactory(countingTransport),
            stubCredentialFactoryProvider,
            InternalConnectorRegistry.DEFAULT_SERVER_PROXY_PORT,
            5000L,
            defaultExecutor);
  }

  @After
  public void tearDown() {
    registry.shutdown();
    defaultExecutor.shutdownNow();
  }

  @Test
  public void create_forcesRefreshWhenConnectionFails() {
    // For example, the TLS handshake fails after the server CA was rotated.
    R2dbcNonTransientResourceException connectError =
        new R2dbcNonTransientResourceException("PKIX path building failed");
    CloudSqlConnectionFactory factory = newFactory(registry, Mono.error(connectError));

    Throwable thrown =
        assertThrows(
            R2dbcNonTransientResourceException.class, () -> Mono.from(factory.create()).block());
    assertThat(thrown).isSameInstanceAs(connectError);

    int requests = adminApiRequests.get();
    assertThat(requests).isGreaterThan(0);
    registry.getConnectionMetadata(config);
    assertThat(adminApiRequests.get()).isGreaterThan(requests);
  }

  @Test
  public void create_doesNotRefreshWhenConnectionSucceeds() {
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> null);
    CloudSqlConnectionFactory factory = newFactory(registry, Mono.just(connection));

    assertThat(Mono.from(factory.create()).block()).isInstanceOf(CloudSqlConnection.class);

    int requests = adminApiRequests.get();
    registry.getConnectionMetadata(config);
    assertThat(adminApiRequests.get()).isEqualTo(requests);
  }

  @Test
  public void create_reportsConnectionErrorWhenRefreshFails() {
    R2dbcNonTransientResourceException connectError =
        new R2dbcNonTransientResourceException("PKIX path building failed");
    AtomicInteger registryCalls = new AtomicInteger();
    CloudSqlConnectionFactory factory =
        new CloudSqlConnectionFactory(
            config,
            () -> stubProvider(Mono.error(connectError)),
            ConnectionFactoryOptions.builder()) {
          @Override
          InternalConnectorRegistry getConnectorRegistry() {
            // The first call looks up the instance IP, the second one is the forced refresh.
            if (registryCalls.incrementAndGet() > 1) {
              throw new IllegalStateException("ConnectorRegistry was shut down.");
            }
            return registry;
          }
        };

    Throwable thrown =
        assertThrows(
            R2dbcNonTransientResourceException.class, () -> Mono.from(factory.create()).block());
    assertThat(thrown).isSameInstanceAs(connectError);
    assertThat(registryCalls.get()).isEqualTo(2);
  }

  private CloudSqlConnectionFactory newFactory(
      InternalConnectorRegistry registry, Publisher<? extends Connection> connection) {
    return new CloudSqlConnectionFactory(
        config, () -> stubProvider(connection), ConnectionFactoryOptions.builder()) {
      @Override
      InternalConnectorRegistry getConnectorRegistry() {
        return registry;
      }
    };
  }

  private static ConnectionFactoryProvider stubProvider(
      Publisher<? extends Connection> connection) {
    return new ConnectionFactoryProvider() {
      @Override
      public ConnectionFactory create(ConnectionFactoryOptions options) {
        return new ConnectionFactory() {
          @Override
          public Publisher<? extends Connection> create() {
            return connection;
          }

          @Override
          public ConnectionFactoryMetadata getMetadata() {
            return () -> "stub";
          }
        };
      }

      @Override
      public boolean supports(ConnectionFactoryOptions options) {
        return true;
      }

      @Override
      public String getDriver() {
        return "stub";
      }
    };
  }
}
