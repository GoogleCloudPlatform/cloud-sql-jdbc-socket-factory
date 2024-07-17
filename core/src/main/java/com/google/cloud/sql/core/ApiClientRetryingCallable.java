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

import com.google.api.client.http.HttpResponseException;
import java.util.concurrent.Callable;

/**
 * Extends RetryingCallable with logic to only retry on HTTP errors with error codes in the 5xx
 * range.
 *
 * @param <T> the return value for Callable
 */
class ApiClientRetryingCallable<T> extends RetryingCallable<T> {

  /**
   * Construct a new RetryLogic.
   *
   * @param callable the callable that should be retried
   */
  public ApiClientRetryingCallable(Callable<T> callable) {
    super(callable);
  }

  /**
   * Returns false indicating that there should be another attempt if the exception is an HTTP
   * response with an error code in the 5xx range.
   *
   * @param e the exception
   * @return false if this is a http response with a 5xx status code, otherwise true.
   */
  @Override
  protected boolean isFatalException(Exception e) {
    // Only retry if the error is an HTTP response with a 5xx error code.
    if (e instanceof HttpResponseException) {
      HttpResponseException re = (HttpResponseException) e;
      return re.getStatusCode() < 500;
    }
    // Otherwise this is a fatal exception, no more tries.
    return true;
  }
}
