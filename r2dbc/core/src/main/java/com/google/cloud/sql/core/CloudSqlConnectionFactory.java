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
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import java.util.function.Function;
import org.reactivestreams.Publisher;

public class CloudSqlConnectionFactory implements ConnectionFactory {

  private Function<ConnectionFactoryOptions, ConnectionFactory> connectionFactoryFactory;
  private ConnectionFactoryOptions.Builder builder;
  private String csqlHostName;

  /**
   * Creates an instance of ConnectionFactory that pulls and sets host ip before delegating.
   */
  public CloudSqlConnectionFactory(
      Function<ConnectionFactoryOptions, ConnectionFactory> connectionFactoryFactory,
      Builder builder,
      String csqlHostName) {
    this.connectionFactoryFactory = connectionFactoryFactory;
    this.builder = builder;
    this.csqlHostName = csqlHostName;
  }

  @Override
  public Publisher<? extends Connection> create() {
    return getConnectionFactory().create();
  }

  @Override
  public ConnectionFactoryMetadata getMetadata() {
    return getConnectionFactory().getMetadata();
  }

  private ConnectionFactory getConnectionFactory() {
    String hostIp = CoreSocketFactory.getHostIp(csqlHostName);
    builder.option(HOST, hostIp).option(PORT, CoreSocketFactory.getDefaultServerProxyPort());
    return connectionFactoryFactory.apply(builder.build());
  }
}
