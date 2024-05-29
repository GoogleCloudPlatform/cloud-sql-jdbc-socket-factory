/*
 * Copyright 2023 Google LLC
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A value object containing configuration needed to set up an mTLS connection to a Cloud SQL
 * instance.
 */
public class ConnectionMetadata {
  private final String preferredIpAddress;
  private final KeyManagerFactory keyManagerFactory;
  private final TrustManagerFactory trustManagerFactory;
  private final SSLContext sslContext;

  /** Construct an immutable ConnectionMetadata. */
  public ConnectionMetadata(
      String preferredIpAddress,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext) {

    this.preferredIpAddress = preferredIpAddress;
    this.keyManagerFactory = keyManagerFactory;
    this.trustManagerFactory = trustManagerFactory;
    this.sslContext = sslContext;
  }

  public String getPreferredIpAddress() {
    return preferredIpAddress;
  }

  public KeyManagerFactory getKeyManagerFactory() {
    return keyManagerFactory;
  }

  public TrustManagerFactory getTrustManagerFactory() {
    return trustManagerFactory;
  }

  public SSLContext getSslContext() {
    return sslContext;
  }
}
