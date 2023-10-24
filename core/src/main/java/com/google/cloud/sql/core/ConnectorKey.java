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

import com.google.cloud.sql.ConnectionConfig;
import com.google.common.base.Objects;

class ConnectorKey {
  private final ConnectionConfig config;

  public ConnectorKey(ConnectionConfig config) {
    this.config = config;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConnectorKey)) {
      return false;
    }
    ConnectorKey that = (ConnectorKey) o;
    return Objects.equal(config.getAdminRootUrl(), that.config.getAdminRootUrl())
        && Objects.equal(config.getAdminServicePath(), that.config.getAdminServicePath())
        && Objects.equal(config.getTargetPrincipal(), that.config.getTargetPrincipal())
        && Objects.equal(config.getDelegates(), that.config.getDelegates())
        && Objects.equal(config.getAuthType(), that.config.getAuthType());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        config.getAdminRootUrl(),
        config.getAdminServicePath(),
        config.getTargetPrincipal(),
        config.getDelegates(),
        config.getAuthType());
  }
}
