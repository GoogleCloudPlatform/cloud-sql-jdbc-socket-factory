/*
 * Copyright 2021 Google LLC
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * This class stores data that can be used to establish Cloud SQL SSL connection.
 */
public class SslData {

  private SSLContext sslContext;
  private KeyManagerFactory keyManagerFactory;
  private TrustManagerFactory trustManagerFactory;

  SslData(SSLContext sslContext, KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory) {
    this.sslContext = sslContext;
    this.keyManagerFactory = keyManagerFactory;
    this.trustManagerFactory = trustManagerFactory;
  }

  public SSLContext getSslContext() {
    return sslContext;
  }

  public void setSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
  }

  public KeyManagerFactory getKeyManagerFactory() {
    return keyManagerFactory;
  }

  public void setKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
    this.keyManagerFactory = keyManagerFactory;
  }

  public TrustManagerFactory getTrustManagerFactory() {
    return trustManagerFactory;
  }

  public void setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
    this.trustManagerFactory = trustManagerFactory;
  }
}
