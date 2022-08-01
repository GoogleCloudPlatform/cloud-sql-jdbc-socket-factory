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

  public static CloudSqlInstanceKey Create(String instanceName, boolean enableIamAuthn) {
    return new CloudSqlInstanceKey(instanceName, Optional.of(enableIamAuthn));
  }

  public static CloudSqlInstanceKey MatchInstance(String instanceName) {
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
