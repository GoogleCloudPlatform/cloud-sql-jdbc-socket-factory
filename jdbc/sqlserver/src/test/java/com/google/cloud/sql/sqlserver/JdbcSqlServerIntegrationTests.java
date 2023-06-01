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

package com.google.cloud.sql.sqlserver;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JdbcSqlServerIntegrationTests {

  private static final String CONNECTION_NAME = System.getenv("SQLSERVER_CONNECTION_NAME");
  private static final String DB_NAME = System.getenv("SQLSERVER_DB");
  private static final String DB_USER = System.getenv("SQLSERVER_USER");
  private static final String DB_PASSWORD = System.getenv("SQLSERVER_PASS");
  private static final ImmutableList<String> requiredEnvVars =
      ImmutableList.of(
          "SQLSERVER_USER", "SQLSERVER_PASS", "SQLSERVER_DB", "SQLSERVER_CONNECTION_NAME");
  @Rule public Timeout globalTimeout = new Timeout(30, TimeUnit.SECONDS);

  private HikariDataSource connectionPool;

  @BeforeClass
  public static void checkEnvVars() {
    // Check that required env vars are set
    requiredEnvVars.forEach(
        (varName) ->
            assertWithMessage(
                    String.format(
                        "Environment variable '%s' must be set to perform these tests.", varName))
                .that(System.getenv(varName))
                .isNotEmpty());
  }

  @Before
  public void setUpPool() throws SQLException {

    // Initialize connection pool
    HikariConfig config = new HikariConfig();
    config.setDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
    config.setUsername(DB_USER); // e.g. "root", "sqlserver"
    config.setPassword(DB_PASSWORD); // e.g. "my-password"
    config.addDataSourceProperty("databaseName", DB_NAME);

    config.addDataSourceProperty(
        "socketFactoryClass", "com.google.cloud.sql.sqlserver.com.google.cloud.sql.SocketFactory");
    config.addDataSourceProperty("socketFactoryConstructorArg", CONNECTION_NAME);
    config.addDataSourceProperty("encrypt", "false");
    config.setConnectionTimeout(10000); // 10s

    this.connectionPool = new HikariDataSource(config);
  }

  @Test
  public void pooledConnectionTest() throws SQLException {

    List<Integer> rows = new ArrayList<>();
    try (Connection conn = connectionPool.getConnection()) {
      try (PreparedStatement selectStmt = conn.prepareStatement("SELECT 1 as TS")) {
        ResultSet rs = selectStmt.executeQuery();
        while (rs.next()) {
          rows.add(rs.getInt("TS"));
        }
      }
    }
    assertThat(rows.size()).isEqualTo(1);
  }
}
