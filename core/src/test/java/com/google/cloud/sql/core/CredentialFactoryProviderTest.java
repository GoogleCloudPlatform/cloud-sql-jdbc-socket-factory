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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.sql.CredentialFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CredentialFactoryProviderTest {

  @Test
  public void returnsDefaultCredentialFactory() {
    CredentialFactory factory = CredentialFactoryProvider.getCredentialFactory();
    assertThat(factory).isInstanceOf(ApplicationDefaultCredentialFactory.class);
  }

  @Test
  public void returnsUserSpecifiedCredentialFactory() {
    System.setProperty(
        CredentialFactory.CREDENTIAL_FACTORY_PROPERTY, StubCredentialFactory.class.getName());
    CredentialFactory factory = CredentialFactoryProvider.getCredentialFactory();
    assertThat(factory).isInstanceOf(StubCredentialFactory.class);
    System.clearProperty(CredentialFactory.CREDENTIAL_FACTORY_PROPERTY);
  }
}
