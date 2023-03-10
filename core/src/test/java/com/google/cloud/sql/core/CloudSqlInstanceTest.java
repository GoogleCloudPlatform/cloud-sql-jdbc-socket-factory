/*
 * Copyright 2022 Google LLC. All Rights Reserved.
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
package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.sql.AuthType;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {

  private final ConnectSettings instanceData = new ConnectSettings();
  @Mock
  private GoogleCredentials googleCredentials;
  @Mock
  private GoogleCredentials scopedCredentials;
  @Mock
  private OAuth2Credentials oAuth2Credentials;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(googleCredentials.createScoped(
        "https://www.googleapis.com/auth/sqlservice.login")).thenReturn(scopedCredentials);
    instanceData.setDatabaseVersion("SQLSERVER_2019_STANDARD");
  }

  @Test
  public void downscopesGoogleCredentials() {
    GoogleCredentials downscoped = SqlAdminApiFetcher.getDownscopedCredentials(googleCredentials);
    assertThat(downscoped).isEqualTo(scopedCredentials);
    verify(googleCredentials, times(1)).createScoped(
        "https://www.googleapis.com/auth/sqlservice.login");
  }


  @Test
  public void throwsErrorForWrongCredentialType() {
    try {
      SqlAdminApiFetcher.getDownscopedCredentials(oAuth2Credentials);
    } catch (RuntimeException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains("Failed to downscope credentials for IAM Authentication");
    }
  }

  @Test
  public void throwsErrorIamAuthNotSupported() {
    String connName = "my-project:region:my-instance";

    try {
      SqlAdminApiFetcher.checkDatabaseCompatibility(instanceData, AuthType.IAM, connName);
    } catch (IllegalArgumentException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains("[my-project:region:my-instance] " +
              "IAM Authentication is not supported for SQL Server instances");
    }
  }

  @Test
  public void timeUntilRefreshImmediate() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofMinutes(3)));
    assertThat(CloudSqlInstance.secondsUntilRefresh(expiration)).isEqualTo(0L);
  }

  @Test
  public void timeUntilRefresh1Hr() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofMinutes(59)));
    long expected = Duration.ofMinutes(59).minus(Duration.ofMinutes(4)).getSeconds();
    Assert.assertEquals(CloudSqlInstance.secondsUntilRefresh(expiration), expected, 1);
  }

  @Test
  public void timeUntilRefresh24Hr() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofHours(23)));
    long expected = Duration.ofHours(23).dividedBy(2).getSeconds();
    Assert.assertEquals(CloudSqlInstance.secondsUntilRefresh(expiration), expected, 1);
  }
}
