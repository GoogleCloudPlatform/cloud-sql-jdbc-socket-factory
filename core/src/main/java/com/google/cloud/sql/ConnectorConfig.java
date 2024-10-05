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
import java.util.function.Function;
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
  private final Function<String, String> instanceNameResolver;
  private final GoogleCredentials googleCredentials;
  private final String googleCredentialsPath;
  private final String adminQuotaProject;
  private final String universeDomain;

  private final RefreshStrategy refreshStrategy;

  private ConnectorConfig(
      String targetPrincipal,
      List<String> delegates,
      String adminRootUrl,
      String adminServicePath,
      Supplier<GoogleCredentials> googleCredentialsSupplier,
      GoogleCredentials googleCredentials,
      String googleCredentialsPath,
      String adminQuotaProject,
      String universeDomain,
      RefreshStrategy refreshStrategy,
      Function<String, String> instanceNameResolver) {
    this.targetPrincipal = targetPrincipal;
    this.delegates = delegates;
    this.adminRootUrl = adminRootUrl;
    this.adminServicePath = adminServicePath;
    this.googleCredentialsSupplier = googleCredentialsSupplier;
    this.googleCredentials = googleCredentials;
    this.googleCredentialsPath = googleCredentialsPath;
    this.adminQuotaProject = adminQuotaProject;
    this.universeDomain = universeDomain;
    this.refreshStrategy = refreshStrategy;
    this.instanceNameResolver = instanceNameResolver;
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
        && Objects.equal(googleCredentialsPath, that.googleCredentialsPath)
        && Objects.equal(adminQuotaProject, that.adminQuotaProject)
        && Objects.equal(universeDomain, that.universeDomain)
        && Objects.equal(refreshStrategy, that.refreshStrategy)
        && Objects.equal(instanceNameResolver, that.instanceNameResolver);
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
        googleCredentialsPath,
        adminQuotaProject,
        universeDomain,
        refreshStrategy,
        instanceNameResolver);
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

  public String getAdminQuotaProject() {
    return adminQuotaProject;
  }

  public String getUniverseDomain() {
    return universeDomain;
  }

  public RefreshStrategy getRefreshStrategy() {
    return refreshStrategy;
  }

  public Function<String, String> getInstanceNameResolver() {
    return instanceNameResolver;
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
    private String adminQuotaProject;
    private String universeDomain;
    private RefreshStrategy refreshStrategy = RefreshStrategy.BACKGROUND;
    private Function<String, String> instanceNameResolver;

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

    public Builder withAdminQuotaProject(String adminQuotaProject) {
      this.adminQuotaProject = adminQuotaProject;
      return this;
    }

    public Builder withUniverseDomain(String universeDomain) {
      this.universeDomain = universeDomain;
      return this;
    }

    public Builder withRefreshStrategy(RefreshStrategy refreshStrategy) {
      this.refreshStrategy = refreshStrategy;
      return this;
    }

    public Builder withInstanceNameResolver(Function<String, String> instanceNameResolver) {
      this.instanceNameResolver = instanceNameResolver;
      return this;
    }

    /** Builds a new instance of {@code ConnectionConfig}. */
    public ConnectorConfig build() {
      // validate only one GoogleCredentials configuration field set
      int googleCredsCount = 0;
      if (googleCredentials != null) {
        googleCredsCount++;
      }
      if (googleCredentialsPath != null) {
        googleCredsCount++;
      }
      if (googleCredentialsSupplier != null) {
        googleCredsCount++;
      }
      if (googleCredsCount > 1) {
        throw new IllegalStateException(
            "Invalid configuration, more than one GoogleCredentials field has a value "
                + "(googleCredentials, googleCredentialsPath, googleCredentialsSupplier)");
      }
      if (adminRootUrl != null && universeDomain != null) {
        throw new IllegalStateException(
            "Can not set Admin API Endpoint and Universe Domain together, "
                + "set only Admin API Endpoint (it already contains the universe domain)");
      }

      return new ConnectorConfig(
          targetPrincipal,
          delegates,
          adminRootUrl,
          adminServicePath,
          googleCredentialsSupplier,
          googleCredentials,
          googleCredentialsPath,
          adminQuotaProject,
          universeDomain,
          refreshStrategy,
          instanceNameResolver);
    }
  }
}
