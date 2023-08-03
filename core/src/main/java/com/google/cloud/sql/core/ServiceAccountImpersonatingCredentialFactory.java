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
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;

/**
 * Wraps an existing CredentialFactory, adding service account impersonation for the list of
 * delegates.
 *
 * <p>The "delegates" property is built to work like the cloud-sql-proxy
 * --impersonate-service-account and gcloud --impersonate-service-account flags: The first element
 * in the list is the target service account. Intermediate delegated credentials are applied first
 * from end of the list, working towards the beginning of the list. For example, if <code>
 * delegates = Arrays.asList( "first@serviceaccount.com", "second@serviceaccount.com",
 * "third@serviceaccount.com")
 * </code>
 *
 * <p>The connector will start with the GoogleCredentials supplied by `source`. It will use those to
 * impersonate "third@serviceaccount.com", then with a token for "third@serviceaccount.com",
 * impersonate "second@serviceaccount.com", and then impersonate "first@serviceaccount.com".
 * Finally, the connector will attempt to access the services as "first@serviceaccount.com".
 */
class ServiceAccountImpersonatingCredentialFactory implements CredentialFactory {

  private final CredentialFactory source;
  private final List<String> delegates;
  private final String targetPrincipal;

  ServiceAccountImpersonatingCredentialFactory(CredentialFactory source, List<String> delegates) {
    if (delegates == null || delegates.isEmpty()) {
      throw new IllegalArgumentException("delegates must not be empty");
    }
    this.source = source;

    // The "delegates" property is built so that delegated credentials are applied first from end
    // of the list, working towards the beginning of the list. However, the
    // ImpersonatedCredentials.setDelegates() expects the list to be in the opposite order,
    // so we have to reverse the list.
    //
    // From the ImpersonatedCredentials doc:
    //
    //    targetPrincipal – the service account to impersonate
    //    delegates – the chained list of delegates required to grant the final access_token.
    //      If set, the sequence of identities must have "Service Account Token Creator"
    //      capability granted to the preceding identity. For example, if set to
    //      [serviceAccountB, serviceAccountC], the sourceCredential must have the Token Creator
    //      role on serviceAccountB. serviceAccountB must have the Token Creator on
    //      serviceAccountC. Finally, C must have Token Creator on target_principal.
    //      If unset, sourceCredential must have that role on targetPrincipal.
    List<String> reversedDelegates = Lists.reverse(delegates);
    this.targetPrincipal = reversedDelegates.get(reversedDelegates.size() - 1);
    this.delegates = reversedDelegates.subList(0, reversedDelegates.size() - 1);
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
