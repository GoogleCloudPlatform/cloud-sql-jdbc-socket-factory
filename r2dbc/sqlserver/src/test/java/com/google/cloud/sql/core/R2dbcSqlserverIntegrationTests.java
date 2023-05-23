/*
 * Copyright 2020 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
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
public class R2dbcSqlserverIntegrationTests {

  private static final ImmutableList<String> requiredEnvVars =
      ImmutableList.of(
          "SQLSERVER_USER", "SQLSERVER_PASS", "SQLSERVER_DB", "SQLSERVER_CONNECTION_NAME");

  private static final String CONNECTION_NAME = System.getenv("SQLSERVER_CONNECTION_NAME");
  private static final String DB_NAME = System.getenv("SQLSERVER_DB");
  private static final String DB_USER = System.getenv("SQLSERVER_USER");
  private static final String DB_PASSWORD = System.getenv("SQLSERVER_PASS");

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

    // Set up URL parameters
    String r2dbcURL =
        String.format(
            "r2dbc:gcp:mssql://%s:%s@%s/%s", DB_USER, DB_PASSWORD, CONNECTION_NAME, DB_NAME);

    // Initialize connection pool
    ConnectionFactory connectionFactory = ConnectionFactories.get(r2dbcURL);
    ConnectionPoolConfiguration configuration =
        ConnectionPoolConfiguration.builder(connectionFactory).build();

    this.connectionPool = new ConnectionPool(configuration);
  }

  @Test
  public void pooledConnectionTest() {
    List<Integer> rows =
        Mono.from(this.connectionPool.create())
            .flatMapMany(connection -> connection.createStatement("SELECT 1 as TS").execute())
            .flatMap(result -> result.map((r, meta) -> r.get("TS", Integer.class)))
            .collectList()
            .block();

    assertThat(rows.size()).isEqualTo(1);
  }
}
