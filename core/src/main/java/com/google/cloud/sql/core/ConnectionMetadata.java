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

import com.google.cloud.sql.IpType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A value object containing configuration needed to set up an mTLS connection to a Cloud SQL
 * instance.
 */
public class ConnectionMetadata {
  private final List<String> preferredIpAddresses;
  private final Map<IpType, List<String>> ipAddrs;
  private final KeyManagerFactory keyManagerFactory;
  private final TrustManagerFactory trustManagerFactory;
  private final SSLContext sslContext;
  private final List<String> mdxProtocolSupport;

  /** Construct an immutable ConnectionMetadata. */
  public ConnectionMetadata(
      List<String> preferredIpAddresses,
      Map<IpType, List<String>> ipAddrs,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext,
      List<String> mdxProtocolSupport) {

    this.preferredIpAddresses = preferredIpAddresses;
    this.ipAddrs = ipAddrs;
    this.keyManagerFactory = keyManagerFactory;
    this.trustManagerFactory = trustManagerFactory;
    this.sslContext = sslContext;
    this.mdxProtocolSupport = mdxProtocolSupport;
  }

  /** Construct an immutable ConnectionMetadata (Deprecated). */
  @Deprecated
  public ConnectionMetadata(
      String preferredIpAddress,
      Map<IpType, String> ipAddrs,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext,
      List<String> mdxProtocolSupport) {
    this(
        Collections.singletonList(preferredIpAddress),
        compatMap(ipAddrs),
        keyManagerFactory,
        trustManagerFactory,
        sslContext,
        mdxProtocolSupport);
  }

  private static Map<IpType, List<String>> compatMap(Map<IpType, String> map) {
    Map<IpType, List<String>> newMap = new HashMap<>();
    if (map != null) {
      map.forEach((k, v) -> newMap.put(k, Collections.singletonList(v)));
    }
    return newMap;
  }

  @Deprecated
  public String getPreferredIpAddress() {
    return preferredIpAddresses == null || preferredIpAddresses.isEmpty()
        ? null
        : preferredIpAddresses.get(0);
  }

  public List<String> getPreferredIpAddresses() {
    return preferredIpAddresses;
  }

  @Deprecated
  public Map<IpType, String> getIpAddrs() {
    if (ipAddrs == null) {
      return null;
    }
    return ipAddrs.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().isEmpty() ? null : e.getValue().get(0)));
  }

  public Map<IpType, List<String>> getAllIpAddrs() {
    return ipAddrs;
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

  public List<String> getMdxProtocolSupport() {
    return mdxProtocolSupport;
  }

  public boolean isMdxClientProtocolTypeSupport() {
    return mdxProtocolSupport != null && mdxProtocolSupport.contains("CLIENT_PROTOCOL_TYPE");
  }
}
