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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {

  @Mock
  private GoogleCredentials googleCredentials;

  @Mock
  private GoogleCredentials scopedCredentials;

  @Mock
  private OAuth2Credentials oAuth2Credentials;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);
    when(googleCredentials.createScoped(
        "https://www.googleapis.com/auth/sqlservice.login")).thenReturn(scopedCredentials);
  }

  @Test
  public void testFlakybot() {
    throw new RuntimeException("This error is to test that Flakybot is working");
  }

  @Test
  public void downscopesGoogleCredentials() {
    GoogleCredentials downscoped = CloudSqlInstance.getDownscopedCredentials(googleCredentials);
    assertThat(downscoped).isEqualTo(scopedCredentials);
    verify(googleCredentials, times(1)).createScoped(
        "https://www.googleapis.com/auth/sqlservice.login");
  }


  @Test
  public void throwsErrorForWrongCredentialType() {
    try {
      CloudSqlInstance.getDownscopedCredentials(oAuth2Credentials);
    } catch (RuntimeException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains("Failed to downscope credentials for IAM Authentication");
    }
  }


}
