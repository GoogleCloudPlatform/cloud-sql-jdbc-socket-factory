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
  final AtomicBoolean allowProceed = new AtomicBoolean(false);

  /**
   * Waits for the condition to become true, signaling the thread that is blocked on pause() to
   * continue.
   */
  public void proceedWhen(Supplier<Boolean> cond) {
    while (!cond.get()) {
      proceed();
      Thread.yield();
    }
  }

  /**
   * Immediately signals the thread blocked on pause() to continue. Note, if the thread is not yet
   * blocked on pause(), then it will
   */
  public void proceed() {
    try {
      lock.lock();
      allowProceed.set(true);
      allowContinue.signal();
    } finally {
      lock.unlock();
    }
    Thread.yield();
  }

  /** Pause until signaled to proceed. */
  public void pause() throws InterruptedException {
    try {
      lock.lock();
      while (!allowProceed.get()) {
        allowContinue.await();
      }
      allowProceed.set(false);
    } finally {
      lock.unlock();
    }
  }
}
