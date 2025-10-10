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

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RetryingCallableTest {
  @Test
  public void testConstructorIllegalArguments() throws Exception {
    // Callable must not be null
    Assert.assertThrows(IllegalArgumentException.class, () -> new RetryingCallable<>(null));
  }

  @Test
  public void testNoRetryRequired() throws Exception {
    RetryingCallable<Integer> r = new RetryingCallable<>(() -> 1);
    int v = r.call();
    assertThat(v).isEqualTo(1);
  }

  @Test
  public void testAlwaysFails() {
    final AtomicInteger counter = new AtomicInteger();
    RetryingCallable<Integer> r =
        new RetryingCallable<>(
            () -> {
              counter.incrementAndGet();
              throw new Exception("nope");
            });

    try {
      r.call();
      Assert.fail("got no exception, wants an exception to be thrown");
    } catch (Exception e) {
      // Expected to throw an exception
    }

    assertThat(counter.get()).isEqualTo(5);
  }

  @Test
  public void testRetrySucceedsAfterFailures() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    RetryingCallable<Integer> r =
        new RetryingCallable<>(
            () -> {
              int i = counter.incrementAndGet();
              if (i < 3) {
                throw new Exception("nope");
              }
              return i;
            });

    int v = r.call();
    assertThat(counter.get()).isEqualTo(3);
    assertThat(v).isEqualTo(3);
  }

  @Test
  public void testRetryStopsAfterFatalException() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    RetryingCallable<Integer> r =
        new RetryingCallable<Integer>(
            () -> {
              int i = counter.incrementAndGet();
              if (i < 3) {
                throw new Exception("nope");
              }
              return i;
            }) {
          @Override
          protected boolean isFatalException(Exception e) {
            return true;
          }
        };

    try {
      r.call();
      Assert.fail("got no exception, wants an exception to be thrown");
    } catch (Exception e) {
      // Expected to throw an exception
    }
    assertThat(counter.get()).isEqualTo(1);
  }
}
