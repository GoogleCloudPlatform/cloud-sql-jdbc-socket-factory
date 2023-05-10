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

import java.util.Date;
import java.util.Map;
import javax.net.ssl.SSLContext;

/** Represents the results of a certificate and metadata refresh operation. */
class InstanceData {

  private final Metadata metadata;
  private final SSLContext sslContext;
  private final SslData sslData;
  private final Date expiration;

  InstanceData(Metadata metadata, SslData sslData, Date expiration) {
    this.metadata = metadata;
    this.sslData = sslData;
    this.sslContext = sslData.getSslContext();
    this.expiration = expiration;
  }

  Date getExpiration() {
    return expiration;
  }

  SSLContext getSslContext() {
    return sslContext;
  }

  Map<String, String> getIpAddrs() {
    return metadata.getIpAddrs();
  }

  SslData getSslData() {
    return sslData;
  }
}
