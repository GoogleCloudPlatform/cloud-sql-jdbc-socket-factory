/*
 * Copyright 2024 Google LLC
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

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RefreshStrategy that implements the lazy refresh strategy. */
public class LazyRefreshStrategy implements RefreshStrategy {
  private final Logger logger = LoggerFactory.getLogger(LazyRefreshStrategy.class);

  private final String name;
  private final Supplier<ConnectionInfo> refreshOperation;
  private final Duration refreshBuffer;

  private final Object connectionInfoGuard = new Object();

  @GuardedBy("connectionInfoGuard")
  private ConnectionInfo connectionInfo;

  @GuardedBy("connectionInfoGuard")
  private boolean closed;

  /** Creates a new LazyRefreshStrategy instance. */
  public LazyRefreshStrategy(
      String name, Supplier<ConnectionInfo> refreshOperation, Duration refreshDuration) {
    this.name = name;
    this.refreshOperation = refreshOperation;
    this.refreshBuffer = refreshDuration;
  }

  @Override
  public ConnectionInfo getConnectionInfo(long timeoutMs) {
    synchronized (connectionInfoGuard) {
      if (closed) {
        throw new IllegalStateException(
            String.format("[%s] Lazy Refresh: Named connection closed.", name));
      }

      if (connectionInfo == null) {
        logger.debug(
            String.format(
                "[%s] Lazy Refresh Operation: No client certificate. Starting next refresh "
                    + "operation immediately.",
                name));
        fetchConnectionInfo();
      }
      if (Instant.now().isAfter(connectionInfo.getExpiration().minus(refreshBuffer))) {
        logger.debug(
            String.format(
                "[%s] Lazy Refresh Operation: Client certificate has expired. Starting next "
                    + "refresh operation immediately.",
                name));
        fetchConnectionInfo();
      }
      return connectionInfo;
    }
  }

  private void fetchConnectionInfo() {
    synchronized (connectionInfoGuard) {
      logger.debug(String.format("[%s] Lazy Refresh Operation: Starting refresh operation.", name));
      try {
        this.connectionInfo = this.refreshOperation.get();
        logger.debug(
            String.format(
                "[%s] Lazy Refresh Operation: Completed refresh with new certificate "
                    + "expiration at %s.",
                name, connectionInfo.getExpiration().toString()));

      } catch (TerminalException e) {
        logger.debug(String.format("[%s] Lazy Refresh Operation: Failed! No retry.", name), e);
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(String.format("[%s] Refresh Operation: Failed!", name), e);
      }
    }
  }

  /** Force a new refresh of the instance data if the client certificate has expired. */
  @Override
  public void forceRefresh() {
    // invalidate connectionInfo so that the next call to getConectionInfo() will
    // fetch new data.
    synchronized (connectionInfoGuard) {
      if (closed) {
        throw new IllegalStateException(
            String.format("[%s] Lazy Refresh: Named connection closed.", name));
      }
      this.connectionInfo = null;
      logger.debug(String.format("[%s] Lazy Refresh Operation: Forced refresh.", name));
    }
  }

  /** Force a new refresh of the instance data if the client certificate has expired. */
  @Override
  public void refreshIfExpired() {
    synchronized (connectionInfoGuard) {
      if (closed) {
        throw new IllegalStateException(
            String.format("[%s] Lazy Refresh: Named connection closed.", name));
      }
    }
  }

  @Override
  public void close() {
    synchronized (connectionInfoGuard) {
      closed = true;
      logger.debug(String.format("[%s] Lazy Refresh Operation: Connector closed.", name));
    }
  }
}
