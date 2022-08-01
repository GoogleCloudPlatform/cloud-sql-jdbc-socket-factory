/*
 * Copyright 2022 Google Inc. All Rights Reserved.
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

import java.util.Objects;
import java.util.Optional;

public class CloudSqlInstanceKey {
  private final String instanceName;
  private final Optional<Boolean> enableIamAuth;

  private CloudSqlInstanceKey(String instanceName, Optional<Boolean> enableIamAuth) {
    this.instanceName = instanceName;
    this.enableIamAuth = enableIamAuth;
  }

  public static CloudSqlInstanceKey create(String instanceName, boolean enableIamAuthn) {
    return new CloudSqlInstanceKey(instanceName, Optional.of(enableIamAuthn));
  }

  public static CloudSqlInstanceKey matchingInstanceName(String instanceName) {
    return new CloudSqlInstanceKey(instanceName, Optional.empty());
  }

  public String getInstanceName() {
    return instanceName;
  }

  public Optional<Boolean> getEnableIamAuth() {
    return enableIamAuth;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CloudSqlInstanceKey)) {
      return false;
    }
    CloudSqlInstanceKey that = (CloudSqlInstanceKey) o;
    return Objects.equals(instanceName, that.instanceName) && Objects.equals(
        enableIamAuth, that.enableIamAuth);
  }

  @Override
  public int hashCode() {
    return Objects.hash(instanceName, enableIamAuth);
  }
}