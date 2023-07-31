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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** This class creates a HttpRequestInitializer from Application Default Credentials. */
class ApplicationDefaultCredentialFactory implements CredentialFactory {

  private final List<String> delegates;

  ApplicationDefaultCredentialFactory() {
    delegates = Collections.emptyList();
  }

  ApplicationDefaultCredentialFactory(List<String> delegates) {
    this.delegates = delegates;
  }

  @Override
  public HttpRequestInitializer create() {
    GoogleCredentials credentials;
    try {
      credentials = GoogleCredentials.getApplicationDefault();
    } catch (IOException err) {
      throw new RuntimeException(
          "Unable to obtain credentials to communicate with the Cloud SQL API", err);
    }

    if (credentials.createScopedRequired()) {
      credentials =
          credentials.createScoped(
              Arrays.asList(SQLAdminScopes.SQLSERVICE_ADMIN, SQLAdminScopes.CLOUD_PLATFORM));
    }

    if (delegates != null && !delegates.isEmpty()) {
      // The "delegates" property is built to work like the cloud-sql-proxy
      // --impersonate-service-account
      // and gcloud --impersonate-service-account flags: The first element in the list is the target
      // service account. Intermediate delegated credentials are applied first from end of the list,
      // working towards the beginning of the list.
      //
      // For example, if
      //
      //   delegates = Arrays.asList(
      //        "first@serviceaccount.com",
      //        "second@serviceaccount.com",
      //        "third@serviceaccount.com")
      //
      // The connector will start with the Application Default credentials, use those to impersonate
      // "third@serviceaccount.com", then with a token for "third@serviceaccount.com",
      // impersonate "second@serviceaccount.com", and then impersonate "first@serviceaccount.com".
      // Finally, the connector will attempt to access the services as "first@serviceaccount.com".
      //
      // However, the ImpersonatedCredentials.setDelegates() expects the list to be in the reverse
      // order.
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
      credentials =
          ImpersonatedCredentials.newBuilder()
              .setSourceCredentials(credentials)
              .setTargetPrincipal(reversedDelegates.get(reversedDelegates.size() - 1))
              .setDelegates(reversedDelegates.subList(0, reversedDelegates.size() - 1))
              .setScopes(
                  Arrays.asList(SQLAdminScopes.SQLSERVICE_ADMIN, SQLAdminScopes.CLOUD_PLATFORM))
              .build();
    }

    return new HttpCredentialsAdapter(credentials);
  }

  @Override
  public CredentialFactory withDelegates(List<String> delegates) {
    return new ApplicationDefaultCredentialFactory(delegates);
  }
}
