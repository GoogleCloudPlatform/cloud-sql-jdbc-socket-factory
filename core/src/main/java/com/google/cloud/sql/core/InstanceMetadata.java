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
import java.util.Map;

/** Represents the results of @link #fetchMetadata(). */
class InstanceMetadata {

  private final Map<IpType, String> ipAddrs;
  private final Certificate instanceCaCertificate;

  InstanceMetadata(Map<IpType, String> ipAddrs, Certificate instanceCaCertificate) {
    this.ipAddrs = ipAddrs;
    this.instanceCaCertificate = instanceCaCertificate;
  }

  Map<IpType, String> getIpAddrs() {
    return ipAddrs;
  }

  Certificate getInstanceCaCertificate() {
    return instanceCaCertificate;
  }
}
