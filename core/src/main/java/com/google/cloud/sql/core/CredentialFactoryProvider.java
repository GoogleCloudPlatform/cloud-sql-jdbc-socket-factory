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

import com.google.cloud.sql.ApplicationDefaultCredentialFactory;
import com.google.cloud.sql.CredentialFactory;

/**
 * This class can be used to inject a credential factory based on whether
 * CREDENTIAL_FACTORY_PROPERTY is set.
 */
public class CredentialFactoryProvider {

  /**
   * Checks whether CREDENTIAL_FACTORY_PROPERTY is set and returns a CredentialFactory instance.
   */
  public static CredentialFactory getCredentialFactory() {
    String userCredentialFactoryClassName = System.getProperty(
        CredentialFactory.CREDENTIAL_FACTORY_PROPERTY);

    CredentialFactory credentialFactory;
    if (userCredentialFactoryClassName != null) {
      try {
        credentialFactory =
            (CredentialFactory)
                Class.forName(userCredentialFactoryClassName).newInstance();
      } catch (Exception err) {
        throw new RuntimeException(err);
      }
    } else {
      credentialFactory = new ApplicationDefaultCredentialFactory();
    }
    return credentialFactory;
  }
}
