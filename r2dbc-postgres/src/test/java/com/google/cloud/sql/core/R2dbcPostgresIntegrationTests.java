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

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import reactor.core.publisher.Mono;

@Ignore
@RunWith(JUnit4.class)
public class R2dbcPostgresIntegrationTests {

  @Rule
  public Timeout globalTimeout= new Timeout(20, TimeUnit.SECONDS);

  private ConnectionFactory connectionFactory;

  @Before
  public void setUpConnection() {
    this.connectionFactory =
        ConnectionFactories.get(
            "r2dbc:gcp:postgres://user:password@connectionString/dbName");

    Mono.from(this.connectionFactory.create())
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
    Mono.from(this.connectionFactory.create())
        .delayUntil(c -> c.createStatement("DROP TABLE BOOKS").execute())
        .block();
  }

  @Test
  public void insertTest() {
    Mono.from(this.connectionFactory.create())
        .flatMapMany(
            c ->
                c.createStatement("INSERT INTO BOOKS (ID, TITLE) VALUES ($1, $2)")
                    .bind("$1", "book1")
                    .bind("$2", "Book One")
                    .add()
                    .bind("$1", "book2")
                    .bind("$2", "Book Two")
                    .execute())
        .flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> row.get(0)))
        .blockLast();

    List<String> books =
        Mono.from(this.connectionFactory.create())
            .flatMapMany(
                connection ->
                    connection.createStatement("SELECT TITLE FROM BOOKS ORDER BY ID").execute())
            .flatMap(
                spannerResult ->
                    spannerResult.map(
                        (r, meta) -> r.get("TITLE", String.class)))
            .collectList()
            .block();

    assertThat(books).containsExactly("Book One", "Book Two");
  }
}
