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

package com.google.cloud.sql.postgres;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(JUnit4.class)
public class JdbcPostgresIamAuthIntegrationTests {
  private static final Logger log =
      LoggerFactory.getLogger(JdbcPostgresIamAuthIntegrationTests.class);

  // [START cloud_sql_connector_postgres_jdbc_iam_auth]
  private static final String CONNECTION_NAME = System.getenv("POSTGRES_CONNECTION_NAME");
  private static final String DB_NAME = System.getenv("POSTGRES_DB");
  private static final String DB_USER = System.getenv("POSTGRES_IAM_USER");
  private static final String IP_TYPE =
      System.getenv("IP_TYPE") == null ? "PUBLIC" : System.getenv("IP_TYPE");
  // [END cloud_sql_connector_postgres_jdbc_iam_auth]
  private static final ImmutableList<String> requiredEnvVars =
      ImmutableList.of("POSTGRES_IAM_USER", "POSTGRES_DB", "POSTGRES_CONNECTION_NAME");
  @Rule public Timeout globalTimeout = new Timeout(80, TimeUnit.SECONDS);

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

    log.info("Crypto Providers: ");
    Provider[] providers = Security.getProviders();
    for (Provider p : providers) {
      log.info("  {}", p.getName());
    }
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      Provider prov = ctx.getProvider();
      log.info("TLS Provider: {}", prov.getName());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Before
  public void setUpPool() throws SQLException {
    // [START cloud_sql_connector_postgres_jdbc_iam_auth]
    // Set up URL parameters
    String jdbcURL = String.format("jdbc:postgresql:///%s", DB_NAME);
    Properties connProps = new Properties();
    connProps.setProperty("user", DB_USER);
    // Password must be set to a nonempty value to bypass driver validation errors
    connProps.setProperty("password", "password");
    connProps.setProperty("sslmode", "disable");
    connProps.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory");
    connProps.setProperty("cloudSqlInstance", CONNECTION_NAME);
    connProps.setProperty("ipTypes", IP_TYPE);
    connProps.setProperty("enableIamAuth", "true");

    // Initialize connection pool
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcURL);
    config.setDataSourceProperties(connProps);

    this.connectionPool = new HikariDataSource(config);
    // [END cloud_sql_connector_postgres_jdbc_iam_auth]
  }

  @Test
  public void pooledConnectionTest() throws SQLException {

    List<Timestamp> rows = new ArrayList<>();
    try (Connection conn = connectionPool.getConnection()) {
      try (PreparedStatement selectStmt = conn.prepareStatement("SELECT NOW() as TS")) {
        ResultSet rs = selectStmt.executeQuery();
        while (rs.next()) {
          rows.add(rs.getTimestamp("TS"));
        }
      }
    }
    assertThat(rows.size()).isEqualTo(1);
  }
}
