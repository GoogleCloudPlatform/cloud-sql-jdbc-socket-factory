/*
 * Copyright 2020 Google Inc. All Rights Reserved.
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

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import reactor.util.annotation.NonNull;

/** {@link ConnectionFactory} for accessing Cloud SQL instances via R2DBC protocol. */
public class CloudSqlConnectionFactory implements ConnectionFactory {

  public static final int SERVER_PROXY_PORT = 3307;
  private final Supplier<ConnectionFactoryProvider> supplier;
  private final ConnectionFactoryOptions.Builder builder;
  private final String hostname;
  private final String ipTypes;
  private final List<String> delegates;

  /** Creates an instance of ConnectionFactory that pulls and sets host ip before delegating. */
  public CloudSqlConnectionFactory(
      Supplier<ConnectionFactoryProvider> supplier,
      String ipTypes,
      List<String> delegates,
      ConnectionFactoryOptions.Builder builder,
      String hostname) {
    this.supplier = supplier;
    this.ipTypes = ipTypes;
    this.builder = builder;
    this.hostname = hostname;
    this.delegates = delegates;
  }

  @Override
  @NonNull
  public Publisher<? extends Connection> create() {
    try {
      String hostIp = CoreSocketFactory.getHostIp(hostname, ipTypes, delegates);
      builder.option(HOST, hostIp).option(PORT, SERVER_PROXY_PORT);
      return supplier.get().create(builder.build()).create();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @NonNull
  public ConnectionFactoryMetadata getMetadata() {
    try {
      String hostIp = CoreSocketFactory.getHostIp(hostname, ipTypes, delegates);
      builder.option(HOST, hostIp).option(PORT, SERVER_PROXY_PORT);
      return supplier.get().create(builder.build()).getMetadata();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
