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


import com.google.common.collect.ImmutableList;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;


@RunWith(JUnit4.class)
public class R2dbcPostgresIntegrationTests {

    private static final String CONNECTION_NAME = System.getenv("POSTGRES_CONNECTION_NAME");
    private static final String DB_NAME = System.getenv("POSTGRES_DB");
    private static final String DB_USER = System.getenv("POSTGRES_USER");
    private static final String DB_PASSWORD = System.getenv("POSTGRES_PASS");
    private static ImmutableList<String> requiredEnvVars = ImmutableList
            .of("POSTGRES_USER", "POSTGRES_PASS", "POSTGRES_DB", "POSTGRES_CONNECTION_NAME");
    @Rule
    public Timeout globalTimeout = new Timeout(20, TimeUnit.SECONDS);

    private ConnectionFactory connectionPool;

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
    public void setUpPool() {
        // Set up URL parameters
        String r2dbcURL = String
                .format("r2dbc:gcp:postgres://%s:%s@%s/%s", DB_USER, DB_PASSWORD, CONNECTION_NAME,
                        DB_NAME);

        // Initialize connection pool
        ConnectionFactory connectionFactory = ConnectionFactories.get(r2dbcURL);
        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
                .builder(connectionFactory)
                .build();

        this.connectionPool = new ConnectionPool(configuration);

        // Create table
        Mono.from(this.connectionPool.create())
                .flatMapMany(
                        c ->
                                c.createStatement(
                                        "CREATE TABLE IF NOT EXISTS BOOKS ("
                                                + "  ID CHAR(20) NOT NULL,"
                                                + "  TITLE TEXT NOT NULL"
                                                + ")")
                                        .execute())
                .blockLast();
    }


    @After
    public void dropTableIfPresent() {
        Mono.from(this.connectionPool.create())
                .delayUntil(c -> c.createStatement("DROP TABLE BOOKS").execute())
                .block();
    }

    @Test
    public void pooledConnectionTest() {
        Mono.from(this.connectionPool.create())
                .flatMapMany(
                        c ->
                                c.createStatement("INSERT INTO BOOKS (ID, TITLE) VALUES ($1, $2)")
                                        .bind("$1", "book1")
                                        .bind("$2", "Book One")
                                        .add()
                                        .bind("$1", "book2")
                                        .bind("$2", "Book Two")
                                        .execute())
                .flatMap(result -> result.map((row, rowMetadata) -> row.get(0)))
                .blockLast();

        List<String> books =
                Mono.from(this.connectionPool.create())
                        .flatMapMany(
                                connection ->
                                        connection.createStatement("SELECT TITLE FROM BOOKS ORDER BY ID").execute())
                        .flatMap(
                                result ->
                                        result.map(
                                                (r, meta) -> r.get("TITLE", String.class)))
                        .collectList()
                        .block();

        assertThat(books).containsExactly("Book One", "Book Two");
    }
}
