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

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RetryLogic attempts to call a Callable multiple times, sleeping between failed attempts. The
 * sleep duration is chosen randomly in the range [sleepDuration, sleepDuration * 2] to avoid
 * causing a thundering herd of requests on failure.
 *
 * @param <T> the result type of the Callable.
 */
public class RetryingCallable<T> implements Callable<T> {

  /** The callable that should be retried. */
  private final Callable<T> call;
  /** The number of times to attempt to retry. */
  private final int retryCount;
  /** The duration to sleep after a failed retry attempt. */
  private final Duration sleepDuration;

  /**
   * Construct a new RetryLogic.
   *
   * @param call the callable that should be retried
   * @param retryCount the number of times to retry
   * @param sleepDuration the duration wait after a failed attempt.
   */
  public RetryingCallable(Callable<T> call, int retryCount, Duration sleepDuration) {
    if (retryCount <= 0) {
      throw new IllegalArgumentException("retryCount must be > 0");
    }
    if (sleepDuration.isNegative() || sleepDuration.isZero()) {
      throw new IllegalArgumentException("sleepDuration must be positive");
    }
    if (call == null) {
      throw new IllegalArgumentException("call must not be null");
    }
    this.call = call;
    this.retryCount = retryCount;
    this.sleepDuration = sleepDuration;
  }

  @Override
  public T call() throws Exception {

    for (int i = retryCount - 1; i >= 0; i--) {
      // Attempt to call the Callable.
      try {
        return call.call();
      } catch (Exception e) {
        // Callable threw an exception.

        // If this is the last iteration, then
        // throw the exception
        if (i == 0) {
          throw e;
        }

        // Else, sleep a random amount of time, then retry
        long sleep =
            ThreadLocalRandom.current()
                .nextLong(sleepDuration.toMillis(), sleepDuration.toMillis() * 2);
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
}
