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
    when(googleCredentials.createScoped()).thenReturn(scopedCredentials);
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
