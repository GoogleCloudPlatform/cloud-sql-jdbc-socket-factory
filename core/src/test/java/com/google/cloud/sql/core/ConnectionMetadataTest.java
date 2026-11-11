/*
 * Copyright 2026 Google LLC
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

import com.google.cloud.sql.IpType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnectionMetadataTest {

  @Test
  @SuppressWarnings("deprecation")
  public void testConnectionMetadata() {
    List<String> preferred = Arrays.asList("10.0.0.1", "10.0.0.2");
    Map<IpType, List<String>> ipMap = new HashMap<>();
    ipMap.put(IpType.PUBLIC, Arrays.asList("10.0.0.1", "10.0.0.2"));
    List<String> mdxSupport = Collections.singletonList("CLIENT_PROTOCOL_TYPE");

    ConnectionMetadata metadata =
        new ConnectionMetadata(preferred, ipMap, null, null, null, mdxSupport);

    assertThat(metadata.getPreferredIpAddresses()).isEqualTo(preferred);
    assertThat(metadata.getPreferredIpAddress()).isEqualTo("10.0.0.1");
    assertThat(metadata.getAllIpAddrs()).isEqualTo(ipMap);
    assertThat(metadata.getIpAddrs().get(IpType.PUBLIC)).isEqualTo("10.0.0.1");
    assertThat(metadata.getKeyManagerFactory()).isNull();
    assertThat(metadata.getTrustManagerFactory()).isNull();
    assertThat(metadata.getSslContext()).isNull();
    assertThat(metadata.getMdxProtocolSupport()).isEqualTo(mdxSupport);
    assertThat(metadata.isMdxClientProtocolTypeSupport()).isTrue();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testDeprecatedConstructor() {
    Map<IpType, String> oldMap = new HashMap<>();
    oldMap.put(IpType.PUBLIC, "10.0.0.1");

    ConnectionMetadata metadata =
        new ConnectionMetadata("10.0.0.1", oldMap, null, null, null, Collections.emptyList());

    assertThat(metadata.getPreferredIpAddress()).isEqualTo("10.0.0.1");
    assertThat(metadata.isMdxClientProtocolTypeSupport()).isFalse();
    assertThat(metadata.getIpAddrs().get(IpType.PUBLIC)).isEqualTo("10.0.0.1");
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testEmptyOrNullProperties() {
    ConnectionMetadata metadata =
        new ConnectionMetadata(Collections.emptyList(), null, null, null, null, null);

    assertThat(metadata.getPreferredIpAddress()).isNull();
    assertThat(metadata.getIpAddrs()).isNull();
    assertThat(metadata.isMdxClientProtocolTypeSupport()).isFalse();
  }
}
