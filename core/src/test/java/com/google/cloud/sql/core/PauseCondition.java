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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Allows a test case to pause and continue of another thread predictably, so that unit tests don't
 * need to rely on carefully crafted timeouts using Thread.sleep() to test sequences of execution
 * across threads.
 */
class PauseCondition {

  private final Lock lock = new ReentrantLock();
  private final Condition allowContinue = lock.newCondition();
  private final Condition proceeded = lock.newCondition();

  private final AtomicBoolean allowProceed = new AtomicBoolean(false);
  private final AtomicBoolean afterPause = new AtomicBoolean(false);
  private final AtomicBoolean beforePause = new AtomicBoolean(false);

  /**
   * Signals any thread blocked on pause() to continue, all subsequent calls to pause() will return
   * immediately.
   */
  public void proceed() {
    lock.lock();
    try {
      allowProceed.set(true);
      allowContinue.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Pause until proceed() is called. If proceed() was called in the past, this returns immediately.
   */
  public void pause() {

    lock.lock();
    try {
      beforePause.set(true);
      proceeded.signalAll();
      while (!allowProceed.get()) {
        try {
          allowContinue.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      afterPause.set(true);
      proceeded.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Blocks until the pause() method has been called at least once.
   *
   * @throws TimeoutException when the timeout expires before pause() is called.
   */
  public void waitForPauseToStart(long timeoutMs) throws TimeoutException, InterruptedException {
    waitForCondition(() -> beforePause.get(), timeoutMs);
  }

  /**
   * Blocks until the pause() method has returned at least once.
   *
   * @throws TimeoutException when the timeout expires before pause() has exited.
   */
  public void waitForPauseToEnd(long waitMs) throws InterruptedException, TimeoutException {
    waitForCondition(() -> afterPause.get(), waitMs);
  }

  /**
   * Blocks until the condition function is true, checking every 100ms or when signaled because the
   * pause() function was called.
   *
   * @throws TimeoutException when the timeout expires before the condition returns true.
   */
  public void waitForCondition(Supplier<Boolean> condition, long waitMs)
      throws InterruptedException, TimeoutException {
    final long until = System.currentTimeMillis() + waitMs;
    lock.lock();
    try {
      while (!condition.get() && System.currentTimeMillis() < until) {
        proceeded.await(100, TimeUnit.MILLISECONDS);
      }
      if (!condition.get()) {
        throw new TimeoutException("waitForCondition() has not succeeded after " + waitMs + "ms");
      }
    } finally {
      lock.unlock();
    }
  }
}
