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
import static org.junit.Assert.assertThrows;

import com.google.cloud.sql.AuthType;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.naming.NameNotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DnsInstanceConnectionNameResolverTest {

  private static class FakeDnsResolver implements DnsResolver {
    private final Map<String, Collection<String>> txtEntries = new HashMap<>();
    private final Map<String, String> cnameEntries = new HashMap<>();

    public void putTxt(String name, String value) {
      txtEntries.put(name, Collections.singletonList(value));
    }

    public void putCname(String name, String target) {
      cnameEntries.put(name, target);
    }

    @Override
    public Collection<String> resolveTxt(String domainName) throws NameNotFoundException {
      if (txtEntries.containsKey(domainName)) {
        return txtEntries.get(domainName);
      }
      throw new NameNotFoundException("No TXT record for " + domainName);
    }

    @Override
    public List<InetAddress> resolveHost(String hostName) throws UnknownHostException {
      throw new UnknownHostException("No host resolution for " + hostName);
    }

    @Override
    public String resolveCname(String domainName) throws NameNotFoundException {
      if (cnameEntries.containsKey(domainName)) {
        return cnameEntries.get(domainName);
      }
      throw new NameNotFoundException("No CNAME record for " + domainName);
    }
  }

  private static class FakeConnectionInfoRepository implements ConnectionInfoRepository {
    private final Map<String, String> resolvedNames = new HashMap<>();

    public void putResolution(String region, String dnsName, String connectionName) {
      resolvedNames.put(region + ":" + dnsName, connectionName);
    }

    @Override
    public String resolveConnectionName(String region, String dnsName) {
      String key = region + ":" + dnsName;
      if (resolvedNames.containsKey(key)) {
        return resolvedNames.get(key);
      }
      throw new RuntimeException("Failed to resolve PSC DNS name: " + dnsName);
    }

    @Override
    public ListenableFuture<ConnectionInfo> getConnectionInfo(
        CloudSqlInstanceName instanceName,
        AccessTokenSupplier accessTokenSupplier,
        AuthType authType,
        ListeningScheduledExecutorService executor,
        ListenableFuture<KeyPair> keyPair) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ConnectionInfo getConnectionInfoSync(
        CloudSqlInstanceName instanceName,
        AccessTokenSupplier accessTokenSupplier,
        AuthType authType,
        KeyPair keyPair) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testResolve_validInstanceName() {
    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(
            new FakeDnsResolver(), new FakeConnectionInfoRepository());
    CloudSqlInstanceName name = resolver.resolve("my-project:my-region:my-instance");
    assertThat(name.getConnectionName()).isEqualTo("my-project:my-region:my-instance");
    assertThat(name.getProjectId()).isEqualTo("my-project");
    assertThat(name.getRegionId()).isEqualTo("my-region");
    assertThat(name.getInstanceId()).isEqualTo("my-instance");
    assertThat(name.getDomainName()).isNull();
  }

  @Test
  public void testResolve_success_txtRecord() {
    FakeDnsResolver fakeDns = new FakeDnsResolver();
    fakeDns.putTxt("db.example.com", "my-project:my-region:my-instance");

    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(fakeDns, new FakeConnectionInfoRepository());
    CloudSqlInstanceName name = resolver.resolve("db.example.com");
    assertThat(name.getConnectionName()).isEqualTo("my-project:my-region:my-instance");
    assertThat(name.getDomainName()).isEqualTo("db.example.com");
  }

  @Test
  public void testResolve_success_directPsc() throws Exception {
    String dnsName = "0123456789ab.fedcba9876543.europe-north2.sql-psc.goog";
    String realConnectionName = "my-project:europe-north2:my-instance";

    FakeConnectionInfoRepository fakeRepo = new FakeConnectionInfoRepository();
    fakeRepo.putResolution("europe-north2", dnsName + ".", realConnectionName);

    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(new FakeDnsResolver(), fakeRepo);

    CloudSqlInstanceName name = resolver.resolve(dnsName);
    assertThat(name.getConnectionName()).isEqualTo(realConnectionName);
    assertThat(name.getDomainName()).isEqualTo(dnsName);
    assertThat(name.getProjectId()).isEqualTo("my-project");
    assertThat(name.getRegionId()).isEqualTo("europe-north2");
    assertThat(name.getInstanceId()).isEqualTo("my-instance");
  }

  @Test
  public void testResolve_success_cnamePsc() throws Exception {
    String dnsName = "db.example.com";
    String cnameTarget = "0123456789ab.fedcba9876543.europe-north2.sql-psc.goog";
    String realConnectionName = "my-project:europe-north2:my-instance";

    FakeConnectionInfoRepository fakeRepo = new FakeConnectionInfoRepository();
    fakeRepo.putResolution("europe-north2", cnameTarget + ".", realConnectionName);

    FakeDnsResolver fakeDns = new FakeDnsResolver();
    fakeDns.putCname(dnsName, cnameTarget);

    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(fakeDns, fakeRepo);

    CloudSqlInstanceName name = resolver.resolve(dnsName);
    assertThat(name.getConnectionName()).isEqualTo(realConnectionName);
    assertThat(name.getDomainName()).isEqualTo(dnsName);
  }

  @Test
  public void testResolve_success_cnameChainPsc() throws Exception {
    String dnsName = "name1.example.com";
    String cname2 = "name2.example.com";
    String cnameTarget = "0123456789ab.fedcba9876543.europe-north2.sql-psc.goog";
    String realConnectionName = "my-project:europe-north2:my-instance";

    FakeConnectionInfoRepository fakeRepo = new FakeConnectionInfoRepository();
    fakeRepo.putResolution("europe-north2", cnameTarget + ".", realConnectionName);

    FakeDnsResolver fakeDns = new FakeDnsResolver();
    fakeDns.putCname(dnsName, cname2);
    fakeDns.putCname(cname2, cnameTarget);

    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(fakeDns, fakeRepo);

    CloudSqlInstanceName name = resolver.resolve(dnsName);
    assertThat(name.getConnectionName()).isEqualTo(realConnectionName);
    assertThat(name.getDomainName()).isEqualTo(dnsName);
  }

  @Test
  public void testResolve_success_cnameChainTxt() {
    String dnsName = "name1.example.com";
    String cname2 = "name2.example.com";
    String cname3 = "name3.example.com";
    FakeDnsResolver fakeDns = new FakeDnsResolver();
    fakeDns.putCname(dnsName, cname2);
    fakeDns.putCname(cname2, cname3);
    fakeDns.putTxt(cname3, "my-project:my-region:my-instance");

    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(fakeDns, new FakeConnectionInfoRepository());
    CloudSqlInstanceName name = resolver.resolve(dnsName);
    assertThat(name.getConnectionName()).isEqualTo("my-project:my-region:my-instance");
    assertThat(name.getDomainName()).isEqualTo(cname3);
  }

  @Test
  public void testResolve_fails_invalidPattern() {
    String[] invalidDnsNames = {
      "0123456789ab.fedcba9876543.europe-north2.sql-psc.goog.com", // wrong suffix domain
      "0123456789ag.fedcba9876543.europe-north2.sql-psc.goog", // non-hex char 'g' in hash
      "0123456789a.fedcba9876543.europe-north2.sql-psc.goog", // wrong hash length (11)
      "0123456789abc.fedcba9876543.europe-north2.sql-psc.goog", // wrong hash length (13)
      "0123456789ab.fedcba9876543.europenorth2.sql-psc.goog", // region has no hyphen
    };

    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(
            new FakeDnsResolver(), new FakeConnectionInfoRepository());
    for (String dnsName : invalidDnsNames) {
      assertThrows(IllegalArgumentException.class, () -> resolver.resolve(dnsName));
    }
  }

  @Test
  public void testResolve_fails_cnameLoop() {
    String dnsName = "name1.example.com";
    String cname2 = "name2.example.com";
    FakeDnsResolver fakeDns = new FakeDnsResolver();
    fakeDns.putCname(dnsName, cname2);
    fakeDns.putCname(cname2, dnsName); // Loop!

    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(fakeDns, new FakeConnectionInfoRepository());
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(dnsName));
    assertThat(ex.getMessage()).contains("CNAME loop detected");
  }

  @Test
  public void testResolve_fails_cnameLimitExceeded() {
    FakeDnsResolver fakeDns = new FakeDnsResolver();
    for (int i = 1; i <= 10; i++) {
      fakeDns.putCname("name" + i + ".example.com", "name" + (i + 1) + ".example.com");
    }
    DnsInstanceConnectionNameResolver resolver =
        new DnsInstanceConnectionNameResolver(fakeDns, new FakeConnectionInfoRepository());
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("name1.example.com"));
    assertThat(ex.getMessage()).contains("CNAME lookup limit exceeded");
  }
}
