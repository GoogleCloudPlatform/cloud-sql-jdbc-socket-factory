/*
 * Copyright 2023 Google LLC
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

package com.google.cloud.sql.core;

import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionMetadata;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.TransactionDefinition;
import io.r2dbc.spi.ValidationDepth;
import java.time.Duration;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** {@link CloudSqlConnection} for connecting to Cloud SQL instances via R2DBC protocol. */
public class CloudSqlConnection implements Connection {
  private final Connection delegate;
  private final ConnectionConfig config;

  /** Creates an instance of Connection. */
  public CloudSqlConnection(ConnectionConfig config, Connection delegate) {
    this.config = config;
    this.delegate = delegate;
  }

  @Override
  public Publisher<Void> setStatementTimeout(Duration timeout) {
    return delegate.setStatementTimeout(timeout);
  }

  @Override
  public Publisher<Void> beginTransaction() {
    return delegate.beginTransaction();
  }

  @Override
  public Publisher<Void> beginTransaction(TransactionDefinition definition) {
    return delegate.beginTransaction(definition);
  }

  @Override
  public Publisher<Void> close() {
    return delegate.close();
  }

  @Override
  public Publisher<Void> commitTransaction() {
    return delegate.commitTransaction();
  }

  @Override
  public Batch createBatch() {
    return delegate.createBatch();
  }

  @Override
  public Publisher<Void> createSavepoint(String name) {
    return delegate.createSavepoint(name);
  }

  @Override
  public Statement createStatement(String sql) {
    return delegate.createStatement(sql);
  }

  @Override
  public boolean isAutoCommit() {
    return delegate.isAutoCommit();
  }

  @Override
  public ConnectionMetadata getMetadata() {
    return delegate.getMetadata();
  }

  @Override
  public IsolationLevel getTransactionIsolationLevel() {
    return delegate.getTransactionIsolationLevel();
  }

  @Override
  public Publisher<Void> releaseSavepoint(String name) {
    return delegate.releaseSavepoint(name);
  }

  @Override
  public Publisher<Void> rollbackTransaction() {
    return delegate.rollbackTransaction();
  }

  @Override
  public Publisher<Void> rollbackTransactionToSavepoint(String name) {
    return delegate.rollbackTransactionToSavepoint(name);
  }

  @Override
  public Publisher<Void> setAutoCommit(boolean autoCommit) {
    return delegate.setAutoCommit(autoCommit);
  }

  @Override
  public Publisher<Void> setTransactionIsolationLevel(IsolationLevel isolationLevel) {
    return delegate.setTransactionIsolationLevel(isolationLevel);
  }

  @Override
  public Publisher<Boolean> validate(ValidationDepth depth) {
    Publisher<Boolean> response = delegate.validate(depth);
    Mono.from(response)
        .doOnNext(
            valid -> {
              if (!valid) {
                // Force refresh the connection info when it is invalid.
                InternalConnectorRegistry.getInstance().forceRefresh(config);
              }
            })
        // Execute in a default scheduler to prevent blocking.
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
    return response;
  }

  @Override
  public String toString() {
    return "CloudSqlConnection{" + ", delegate=" + delegate + '}';
  }

  @Override
  public Publisher<Void> setLockWaitTimeout(Duration timeout) {
    return delegate.setLockWaitTimeout(timeout);
  }
}
