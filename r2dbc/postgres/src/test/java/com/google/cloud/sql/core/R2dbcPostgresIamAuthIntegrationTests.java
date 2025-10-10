/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import static com.google.cloud.sql.core.GcpConnectionFactoryProvider.ENABLE_IAM_AUTH;
import static com.google.cloud.sql.core.GcpConnectionFactoryProvider.IP_TYPES;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

import com.google.common.collect.ImmutableList;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import reactor.core.publisher.Mono;

@RunWith(JUnit4.class)
public class R2dbcPostgresIamAuthIntegrationTests {

  // [START cloud_sql_connector_postgres_r2dbc_iam_auth]
  private static final String CONNECTION_NAME = System.getenv("POSTGRES_CONNECTION_NAME");
  private static final String DB_NAME = System.getenv("POSTGRES_DB");
  private static final String DB_USER = System.getenv("POSTGRES_IAM_USER");
  private static final String IP_TYPE =
      System.getenv("IP_TYPE") == null ? "PUBLIC" : System.getenv("IP_TYPE");
  // [END cloud_sql_connector_postgres_r2dbc_iam_auth]
  private static final ImmutableList<String> requiredEnvVars =
      ImmutableList.of("POSTGRES_USER", "POSTGRES_PASS", "POSTGRES_DB", "POSTGRES_CONNECTION_NAME");
  @Rule public Timeout globalTimeout = new Timeout(80, TimeUnit.SECONDS);

  private ConnectionFactory connectionPool;

  @Before
  public void setUpPool() {
    // Check that required env vars are set
    requiredEnvVars.forEach(
        (varName) ->
            assertWithMessage(
                    "Environment variable '%s' must be set to perform these tests.", varName)
                .that(System.getenv(varName))
                .isNotEmpty());

    // [START cloud_sql_connector_postgres_r2dbc_iam_auth]
    // Set up ConnectionFactoryOptions
    ConnectionFactoryOptions options =
        ConnectionFactoryOptions.builder()
            .option(DRIVER, "gcp")
            .option(PROTOCOL, "postgresql")
            // Password must be set to a nonempty value to bypass driver validation errors
            .option(PASSWORD, "password")
            .option(USER, DB_USER)
            .option(DATABASE, DB_NAME)
            .option(HOST, CONNECTION_NAME)
            .option(IP_TYPES, IP_TYPE)
            .option(ENABLE_IAM_AUTH, true)
            .build();

    // Initialize connection pool
    ConnectionFactory connectionFactory = ConnectionFactories.get(options);
    ConnectionPoolConfiguration configuration =
        ConnectionPoolConfiguration.builder(connectionFactory).build();

    this.connectionPool = new ConnectionPool(configuration);
    // [END cloud_sql_connector_postgres_r2dbc_iam_auth]
  }

  @Test
  public void pooledConnectionTest() {
    List<Instant> rows =
        Mono.from(this.connectionPool.create())
            .flatMapMany(connection -> connection.createStatement("SELECT NOW() as TS").execute())
            .flatMap(result -> result.map((r, meta) -> r.get("TS", Instant.class)))
            .collectList()
            .block();

    assertThat(rows.size()).isEqualTo(1);
  }

  // This test verifies the "enable_iam_authn" parameter works when a URL is used
  @Test
  public void urlPooledConnectionTest() {
    String url =
        String.format(
            "r2dbc:gcp:postgres://%s:%s@%s/%s?ENABLE_IAM_AUTH=true",
            DB_USER, "password", CONNECTION_NAME, DB_NAME);
    ConnectionFactory connectionPool = ConnectionFactories.get(url);

    List<Instant> rows =
        Mono.from(connectionPool.create())
            .flatMapMany(connection -> connection.createStatement("SELECT NOW() as TS").execute())
            .flatMap(result -> result.map((r, meta) -> r.get("TS", Instant.class)))
            .collectList()
            .block();

    assertThat(rows.size()).isEqualTo(1);
  }
}
