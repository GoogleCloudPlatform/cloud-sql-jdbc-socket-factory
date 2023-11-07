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

package com.google.cloud.sql;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Objects;
import java.util.List;
import java.util.function.Supplier;

/**
 * ConnectorConfig is an immutable configuration value object that holds the entire configuration of
 * a Cloud SQL Connector that may be used to connect to multiple Cloud SQL Instances.
 */
public class ConnectorConfig {

  // go into ConnectorConfig
  private final String targetPrincipal;
  private final List<String> delegates;
  private final String adminRootUrl;
  private final String adminServicePath;
  private final Supplier<GoogleCredentials> googleCredentialsSupplier;
  private final GoogleCredentials googleCredentials;
  private final String googleCredentialsPath;

  private ConnectorConfig(
      String targetPrincipal,
      List<String> delegates,
      String adminRootUrl,
      String adminServicePath,
      Supplier<GoogleCredentials> googleCredentialsSupplier,
      GoogleCredentials googleCredentials,
      String googleCredentialsPath) {
    this.targetPrincipal = targetPrincipal;
    this.delegates = delegates;
    this.adminRootUrl = adminRootUrl;
    this.adminServicePath = adminServicePath;
    this.googleCredentialsSupplier = googleCredentialsSupplier;
    this.googleCredentials = googleCredentials;
    this.googleCredentialsPath = googleCredentialsPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConnectorConfig)) {
      return false;
    }
    ConnectorConfig that = (ConnectorConfig) o;
    return Objects.equal(targetPrincipal, that.targetPrincipal)
        && Objects.equal(delegates, that.delegates)
        && Objects.equal(adminRootUrl, that.adminRootUrl)
        && Objects.equal(adminServicePath, that.adminServicePath)
        && Objects.equal(googleCredentialsSupplier, that.googleCredentialsSupplier)
        && Objects.equal(googleCredentials, that.googleCredentials)
        && Objects.equal(googleCredentialsPath, that.googleCredentialsPath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        targetPrincipal,
        delegates,
        adminRootUrl,
        adminServicePath,
        googleCredentialsSupplier,
        googleCredentials,
        googleCredentialsPath);
  }

  public String getTargetPrincipal() {
    return targetPrincipal;
  }

  public List<String> getDelegates() {
    return delegates;
  }

  public String getAdminRootUrl() {
    return adminRootUrl;
  }

  public String getAdminServicePath() {
    return adminServicePath;
  }

  public Supplier<GoogleCredentials> getGoogleCredentialsSupplier() {
    return googleCredentialsSupplier;
  }

  public GoogleCredentials getGoogleCredentials() {
    return googleCredentials;
  }

  public String getGoogleCredentialsPath() {
    return googleCredentialsPath;
  }

  /** The builder for the ConnectionConfig. */
  public static class Builder {

    private String targetPrincipal;
    private List<String> delegates;
    private String adminRootUrl;
    private String adminServicePath;
    private Supplier<GoogleCredentials> googleCredentialsSupplier;
    private GoogleCredentials googleCredentials;
    private String googleCredentialsPath;

    public Builder withTargetPrincipal(String targetPrincipal) {
      this.targetPrincipal = targetPrincipal;
      return this;
    }

    public Builder withGoogleCredentialsSupplier(
        Supplier<GoogleCredentials> googleCredentialsSupplier) {
      this.googleCredentialsSupplier = googleCredentialsSupplier;
      return this;
    }

    public Builder withGoogleCredentials(GoogleCredentials googleCredentials) {
      this.googleCredentials = googleCredentials;
      return this;
    }

    public Builder withGoogleCredentialsPath(String googleCredentialsPath) {
      this.googleCredentialsPath = googleCredentialsPath;
      return this;
    }

    public Builder withDelegates(List<String> delegates) {
      this.delegates = delegates;
      return this;
    }

    public Builder withAdminRootUrl(String adminRootUrl) {
      this.adminRootUrl = adminRootUrl;
      return this;
    }

    public Builder withAdminServicePath(String adminServicePath) {
      this.adminServicePath = adminServicePath;
      return this;
    }

    /** Builds a new instance of {@code ConnectionConfig}. */
    public ConnectorConfig build() {
      return new ConnectorConfig(
          targetPrincipal,
          delegates,
          adminRootUrl,
          adminServicePath,
          googleCredentialsSupplier,
          googleCredentials,
          googleCredentialsPath);
    }
  }
}
