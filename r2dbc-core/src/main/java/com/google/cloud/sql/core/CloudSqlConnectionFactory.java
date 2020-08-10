package com.google.cloud.sql.core;

import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import java.util.Properties;
import java.util.function.Function;
import org.reactivestreams.Publisher;

public class CloudSqlConnectionFactory implements ConnectionFactory {
  private Function<ConnectionFactoryOptions, ConnectionFactory> connectionFactoryFactory;
  private ConnectionFactoryOptions.Builder builder;
  private Properties properties;

  /**
   * Creates an instance of ConnectionFactory that pulls and sets host ip before delegating.
   */
  public CloudSqlConnectionFactory(
      Function<ConnectionFactoryOptions, ConnectionFactory> connectionFactoryFactory,
      Builder builder,
      Properties properties) {
    this.connectionFactoryFactory = connectionFactoryFactory;
    this.builder = builder;
    this.properties = properties;
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
    String hostIp = CoreSocketFactory.getHostIp(properties);
    builder.option(HOST, hostIp).option(PORT, CoreSocketFactory.getDefaultServerProxyPort());
    return connectionFactoryFactory.apply(builder.build());
  }
}
