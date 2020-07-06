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

package com.google.cloud.sql.r2dbc;

import java.util.Properties;
import java.util.function.Function;

import com.google.cloud.sql.core.CoreSocketFactory;
import com.google.cloud.sql.core.SslData;
import dev.miku.r2dbc.mysql.MySqlConnectionFactoryProvider;
import dev.miku.r2dbc.mysql.constant.SslMode;
import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.postgresql.client.SSLMode;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import io.r2dbc.spi.Option;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * {@link ConnectionFactoryProvider} for proxied access to GCP Postgres and MySQL instances.
 */
public class GcpConnectionFactoryProvider implements ConnectionFactoryProvider {

	private static final boolean MYSQL_AVAILABLE = isClassPresent("dev.miku.r2dbc.mysql.MySqlConnectionFactoryProvider");

	private static final boolean POSTGRES_AVAILABLE = isClassPresent("io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider");

	/**
	 * MySQL driver option value.
	 */
	public static final String MYSQL_DRIVER = "mysql";

	/**
	 * Postgres driver option value.
	 */
	public static final String POSTGRESQL_DRIVER = "postgresql";

	/**
	 * Legacy postgres driver option value.
	 */
	public static final String LEGACY_POSTGRESQL_DRIVER = "postgres";

	/**
	 * Option to configure the {@code proxyPort} if {@code port} is not configured.
	 */
	public static final Option<Object> PROXY_PORT = Option.valueOf("proxyPort");

	@Override
	public ConnectionFactory create(ConnectionFactoryOptions connectionFactoryOptions) {

		String connectionName = connectionFactoryOptions.getRequiredValue(HOST);
		String protocol = connectionFactoryOptions.getRequiredValue(PROTOCOL);
		int port = getPort(connectionFactoryOptions);

		Properties properties = new Properties();
		properties.put(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY, connectionName);

		if (isMySql(protocol)) {

			if (!MYSQL_AVAILABLE) {
				throw new IllegalStateException("r2dbc-mysql is not on the class path");
			}

			return createMySqlConnectionFactory(connectionFactoryOptions.mutate()
					.option(DRIVER, MYSQL_DRIVER)
					.option(PORT, port), properties);
		}

		if (isPostgres(protocol)) {

			if (!POSTGRES_AVAILABLE) {
				throw new IllegalStateException("r2dbc-postgresql is not on the class path");
			}

			return createPostgresConnectionFactory(connectionFactoryOptions.mutate()
					.option(DRIVER, POSTGRESQL_DRIVER)
					.option(PORT, port), properties);
		}

		throw new UnsupportedOperationException("Cannot create ConnectionFactory " + connectionFactoryOptions);
	}

	private static int getPort(ConnectionFactoryOptions connectionFactoryOptions) {

		if (connectionFactoryOptions.hasOption(PORT)) {
			return connectionFactoryOptions.getRequiredValue(PORT);
		}

		if (connectionFactoryOptions.hasOption(PROXY_PORT)) {
			Object proxyPort = connectionFactoryOptions.getRequiredValue(PROXY_PORT);

			if (proxyPort instanceof Integer) {
				return (Integer) proxyPort;
			}

			return Integer.parseInt(proxyPort.toString());
		}

		return 3307;
	}

	private ConnectionFactory createMySqlConnectionFactory(Builder optionBuilder, Properties properties) {

		// precompute SSL Data to avoid blocking calls during the connect phase.
		CoreSocketFactory.getSslData(properties);

		String socket = CoreSocketFactory.getUnixSocketArg(properties);

		if (socket != null) {
			optionBuilder.option(MySqlConnectionFactoryProvider.UNIX_SOCKET, socket);
		}
		else {

			String hostIp = CoreSocketFactory.getHostIp(properties);
			optionBuilder.option(HOST, hostIp).option(PORT, 3307);

			Function<SslContextBuilder, SslContextBuilder> customizer = sslContextBuilder -> {

				SslData sslData = CoreSocketFactory.getSslData(properties);

				sslContextBuilder.keyManager(sslData.getKeyManagerFactory());
				sslContextBuilder.trustManager(sslData.getTrustManagerFactory());
				sslContextBuilder.protocols("TLSv1.2");

				return sslContextBuilder;
			};

			optionBuilder
					.option(MySqlConnectionFactoryProvider.SSL_CONTEXT_BUILDER_CUSTOMIZER, customizer)
					.option(MySqlConnectionFactoryProvider.SSL_MODE, SslMode.TUNNEL.name())
					.option(MySqlConnectionFactoryProvider.TCP_NODELAY, true)
					.option(MySqlConnectionFactoryProvider.TCP_KEEPALIVE, true);
		}

		return new MySqlConnectionFactoryProvider().create(optionBuilder.build());
	}

	private ConnectionFactory createPostgresConnectionFactory(Builder optionBuilder, Properties properties) {

		// precompute SSL Data to avoid blocking calls during the connect phase.
		CoreSocketFactory.getSslData(properties);

		String socket = CoreSocketFactory.getUnixSocketArg(properties);

		if (socket != null) {
			optionBuilder.option(PostgresqlConnectionFactoryProvider.SOCKET, socket);
		}
		else {

			String hostIp = CoreSocketFactory.getHostIp(properties);
			optionBuilder.option(HOST, hostIp).option(PORT, 3307);

			Function<SslContextBuilder, SslContextBuilder> customizer = sslContextBuilder -> {

				SslData sslData = CoreSocketFactory.getSslData(properties);

				sslContextBuilder.keyManager(sslData.getKeyManagerFactory());
				sslContextBuilder.trustManager(sslData.getTrustManagerFactory());
				sslContextBuilder.protocols("TLSv1.2");

				return sslContextBuilder;
			};

			optionBuilder
					.option(PostgresqlConnectionFactoryProvider.SSL_CONTEXT_BUILDER_CUSTOMIZER, customizer)
					.option(PostgresqlConnectionFactoryProvider.SSL_MODE, SSLMode.TUNNEL)
					.option(PostgresqlConnectionFactoryProvider.TCP_NODELAY, true)
					.option(PostgresqlConnectionFactoryProvider.TCP_KEEPALIVE, true);
		}

		return new PostgresqlConnectionFactoryProvider().create(optionBuilder.build());
	}

	@Override
	public boolean supports(ConnectionFactoryOptions connectionFactoryOptions) {

		String driver = connectionFactoryOptions.getValue(DRIVER);
		String protocol = connectionFactoryOptions.getValue(PROTOCOL);
		return driver != null && protocol != null && driver
				.equals(getDriver()) && (isMySql(protocol) || isPostgres(protocol));
	}

	private boolean isPostgres(String driver) {
		return driver.equals(POSTGRESQL_DRIVER) || driver
				.equals(LEGACY_POSTGRESQL_DRIVER);
	}

	private boolean isMySql(String driver) {
		return driver.equals(MYSQL_DRIVER);
	}

	@Override
	public String getDriver() {
		return "gcp";
	}

	private static boolean isClassPresent(String className) {
		try {
			return Class
					.forName(className, false, GcpConnectionFactoryProvider.class
							.getClassLoader()) != null;
		}
		catch (Exception e) {
			return false;
		}
	}
}
