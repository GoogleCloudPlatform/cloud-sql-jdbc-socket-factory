/*
 * Copyright 2022 Google LLC
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
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
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
public class R2dbcMysqlIamAuthIntegrationTests {

  private static final ImmutableList<String> requiredEnvVars =
      ImmutableList.of("MYSQL_IAM_USER", "MYSQL_DB", "MYSQL_IAM_CONNECTION_NAME");

  private static final String CONNECTION_NAME = System.getenv("MYSQL_IAM_CONNECTION_NAME");
  private static final String DB_NAME = System.getenv("MYSQL_DB");
  private static final String DB_USER = System.getenv("MYSQL_IAM_USER");

  @Rule public Timeout globalTimeout = new Timeout(20, TimeUnit.SECONDS);

  private ConnectionPool connectionPool;

  @Before
  public void setUpPool() {
    // Check that required env vars are set
    requiredEnvVars.forEach(
        (varName) ->
            assertWithMessage(
                    String.format(
                        "Environment variable '%s' must be set to perform these tests.", varName))
                .that(System.getenv(varName))
                .isNotEmpty());

    // [START cloud_sql_connector_mysql_r2dbc_iam_auth]
    // Set up ConnectionFactoryOptions
    ConnectionFactoryOptions options =
        ConnectionFactoryOptions.builder()
            .option(DRIVER, "gcp")
            .option(PROTOCOL, "mysql")
            .option(USER, DB_USER)
            .option(DATABASE, DB_NAME)
            .option(HOST, CONNECTION_NAME)
            .option(ENABLE_IAM_AUTH, true)
            .build();

    // Initialize connection pool
    ConnectionFactory connectionFactory = ConnectionFactories.get(options);
    ConnectionPoolConfiguration configuration =
        ConnectionPoolConfiguration.builder(connectionFactory).build();

    this.connectionPool = new ConnectionPool(configuration);
    // [END cloud_sql_connector_mysql_r2dbc_iam_auth]

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
}
