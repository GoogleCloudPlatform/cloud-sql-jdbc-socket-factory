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

package com.google.cloud.sql.postgres;


import com.google.common.collect.ImmutableList;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;


@RunWith(JUnit4.class)
public class JdbcPostgresIntegrationTests {

    private static final String CONNECTION_NAME = System.getenv("POSTGRES_CONNECTION_NAME");
    private static final String DB_NAME = System.getenv("POSTGRES_DB");
    private static final String DB_USER = System.getenv("POSTGRES_USER");
    private static final String DB_PASSWORD = System.getenv("POSTGRES_PASS");
    private static ImmutableList<String> requiredEnvVars = ImmutableList
            .of("POSTGRES_USER", "POSTGRES_PASS", "POSTGRES_DB", "POSTGRES_CONNECTION_NAME");
    @Rule
    public Timeout globalTimeout = new Timeout(20, TimeUnit.SECONDS);

    private HikariDataSource connectionPool;

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
        // Set up URL parameters
        String jdbcURL = String.format("jdbc:postgresql:///%s", DB_NAME);
        Properties connProps = new Properties();
        connProps.setProperty("user", DB_USER);
        connProps.setProperty("password", DB_PASSWORD);
        connProps.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory");
        connProps.setProperty("cloudSqlInstance", CONNECTION_NAME);

        // Initialize connection pool
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcURL);
        config.setDataSourceProperties(connProps);


        this.connectionPool = new HikariDataSource(config);

        // Create table
        try (Connection conn = connectionPool.getConnection()) {
            String stmt = "CREATE TABLE IF NOT EXISTS BOOKS ("
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
            String stmt = "DROP TABLE BOOKS;";
            try (PreparedStatement dropTableStatement = conn.prepareStatement(stmt)) {
                dropTableStatement.execute();
            }
        }
    }

    @Test
    public void pooledConnectionTest() throws SQLException {
        try (Connection conn = connectionPool.getConnection()) {
            String stmt = "INSERT INTO BOOKS (ID, TITLE) VALUES (?, ?)";
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
            String stmt = "SELECT TITLE FROM BOOKS ORDER BY ID";
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
