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

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.sqladmin.SQLAdminScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.sql.CredentialFactory;
import java.util.Arrays;
import java.util.List;

/**
 * Wraps an existing CredentialFactory to impersonate the targetPrincipal, with an optional list of
 * delegating service accounts in accordance with the ImpersonatedCredentials API.
 *
 * <p>targetPrincipal – the service account to impersonate
 *
 * <p>delegates – the chained list of delegates required to grant the final access_token. If set,
 * the sequence of identities must have "Service Account Token Creator" capability granted to the
 * preceding identity. For example, if set to [serviceAccountB, serviceAccountC], the
 * sourceCredential must have the Token Creator role on serviceAccountB. serviceAccountB must have
 * the Token Creator on serviceAccountC. Finally, C must have Token Creator on target_principal. If
 * unset, sourceCredential must have that role on targetPrincipal.
 *
 * @see com.google.auth.oauth2.ImpersonatedCredentials
 */
class ServiceAccountImpersonatingCredentialFactory implements CredentialFactory {

  private final CredentialFactory source;
  private final List<String> delegates;
  private final String targetPrincipal;

  /**
   * Creates a new ServiceAccountImpersonatingCredentialFactory.
   *
   * @param source the source of the original credentials, before they are impersonated.
   * @param targetPrincipal The target principal in the form of a service account, must not be null.
   * @param delegates The optional list of delegate service accounts, may be null or empty.
   */
  ServiceAccountImpersonatingCredentialFactory(
      CredentialFactory source, String targetPrincipal, List<String> delegates) {
    if (targetPrincipal == null || targetPrincipal.isEmpty()) {
      throw new IllegalArgumentException("targetPrincipal must not be empty");
    }
    this.source = source;
    this.delegates = delegates;
    this.targetPrincipal = targetPrincipal;
  }

  @Override
  public HttpRequestInitializer create() {
    GoogleCredentials credentials = getCredentials();
    return new HttpCredentialsAdapter(credentials);
  }

  @Override
  public GoogleCredentials getCredentials() {
    GoogleCredentials credentials = source.getCredentials();

    credentials =
        ImpersonatedCredentials.newBuilder()
            .setSourceCredentials(credentials)
            .setTargetPrincipal(targetPrincipal)
            .setDelegates(this.delegates)
            .setScopes(
                Arrays.asList(SQLAdminScopes.SQLSERVICE_ADMIN, SQLAdminScopes.CLOUD_PLATFORM))
            .build();
    return credentials;
  }
}
