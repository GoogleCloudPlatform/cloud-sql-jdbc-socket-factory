/*
 * Copyright 2024 Google LLC
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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.security.KeyPair;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LazyRefreshConnectionInfoCacheTest {
  private ListenableFuture<KeyPair> keyPairFuture;
  private final StubCredentialFactory stubCredentialFactory =
      new StubCredentialFactory("my-token", System.currentTimeMillis() + 3600L);

  @Before
  public void setup() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    this.keyPairFuture = Futures.immediateFuture(mockAdminApi.getClientKeyPair());
  }

  @Test
  public void testCloudSqlInstanceDataLazyStrategyRetrievedSuccessfully()
      throws ExecutionException, InterruptedException {
    KeyPair kp = keyPairFuture.get();
    TestDataSupplier instanceDataSupplier = new TestDataSupplier(false);

    // initialize connectionInfoCache after mocks are set up
    LazyRefreshConnectionInfoCache connectionInfoCache =
        new LazyRefreshConnectionInfoCache(
            new ConnectionConfig.Builder().withCloudSqlInstance("project:region:instance").build(),
            instanceDataSupplier,
            stubCredentialFactory,
            kp);

    ConnectionMetadata gotMetadata = connectionInfoCache.getConnectionMetadata(300);
    ConnectionMetadata gotMetadata2 = connectionInfoCache.getConnectionMetadata(300);

    // Assert that the underlying ConnectionInfo was retrieved exactly once.
    assertThat(instanceDataSupplier.counter.get()).isEqualTo(1);

    // Assert that the ConnectionInfo fields are added to ConnectionMetadata
    assertThat(gotMetadata.getKeyManagerFactory())
        .isSameInstanceAs(instanceDataSupplier.response.getSslData().getKeyManagerFactory());
    assertThat(gotMetadata.getKeyManagerFactory())
        .isSameInstanceAs(gotMetadata2.getKeyManagerFactory());
  }
}
