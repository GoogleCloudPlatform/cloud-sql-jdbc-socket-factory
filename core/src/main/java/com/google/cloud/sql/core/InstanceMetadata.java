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

import com.google.cloud.sql.IpType;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;

/** Represents the results of @link #fetchMetadata(). */
class InstanceMetadata {

  private final CloudSqlInstanceName instanceName;
  private final Map<IpType, String> ipAddrs;
  private final List<Certificate> instanceCaCertificates;
  private final boolean casManagedCertificate;
  private final String dnsName;
  private final boolean pscEnabled;

  InstanceMetadata(
      CloudSqlInstanceName instanceName,
      Map<IpType, String> ipAddrs,
      List<Certificate> instanceCaCertificates,
      boolean casManagedCertificate,
      String dnsName,
      boolean pscEnabled) {
    this.instanceName = instanceName;
    this.ipAddrs = ipAddrs;
    this.instanceCaCertificates = instanceCaCertificates;
    this.casManagedCertificate = casManagedCertificate;
    this.dnsName = dnsName;
    this.pscEnabled = pscEnabled;
  }

  Map<IpType, String> getIpAddrs() {
    return ipAddrs;
  }

  List<Certificate> getInstanceCaCertificates() {
    return instanceCaCertificates;
  }

  public boolean isCasManagedCertificate() {
    return casManagedCertificate;
  }

  public String getDnsName() {
    return dnsName;
  }

  public boolean isPscEnabled() {
    return pscEnabled;
  }

  public CloudSqlInstanceName getInstanceName() {
    return instanceName;
  }
}
