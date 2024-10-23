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

/** Provide the refresh strategy to the DefaultConnectionInfoCache. */
public interface RefreshStrategy {
  /** Return the current valid ConnectionInfo, blocking if necessary for up to the timeout. */
  ConnectionInfo getConnectionInfo(long timeoutMs);

  /** Force a refresh of the ConnectionInfo, possibly in the background. */
  void forceRefresh();

  /** Refresh the ConnectionInfo if it has expired or is near expiration. */
  void refreshIfExpired();

  /**
   * Stop background threads and refresh operations in progress and refuse to start subsequent
   * refresh operations.
   */
  void close();
}
