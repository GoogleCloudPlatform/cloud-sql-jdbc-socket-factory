package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {

  private static final String PUBLIC_IP = "127.0.0.1";

  @Mock private SQLAdmin sqlAdminClient;
  @Mock private SQLAdmin.Instances sqlAdminClientInstances;
  @Mock private SQLAdmin.Instances.Get sqlAdminClientInstancesGet;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);

    when(sqlAdminClient.instances()).thenReturn(sqlAdminClientInstances);
    when(sqlAdminClientInstances.get(anyString(), anyString()))
        .thenReturn(sqlAdminClientInstancesGet);
  }

  @Ignore
  @Test
  public void create_throwsExceptionForInvalidConnectionName() {
    String[] tests = new String[] {"foo", "foo:bar"};
    for (String test : tests) {
      try {
        // CloudSqlInstance instance = new CloudSqlInstance(test, sqlAdminClient);
        fail();
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage()).contains("Cloud SQL connection name is invalid");
      }
    }
  }

  @Ignore
  @Test
  public void create_throwsErrorForInvalidInstanceRegion() throws IOException {
    DatabaseInstance mockMetadata =
        new DatabaseInstance()
            .setBackendType("SECOND_GEN")
            .setIpAddresses(ImmutableList.of(new IpMapping().setIpAddress(PUBLIC_IP)))
            .setRegion("not_bar");
    when(sqlAdminClient.instances().get(anyString(), anyString()).execute())
        .thenReturn(mockMetadata);

    try {
      // CloudSqlInstance instance = new CloudSqlInstance("foo:bar:baz", sqlAdminClient);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage())
          .contains("The region specified for the Cloud SQL instance is incorrect");
    }
  }
}
