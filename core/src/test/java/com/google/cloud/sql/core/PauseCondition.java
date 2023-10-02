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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

  final Lock lock = new ReentrantLock();
  final Condition allowContinue = lock.newCondition();
  final Condition proceeded = lock.newCondition();

  final AtomicBoolean allowProceed = new AtomicBoolean(false);
  final AtomicBoolean afterPause = new AtomicBoolean(false);
  final AtomicBoolean beforePause = new AtomicBoolean(false);

  /**
   * Signals the thread blocked on pause() to proceed, then waits for the condition to become true.
   */
  public void proceedWhen(Supplier<Boolean> cond) throws InterruptedException {
    proceed();
    while (!cond.get()) {
      Thread.sleep(100);
    }
  }

  /** Returns after the pause() method is called */
  public void proceedWhenPaused() throws InterruptedException {
    while (!beforePause.get()) {
      Thread.sleep(10);
    }
  }

  /**
   * Immediately signals the thread blocked on pause() to continue. Note, if the thread is not yet
   * blocked on pause(), then it will
   */
  public void proceed() {
    lock.lock();
    try {
      allowProceed.set(true);
      allowContinue.signal();
    } finally {
      lock.unlock();
    }
  }

  /** Pause until allowed to proceed. */
  public void pause() throws InterruptedException {

    lock.lock();
    try {
      beforePause.set(true);
      while (!allowProceed.get()) {
        allowContinue.await(100, TimeUnit.MILLISECONDS);
      }
      afterPause.set(true);
    } finally {
      lock.unlock();
    }
  }

  /** Wait for the pause with timeout. */
  public void waitForPauseToEnd(long waitMs) throws InterruptedException, TimeoutException {
    Instant until = Instant.now().plus(waitMs, ChronoUnit.MILLIS);
    lock.lock();
    try {
      while (!afterPause.get() && Instant.now().isBefore(until)) {
        proceeded.await(waitMs, TimeUnit.MILLISECONDS);
      }
      if (!afterPause.get()) {
        throw new TimeoutException();
      }
    } finally {
      lock.unlock();
    }
  }
}
