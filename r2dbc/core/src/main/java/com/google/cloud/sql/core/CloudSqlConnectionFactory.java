/*
 * Copyright 2020 Google Inc.
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

import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;

import com.google.common.annotations.VisibleForTesting;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** {@link ConnectionFactory} for accessing Cloud SQL instances via R2DBC protocol. */
public class CloudSqlConnectionFactory implements ConnectionFactory {

  public static final int SERVER_PROXY_PORT = 3307;
  private final Supplier<ConnectionFactoryProvider> supplier;
  private final ConnectionFactoryOptions.Builder builder;
  private final ConnectionConfig config;

  /** Creates an instance of ConnectionFactory that pulls and sets host ip before delegating. */
  public CloudSqlConnectionFactory(
      ConnectionConfig config,
      Supplier<ConnectionFactoryProvider> supplier,
      ConnectionFactoryOptions.Builder builder) {
    this.config = config;
    this.supplier = supplier;
    this.builder = builder;
  }

  @Override
  public Publisher<? extends Connection> create() {
    String hostIp =
        getConnectorRegistry().getConnectionMetadata(config).getPreferredIpAddresses().get(0);
    builder.option(HOST, hostIp).option(PORT, SERVER_PROXY_PORT);
    return Mono.from(supplier.get().create(builder.build()).create())
        .onErrorResume(
            e ->
                // Force refresh the connection info when the connection fails, for example
                // because the server CA was rotated, so that the next attempt uses fresh data.
                // The JDBC connector does the same when a socket connection fails.
                Mono.fromRunnable(() -> getConnectorRegistry().forceRefresh(config))
                    // Execute in a default scheduler to prevent blocking.
                    .subscribeOn(Schedulers.boundedElastic())
                    // Report the connection error, not a refresh error.
                    .onErrorResume(refreshError -> Mono.empty())
                    .then(Mono.error(e)))
        .map(c -> new CloudSqlConnection(config, c));
  }

  @Override
  public ConnectionFactoryMetadata getMetadata() {
    String hostIp =
        getConnectorRegistry().getConnectionMetadata(config).getPreferredIpAddresses().get(0);
    builder.option(HOST, hostIp).option(PORT, SERVER_PROXY_PORT);
    return supplier.get().create(builder.build()).getMetadata();
  }

  @VisibleForTesting
  InternalConnectorRegistry getConnectorRegistry() {
    return InternalConnectorRegistry.getInstance();
  }
}
