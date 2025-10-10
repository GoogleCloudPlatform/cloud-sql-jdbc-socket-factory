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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApiClientRetryingCallableTest {
  @Test
  public void testApiClientRetriesOn500ErrorAndSucceeds() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    ApiClientRetryingCallable<Integer> c =
        new ApiClientRetryingCallable<>(
            () -> {
              int attempt = counter.incrementAndGet();
              if (attempt < 3) {
                throw new HttpResponseException.Builder(
                        503, "service unavailable", new HttpHeaders())
                    .build();
              }
              return attempt;
            });

    Integer v = c.call();
    assertThat(counter.get()).isEqualTo(3);
    assertThat(v).isEqualTo(3);
  }

  @Test
  public void testApiClientRetriesOn500ErrorAndFailsAfter5Attempts() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    ApiClientRetryingCallable<Integer> c =
        new ApiClientRetryingCallable<>(
            () -> {
              counter.incrementAndGet();
              throw new HttpResponseException.Builder(503, "service unavailable", new HttpHeaders())
                  .build();
            });

    try {
      c.call();
      Assert.fail("got no exception, wants an exception to be thrown");
    } catch (Exception e) {
      // Expected to throw an exception
    }
    assertThat(counter.get()).isEqualTo(5);
  }

  @Test
  public void testRetryStopsAfterFatalException() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    RetryingCallable<Integer> r =
        new RetryingCallable<Integer>(
            () -> {
              counter.incrementAndGet();
              throw new Exception("nope");
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
