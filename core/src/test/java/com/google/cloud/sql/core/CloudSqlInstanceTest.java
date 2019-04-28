package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.services.sqladmin.SQLAdmin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {

  @Mock private SQLAdmin sqlAdminClient;

  @Test
  public void throwsExceptionForInvalidConnectionName() {
    String[] tests = new String[] {"foo", "foo:bar"};
    for (String test : tests) {
      try {
        CloudSqlInstance instance = new CloudSqlInstance(test, sqlAdminClient);
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage()).contains("Cloud SQL connection name is invalid");
      }
    }
  }
}
