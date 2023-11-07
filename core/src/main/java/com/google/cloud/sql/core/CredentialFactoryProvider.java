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

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;

/**
 * This class can be used to inject a credential factory based on whether
 * CREDENTIAL_FACTORY_PROPERTY is set.
 */
class CredentialFactoryProvider {

  /** Returns a CredentialFactory instance based on whether CREDENTIAL_FACTORY_PROPERTY is set. */
  static CredentialFactory getCredentialFactory() {
    String userCredentialFactoryClassName =
        System.getProperty(CredentialFactory.CREDENTIAL_FACTORY_PROPERTY);

    CredentialFactory credentialFactory;
    if (userCredentialFactoryClassName != null) {
      try {
        credentialFactory =
            Class.forName(userCredentialFactoryClassName)
                .asSubclass(CredentialFactory.class)
                .getDeclaredConstructor()
                .newInstance();
      } catch (Exception err) {
        throw new RuntimeException(err);
      }
    } else {
      credentialFactory = new ApplicationDefaultCredentialFactory();
    }
    return credentialFactory;
  }

  static CredentialFactory getInstanceCredentialFactory(
      CredentialFactory defaultCredentialFactory, ConnectorConfig config) {

    CredentialFactory instanceCredentialFactory;
    if (config.getGoogleCredentialsSupplier() != null) {
      instanceCredentialFactory =
          new SupplierCredentialFactory(config.getGoogleCredentialsSupplier());
    } else if (config.getGoogleCredentials() != null) {
      instanceCredentialFactory = new ConstantCredentialFactory(config.getGoogleCredentials());
    } else if (config.getGoogleCredentialsPath() != null) {
      instanceCredentialFactory = new FileCredentialFactory(config.getGoogleCredentialsPath());
    } else {
      instanceCredentialFactory = defaultCredentialFactory;
    }

    // Validate targetPrincipal and delegates
    if (config.getTargetPrincipal() == null
        && config.getDelegates() != null
        && !config.getDelegates().isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Connection property %s must be when %s is set.",
              ConnectionConfig.CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY,
              ConnectionConfig.CLOUD_SQL_DELEGATES_PROPERTY));
    }

    // If targetPrincipal and delegates are set, then
    if (config.getTargetPrincipal() != null && !config.getTargetPrincipal().isEmpty()) {
      instanceCredentialFactory =
          new ServiceAccountImpersonatingCredentialFactory(
              instanceCredentialFactory, config.getTargetPrincipal(), config.getDelegates());
    }

    return instanceCredentialFactory;
  }
}
