/*
 * Copyright 2023 Google LLC
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

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RetryingCallable attempts to call a Callable multiple times, sleeping between failed attempts.
 * The sleep duration is chosen randomly in the range [sleepDuration, sleepDuration * 2] to avoid
 * causing a thundering herd of requests on failure.
 *
 * <p>exponentialBackoff calculates a duration based on the attempt i.
 *
 * <p>The formula is: base * multi^(attempt + 1 + random)
 *
 * <p>With base = 200ms and multi = 1.618, and random = [0.0, 1.0), the backoff values would fall
 * between the following low and high ends:
 *
 * <p>Attempt Low (ms) High (ms)
 *
 * <p>0 324 524 1 524 847 2 847 1371 3 1371 2218 4 2218 3588
 *
 * <p>The theoretical worst case scenario would have a client wait 8.5s in total for an API request
 * to complete (with the first four attempts failing, and the fifth succeeding).
 *
 * <p>This backoff strategy matches the behavior of the Cloud SQL Proxy v1.
 *
 * @param <T> the result type of the Callable.
 */
class RetryingCallable<T> implements Callable<T> {
  private static final int RETRY_COUNT = 5;

  /** The callable that should be retried. */
  private final Callable<T> callable;

  /**
   * Construct a new RetryLogic.
   *
   * @param callable the callable that should be retried
   */
  public RetryingCallable(Callable<T> callable) {
    if (callable == null) {
      throw new IllegalArgumentException("call must not be null");
    }
    this.callable = callable;
  }

  @Override
  public T call() throws Exception {

    for (int attempt = 0; attempt < RETRY_COUNT; attempt++) {
      // Attempt to call the Callable.
      try {
        return callable.call();
      } catch (Exception e) {
        // If this is the last retry attempt, or if the exception is fatal
        // then exit immediately.
        if (attempt == (RETRY_COUNT - 1) || isFatalException(e)) {
          throw e;
        }
        // Else, sleep a random amount of time, then retry
        long sleep = exponentialBackoffMs(attempt);
        try {
          Thread.sleep(sleep);
        } catch (InterruptedException ie) {
          throw e; // if sleep is interrupted, then throw 'e', don't take another iteration
        }
      }
    }

    // If the callable was never called, then throw an exception. This will never happen
    // as long as the preconditions in the constructor are properly met.
    throw new RuntimeException("call was never called.");
  }

  protected boolean isFatalException(Exception e) {
    return false;
  }

  private long exponentialBackoffMs(int attempt) {
    long baseMs = 200;
    double multi = 1.618;
    double exp = attempt + 1.0 + ThreadLocalRandom.current().nextDouble();
    return (long) (baseMs * Math.pow(multi, exp));
  }
}
