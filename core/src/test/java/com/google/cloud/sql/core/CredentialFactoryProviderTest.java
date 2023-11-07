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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CredentialFactoryProviderTest {

  @Test
  public void returnsDefaultCredentialFactory() {
    CredentialFactory factory = new CredentialFactoryProvider().getDefaultCredentialFactory();
    assertThat(factory).isInstanceOf(ApplicationDefaultCredentialFactory.class);
  }

  @Test
  public void returnsUserSpecifiedCredentialFactory() {
    System.setProperty(
        CredentialFactory.CREDENTIAL_FACTORY_PROPERTY, StubCredentialFactory.class.getName());
    CredentialFactory factory = new CredentialFactoryProvider().getDefaultCredentialFactory();
    assertThat(factory).isInstanceOf(StubCredentialFactory.class);
    System.clearProperty(CredentialFactory.CREDENTIAL_FACTORY_PROPERTY);
  }

  @Test
  public void getInstanceCredentialFactory_returnsDefaultWithNoSettings() {
    ConnectorConfig config = new ConnectorConfig.Builder().build();

    CredentialFactoryProvider f = new CredentialFactoryProvider();
    CredentialFactory got = f.getInstanceCredentialFactory(config);

    assertThat(got).isSameInstanceAs(f.getDefaultCredentialFactory());
  }

  @Test
  public void getInstanceCredentialFactory_returnsFileCredentialFactory() {
    String path = FileCredentialFactoryTest.class.getResource("/sample-credentials.json").getFile();
    ConnectorConfig config = new ConnectorConfig.Builder().withGoogleCredentialsPath(path).build();

    CredentialFactoryProvider f = new CredentialFactoryProvider();
    CredentialFactory got = f.getInstanceCredentialFactory(config);

    assertThat(got.getCredentials().getQuotaProjectId()).isEqualTo("sample");
  }

  @Test
  public void getInstanceCredentialFactory_returnsConstantCredentialFactory() {
    GoogleCredentials c = GoogleCredentials.create(null);
    ConnectorConfig config = new ConnectorConfig.Builder().withGoogleCredentials(c).build();

    CredentialFactoryProvider f = new CredentialFactoryProvider();
    CredentialFactory got = f.getInstanceCredentialFactory(config);

    assertThat(got.getCredentials()).isSameInstanceAs(c);
  }

  @Test
  public void getInstanceCredentialFactory_returnsSupplierCredentialFactory() {
    GoogleCredentials c = GoogleCredentials.create(null);
    ConnectorConfig config =
        new ConnectorConfig.Builder().withGoogleCredentialsSupplier(() -> c).build();

    CredentialFactoryProvider f = new CredentialFactoryProvider();
    CredentialFactory got = f.getInstanceCredentialFactory(config);

    assertThat(got.getCredentials()).isSameInstanceAs(c);
  }

  @Test
  public void getInstanceCredentialFactory_returnsImpersonatingCredentialFactory() {
    GoogleCredentials c = new GoogleCredentials(null);
    ConnectorConfig config =
        new ConnectorConfig.Builder()
            .withGoogleCredentials(c)
            .withTargetPrincipal("test@project.iam.googleapis.com")
            .build();

    CredentialFactoryProvider f = new CredentialFactoryProvider();
    CredentialFactory got = f.getInstanceCredentialFactory(config);

    assertThat(got).isInstanceOf(ServiceAccountImpersonatingCredentialFactory.class);
    ServiceAccountImpersonatingCredentialFactory impersonatedFactory =
        (ServiceAccountImpersonatingCredentialFactory) got;

    assertThat(impersonatedFactory.getCredentials()).isInstanceOf(ImpersonatedCredentials.class);
    ImpersonatedCredentials ic = (ImpersonatedCredentials) impersonatedFactory.getCredentials();
    assertThat(ic.getAccount()).isEqualTo("test@project.iam.googleapis.com");
    assertThat(ic.getSourceCredentials()).isSameInstanceAs(c);
  }
}
