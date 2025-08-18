/*
 * Copyright 2025 Google LLC
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
import static org.junit.Assert.assertThrows;

import java.util.Collection;
import javax.naming.NameNotFoundException;
import org.junit.Test;

public class DnsJavaResolverTest {

  private static final String VALID_DOMAIN_NAME = "invalid-san-test.csqlconnectortest.com";
  private static final String VALID_DOMAIN_NAME_DATA =
      "cloud-sql-connector-testing:us-central1:postgres-customer-cas-test";
  private static final String INVALID_DOMAIN_NAME = "not-a-real-domain.com";

  @Test
  public void testResolveTxt_validDomainName() throws NameNotFoundException {
    DnsJavaResolver resolver = new DnsJavaResolver();
    Collection<String> records = resolver.resolveTxt(VALID_DOMAIN_NAME);
    assertThat(records).isNotEmpty();

    // Check that the record contains the expected IP address.
    assertThat(records).contains(VALID_DOMAIN_NAME_DATA);
  }

  @Test
  public void testResolveTxt_notFound() throws NameNotFoundException {
    DnsJavaResolver resolver = new DnsJavaResolver();
    Collection<String> records = resolver.resolveTxt(INVALID_DOMAIN_NAME);
    assertThat(records).isEmpty();
  }

  @Test
  public void testDnsJavaResolver_invalidDnsServer() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> new DnsJavaResolver("not-a-real-dns-server", 53));
    assertThat(ex).hasMessageThat().contains("Unknown DNS server host");
  }

  @Test
  public void testDnsJavaResolver_validDnsServer() throws NameNotFoundException {
    // Use a public DNS server to resolve a real domain.
    // Note: "8.8.8.8" is Google's public DNS Server
    DnsJavaResolver resolver = new DnsJavaResolver("8.8.8.8", 53);
    Collection<String> records = resolver.resolveTxt(VALID_DOMAIN_NAME);
    assertThat(records).isNotEmpty();
    assertThat(records).contains(VALID_DOMAIN_NAME_DATA);
  }
}
