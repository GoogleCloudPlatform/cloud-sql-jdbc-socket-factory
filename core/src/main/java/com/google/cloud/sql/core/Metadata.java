package com.google.cloud.sql.core;

import java.security.cert.Certificate;
import java.util.Map;

/**
 * Represents the results of @link #fetchMetadata().
 */
class Metadata {

  private final Map<String, String> ipAddrs;
  private final Certificate instanceCaCertificate;

  Metadata(Map<String, String> ipAddrs, Certificate instanceCaCertificate) {
    this.ipAddrs = ipAddrs;
    this.instanceCaCertificate = instanceCaCertificate;
  }

  Map<String, String> getIpAddrs() {
    return ipAddrs;
  }

  Certificate getInstanceCaCertificate() {
    return instanceCaCertificate;
  }
}