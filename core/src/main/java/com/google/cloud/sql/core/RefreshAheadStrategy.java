/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles periodic refresh operations for an instance. */
class RefreshAheadStrategy implements RefreshStrategy {
  private static final Logger logger = LoggerFactory.getLogger(RefreshAheadStrategy.class);
  private static final long DEFAULT_CONNECT_TIMEOUT_MS = 45000;

  private final ListeningScheduledExecutorService executor;

  private final Object connectionInfoGuard = new Object();
  private final AsyncRateLimiter rateLimiter;

  private final RefreshCalculator refreshCalculator = new RefreshCalculator();
  private final Supplier<ListenableFuture<ConnectionInfo>> refreshOperation;
  private final String name;

  @GuardedBy("connectionInfoGuard")
  private ListenableFuture<ConnectionInfo> current;

  @GuardedBy("connectionInfoGuard")
  private ListenableFuture<ConnectionInfo> next;

  @GuardedBy("connectionInfoGuard")
  private boolean refreshRunning;

  @GuardedBy("connectionInfoGuard")
  private Throwable currentRefreshFailure;

  @GuardedBy("connectionInfoGuard")
  private boolean closed;

  @GuardedBy("connectionInfoGuard")
  private boolean triggerNextRefresh = true;

  /**
   * Create a new refresher.
   *
   * @param name the name of what is being refreshed, for logging.
   * @param executor the executor to schedule refresh tasks.
   * @param refreshOperation The supplier that refreshes the data.
   * @param rateLimiter The rate limiter.
   */
  RefreshAheadStrategy(
      String name,
      ListeningScheduledExecutorService executor,
      Supplier<ListenableFuture<ConnectionInfo>> refreshOperation,
      AsyncRateLimiter rateLimiter) {
    this(name, executor, refreshOperation, rateLimiter, true);
  }

  /**
   * Create a new refresher.
   *
   * @param name the name of what is being refreshed, for logging.
   * @param executor the executor to schedule refresh tasks.
   * @param refreshOperation The supplier that refreshes the data.
   * @param rateLimiter The rate limiter.
   * @param triggerNextRefresh The next refresh operation should be triggered.
   */
  RefreshAheadStrategy(
      String name,
      ListeningScheduledExecutorService executor,
      Supplier<ListenableFuture<ConnectionInfo>> refreshOperation,
      AsyncRateLimiter rateLimiter,
      boolean triggerNextRefresh) {
    this.name = name;
    this.executor = executor;
    this.refreshOperation = refreshOperation;
    this.rateLimiter = rateLimiter;
    this.triggerNextRefresh = triggerNextRefresh;
    synchronized (connectionInfoGuard) {
      forceRefresh();
      this.current = this.next;
    }
  }

  /**
   * Returns the current data related to the instance from {@link #startRefreshAttempt()}. May block
   * if no valid data is currently available. This method is called by an application thread when it
   * is trying to create a new connection to the database. (It is not called by a
   * ListeningScheduledExecutorService task.) So it is OK to block waiting for a future to complete.
   *
   * <p>When no refresh attempt is in progress, this returns immediately. Otherwise, it waits up to
   * timeoutMs milliseconds. If a refresh attempt succeeds, returns immediately at the end of that
   * successful attempt. If no attempts succeed within the timeout, throws a RuntimeException with
   * the exception from the last failed refresh attempt as the cause.
   */
  @Override
  public ConnectionInfo getConnectionInfo(long timeoutMs) {
    ListenableFuture<ConnectionInfo> f;
    synchronized (connectionInfoGuard) {
      if (closed) {
        throw new IllegalStateException("Named connection closed");
      }
      f = current;
    }

    try {
      return f.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      synchronized (connectionInfoGuard) {
        if (currentRefreshFailure != null) {
          throw new RuntimeException(
              String.format(
                      "Unable to get valid instance data within %d ms."
                          + " Last refresh attempt failed:",
                      timeoutMs)
                  + currentRefreshFailure.getMessage(),
              currentRefreshFailure);
        }
      }
      throw new RuntimeException(
          String.format(
              "Unable to get valid instance data within %d ms. No refresh has completed.",
              timeoutMs),
          e);
    } catch (ExecutionException | InterruptedException ex) {
      Throwable cause = ex.getCause();
      Throwables.throwIfUnchecked(cause);
      throw new RuntimeException(cause);
    }
  }

  /**
   * Attempts to force a new refresh of the instance data. May fail if called too frequently or if a
   * new refresh is already in progress. If successful, other methods will block until refresh has
   * been completed.
   */
  @Override
  public void forceRefresh() {
    synchronized (connectionInfoGuard) {
      if (closed) {
        throw new IllegalStateException("Named connection closed");
      }
      // Don't force a refresh until the current refresh operation
      // has produced a successful refresh.
      if (refreshRunning) {
        return;
      }

      if (next != null) {
        next.cancel(false);
      }

      logger.debug(
          String.format(
              "[%s] Force Refresh: the next refresh operation was cancelled."
                  + " Scheduling new refresh operation immediately.",
              name));
      next = this.startRefreshAttempt();
    }
  }

  /** Force a new refresh of the instance data if the client certificate has expired. */
  @Override
  public void refreshIfExpired() {
    ConnectionInfo info = getConnectionInfo(DEFAULT_CONNECT_TIMEOUT_MS);
    logger.debug(
        String.format(
            "[%s] Now = %s, Current client certificate expiration = %s",
            name, Instant.now().toString(), info.getExpiration()));
    if (Instant.now().isAfter(info.getExpiration())) {
      logger.debug(
          String.format(
              "[%s] Client certificate has expired. Starting next refresh operation immediately.",
              name));
      forceRefresh();
    }
  }

  /**
   * Triggers an update of internal information obtained from the Cloud SQL Admin API, returning a
   * future that resolves once a valid T has been acquired. This sets up a chain of futures that
   * will 1. Acquire a rate limiter. 2. Attempt to fetch instance data. 3. Schedule the next attempt
   * to get instance data based on the success/failure of this attempt.
   *
   * @see RefreshAheadStrategy#handleRefreshResult(
   *     com.google.common.util.concurrent.ListenableFuture)
   */
  private ListenableFuture<ConnectionInfo> startRefreshAttempt() {
    // As soon as we begin submitting refresh attempts to the executor, mark a refresh
    // as "in-progress" so that subsequent forceRefresh() calls balk until this one completes.
    synchronized (connectionInfoGuard) {
      refreshRunning = true;
    }

    logger.debug(String.format("[%s] Refresh Operation: Acquiring rate limiter permit.", name));
    ListenableFuture<?> delay = rateLimiter.acquireAsync(executor);
    delay.addListener(
        () ->
            logger.debug(
                String.format("[%s] Refresh Operation: Rate limiter permit acquired.", name)),
        executor);

    // Once rate limiter is done, attempt to getInstanceData.
    ListenableFuture<ConnectionInfo> f =
        Futures.whenAllComplete(delay).callAsync(refreshOperation::get, executor);

    // Finally, reschedule refresh after getInstanceData is complete.
    return Futures.whenAllComplete(f).callAsync(() -> handleRefreshResult(f), executor);
  }

  private ListenableFuture<ConnectionInfo> handleRefreshResult(
      ListenableFuture<ConnectionInfo> connectionInfoFuture) {
    try {
      // This does not block, because it only gets called when connectionInfoFuture has completed.
      // This will throw an exception if the refresh attempt has failed.
      ConnectionInfo info = connectionInfoFuture.get();

      logger.debug(
          String.format(
              "[%s] Refresh Operation: Completed refresh with new certificate expiration at %s.",
              name, info.getExpiration().toString()));
      long secondsToRefresh =
          refreshCalculator.calculateSecondsUntilNextRefresh(Instant.now(), info.getExpiration());

      synchronized (connectionInfoGuard) {
        // Refresh completed successfully, reset forceRefreshRunning.
        refreshRunning = false;
        currentRefreshFailure = null;
        current = Futures.immediateFuture(info);

        // Now update nextInstanceData to perform a refresh after the
        // scheduled delay
        if (!closed && triggerNextRefresh) {
          logger.debug(
              String.format(
                  "[%s] Refresh Operation: Next operation scheduled at %s.",
                  name,
                  Instant.now()
                      .plus(secondsToRefresh, ChronoUnit.SECONDS)
                      .truncatedTo(ChronoUnit.SECONDS)
                      .toString()));

          next =
              Futures.scheduleAsync(
                  this::startRefreshAttempt, secondsToRefresh, TimeUnit.SECONDS, executor);
        }
        // Resolves to an T immediately
        return current;
      }

    } catch (ExecutionException | InterruptedException e) {

      // No refresh retry when the TerminalException is raised.
      final Throwable cause = e.getCause();
      if (cause instanceof TerminalException) {
        logger.debug(String.format("[%s] Refresh Operation: Failed! No retry.", name), e);
        throw (TerminalException) cause;
      }

      logger.debug(
          String.format(
              "[%s] Refresh Operation: Failed! Starting next refresh operation immediately.", name),
          e);
      synchronized (connectionInfoGuard) {
        currentRefreshFailure = e;
        if (!closed) {
          next = this.startRefreshAttempt();
        }
        // Resolves after the next successful refresh attempt.
        return next;
      }
    }
  }

  @Override
  public void close() {
    synchronized (connectionInfoGuard) {
      if (closed) {
        return;
      }

      // Cancel any in-progress requests
      if (!this.current.isDone()) {
        this.current.cancel(true);
      }
      if (!this.next.isDone()) {
        this.next.cancel(true);
      }

      this.current =
          Futures.immediateFailedFuture(new RuntimeException("Named connection is closed."));

      this.closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    synchronized (connectionInfoGuard) {
      return closed;
    }
  }

  ListenableFuture<ConnectionInfo> getNext() {
    synchronized (connectionInfoGuard) {
      return this.next;
    }
  }

  ListenableFuture<ConnectionInfo> getCurrent() {
    synchronized (connectionInfoGuard) {
      return this.current;
    }
  }
}
