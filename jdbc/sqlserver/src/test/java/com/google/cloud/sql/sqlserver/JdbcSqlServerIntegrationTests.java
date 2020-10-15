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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.After;
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
  private static ImmutableList<String> requiredEnvVars = ImmutableList
      .of("SQLSERVER_USER", "SQLSERVER_PASS", "SQLSERVER_DB", "SQLSERVER_CONNECTION_NAME");
  @Rule
  public Timeout globalTimeout = new Timeout(20, TimeUnit.SECONDS);

  private HikariDataSource connectionPool;
  private String tableName;

  @BeforeClass
  public static void checkEnvVars() {
    // Check that required env vars are set
    requiredEnvVars.stream().forEach((varName) -> {
      assertWithMessage(
          String.format("Environment variable '%s' must be set to perform these tests.", varName))
          .that(System.getenv(varName)).isNotEmpty();
    });
  }

  @Before
  public void setUpPool() throws SQLException {

    // Initialize connection pool
    HikariConfig config = new HikariConfig();
    config
        .setDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
    config.setUsername(DB_USER); // e.g. "root", "sqlserver"
    config.setPassword(DB_PASSWORD); // e.g. "my-password"
    config.addDataSourceProperty("databaseName", DB_NAME);

    config.addDataSourceProperty("socketFactoryClass",
        "com.google.cloud.sql.sqlserver.SocketFactory");
    config.addDataSourceProperty("socketFactoryConstructorArg", CONNECTION_NAME);
    
    this.connectionPool = new HikariDataSource(config);
    this.tableName = String.format("books_%s", UUID.randomUUID().toString().replace("-", ""));

    // Create table
    try (Connection conn = connectionPool.getConnection()) {
      String stmt = String.format("CREATE TABLE %s (", this.tableName)
          + "  ID CHAR(20) NOT NULL,"
          + "  TITLE TEXT NOT NULL"
          + ");";
      try (PreparedStatement createTableStatement = conn.prepareStatement(stmt)) {
        createTableStatement.execute();
      }
    }
  }


  @After
  public void dropTableIfPresent() throws SQLException {
    try (Connection conn = connectionPool.getConnection()) {
      String stmt = String.format("DROP TABLE %s;", this.tableName);
      try (PreparedStatement dropTableStatement = conn.prepareStatement(stmt)) {
        dropTableStatement.execute();
      }
    }
  }

  @Test
  public void pooledConnectionTest() throws SQLException {
    try (Connection conn = connectionPool.getConnection()) {
      String stmt = String.format("INSERT INTO %s (ID, TITLE) VALUES (?, ?)", this.tableName);
      try (PreparedStatement insertStmt = conn.prepareStatement(stmt)) {
        insertStmt.setString(1, "book1");
        insertStmt.setString(2, "Book One");
        insertStmt.execute();
        insertStmt.setString(1, "book2");
        insertStmt.setString(2, "Book Two");
        insertStmt.execute();
      }
    }

    List<String> bookList = new ArrayList<>();
    try (Connection conn = connectionPool.getConnection()) {
      String stmt = String.format("SELECT TITLE FROM %s ORDER BY ID", this.tableName);
      try (PreparedStatement selectStmt = conn.prepareStatement(stmt)) {

        ResultSet rs = selectStmt.executeQuery();
        while (rs.next()) {
          bookList.add(rs.getString("TITLE"));
        }
      }
    }
    assertThat(bookList).containsExactly("Book One", "Book Two");

  }
}
