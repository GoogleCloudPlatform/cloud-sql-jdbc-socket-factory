# Post-Quantum Cryptography (PQC) Support

Post-Quantum Cryptography (PQC) provides cryptographic algorithms that are
secure against future decryption threats from quantum computers. The Cloud SQL
Java Connector can protect the TLS 1.3 mTLS connection to your Cloud SQL
instance with the hybrid post-quantum key exchange group `X25519MLKEM768`
(`0x11ec`, ML-KEM-768 combined with X25519), for both JDBC and R2DBC
connections.

Depending on your Java Runtime (JRE) and configured JSSE providers, PQC support
is either already active in your JVM's default TLS 1.3 provider or enabled by
selecting `cloudSqlTlsProvider` (JDBC) / `TLS_PROVIDER` (R2DBC).

> **Post-quantum key exchange is opt-in when bundled Conscrypt is needed, and
> never changes existing connection behavior unless you ask it to.** Without
> setting `cloudSqlTlsProvider` / `TLS_PROVIDER`, the connector behaves
> identically to previous releases (`JDK` mode): JDBC connections automatically
> negotiate `X25519MLKEM768` whenever the JVM's default TLS 1.3 provider
> supports it (such as JDK 27+, JDK 25.0.5+, or LTS backports), while R2DBC
> connections keep their historical TLS 1.2-only configuration unless
> `TLS_PROVIDER` is set to `AUTO` or `CONSCRYPT`. Read
> [Verifying PQC is in effect](#verifying-pqc-is-in-effect) before assuming a
> deployment is protected.

## 1. Native JVM & user-registered JSSE support

Several Java runtimes and JSSE providers advertise `X25519MLKEM768` by default
over TLS 1.3:

- **JDK 27+** ([JEP 527][jep-527]) enables `X25519MLKEM768` by default in
  `SunJSSE`.
- **JDK LTS backports of JEP 527**, including **JDK 25.0.5+** (shipped in the
  October 2026 Critical Patch Update via `JDK-8387671`) and upcoming Oracle /
  OpenJDK LTS backports across **JDK 21, 17, 11, and 8**, also enable
  `X25519MLKEM768` by default in `SunJSSE`.
- **User-registered JSSE providers** installed at high priority in
  `Security.getProviders()`, such as BouncyCastle JSSE (`BCJSSE` from
  `org.bouncycastle:bctls-jdk18on` **1.81+** paired with `bcprov-jdk18on`) or a
  globally installed Conscrypt provider (`2.6.0+`).

On any of these runtimes, **JDBC users do not need to change code or
configuration**: once both the client JVM and the Cloud SQL instance support
`X25519MLKEM768`, the default `JDK` mode (`SSLContext.getInstance("TLSv1.3")`)
negotiates it automatically, and `AUTO` detects that the default TLS 1.3
`ClientHello` already advertises `X25519MLKEM768` (`0x11ec`) and skips loading
bundled Conscrypt.

> **R2DBC:** without `TLS_PROVIDER`, R2DBC connections keep their historical
> TLS 1.2-only configuration, and TLS 1.2 has no post-quantum key exchange.
> On every Java version, set `TLS_PROVIDER` to `AUTO` (or `CONSCRYPT`) to enable
> TLS 1.3 and post-quantum key exchange. When the JVM's default TLS 1.3 provider
> already advertises `X25519MLKEM768`, `AUTO` uses that provider via Netty's
> `SslProvider.JDK` (preventing drivers such as `r2dbc-mysql` from diverting
> onto `netty-tcnative` (bypassing the connector's JSSE provider)) without
> loading bundled Conscrypt.

On JDK 27 and later, Maven marks `org.conscrypt:conscrypt-openjdk-uber` as an
optional dependency (`jdk-27-plus` profile, `[27,)`), so Conscrypt is **not**
pulled into downstream Maven builds automatically. If a JDK 27+ application
explicitly sets `cloudSqlTlsProvider` / `TLS_PROVIDER` to `CONSCRYPT` (instead
of `JDK` or `AUTO`), add `org.conscrypt:conscrypt-openjdk-uber` directly to
your application's dependencies.

To customize which groups are offered by `SunJSSE` or Conscrypt, use the
`jdk.tls.namedGroups` system property:

```
-Djdk.tls.namedGroups=X25519MLKEM768,x25519,secp256r1
```

> **Note:** JDK 24 and JDK 25.0.0–25.0.4 ship ML-KEM as a JCA cryptographic
> primitive ([JEP 496][jep-496]) without wiring `X25519MLKEM768` into `SunJSSE`
> TLS 1.3 (`JDK 25.0.5+` adds JEP 527 TLS support). Runtimes whose default
> `SunJSSE` does not yet include JEP 527 use section 2 below.

## 2. Runtimes without built-in PQC TLS (`cloudSqlTlsProvider` / `TLS_PROVIDER`)

On Java 8 through JDK 26 releases that do not include a JEP 527 backport (and
do not register a PQC-capable JSSE provider globally), the connector can serve
its connections from [Conscrypt][conscrypt], a JSSE provider backed by
BoringSSL, which offers `X25519MLKEM768` on every Java version this connector
supports.

**For Maven builds on Java 8 through JDK 26, there is nothing to add to your
`pom.xml`.** The connector's `jdk-pre-27` Maven profile (`[1.8,27)`) includes
`org.conscrypt:conscrypt-openjdk-uber` (`2.6.2`) automatically (whereas on
JDK 27+ it is optional). Build tools that do not activate Maven POM JDK
profiles (such as Gradle) should add
`runtimeOnly("org.conscrypt:conscrypt-openjdk-uber:2.6.2")` when opting into
bundled Conscrypt via `AUTO` or `CONSCRYPT`. No global provider registration is
needed: set `cloudSqlTlsProvider` (JDBC) or `TLS_PROVIDER` (R2DBC) to `AUTO` or
`CONSCRYPT`.

JDBC:

```java
config.addDataSourceProperty("cloudSqlTlsProvider", "AUTO");
```

```
jdbc:postgresql:///<DATABASE_NAME>?cloudSqlInstance=<CLOUD_SQL_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlTlsProvider=AUTO
```

R2DBC:

```java
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(GcpConnectionFactoryProvider.TLS_PROVIDER, "AUTO")
    // ...more connection options
    .build();
```

```
r2dbc:gcp:postgres://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>?TLS_PROVIDER=AUTO
```

Named connectors can set it with
`ConnectorConfig.Builder.withSecurityProviderLibrary()`:

```java
ConnectorConfig config = new ConnectorConfig.Builder()
    .withSecurityProviderLibrary(SecurityProviderLibrary.AUTO)
    .build();
```

| Value | Behavior |
| --- | --- |
| `JDK` (default) | Use the JVM's configured `Security.getProviders()` priority chain as-is (`SSLContext.getInstance("TLSv1.3")` for JDBC; historical TLS 1.2 + Netty default provider for R2DBC) without loading bundled Conscrypt. Honors user-registered providers such as `BCJSSE` or FIPS providers, and negotiates `X25519MLKEM768` for JDBC whenever the JVM's default TLS 1.3 provider supports it. |
| `AUTO` | Inspect an in-memory TLS 1.3 `ClientHello` from the JVM's default `SSLContext` for `X25519MLKEM768` (`0x11ec`) in `supported_groups` (`0x000a`). If advertised (JDK 27+, JDK 25.0.5+ and LTS backports, user-registered `BCJSSE >= 1.81`, or global Conscrypt), use the JVM's default provider without loading bundled Conscrypt; else if `jdk.tls.namedGroups` excludes `X25519MLKEM768`, keep the JVM's default provider without loading Conscrypt; else use connector-scoped Conscrypt (`2.6.0+`) where its native library loads and advertises `X25519MLKEM768`; else **silently** fall back to the JVM's default provider with a classical handshake (logged once at `INFO`). |
| `CONSCRYPT` | Require connector-scoped Conscrypt on every Java version. Fail the connection with a non-retryable error instead of falling back if Conscrypt cannot load. |

`CONSCRYPT` is the strictest option when a deployment specifically requires
BoringSSL/Conscrypt and wants an unusable native library to fail fast rather
than completing a classical handshake. `AUTO` is recommended for general PQC
adoption across mixed JDK versions, LTS backports, and container platforms: it
automatically prefers native or user-registered PQC providers when present, uses
bundled Conscrypt when needed, and falls back cleanly (logging once at `INFO`)
where Conscrypt's native library is unavailable.

The default is `JDK` so upgrading the connector introduces zero behavior change
for existing applications. When bundled Conscrypt is selected, the connector
requests an `SSLContext` from a connector-owned `Provider` instance rather than
by name, so the JVM's global security provider list and the TLS behavior of the
rest of the application are untouched.

The setting is connector-scoped: it is part of `ConnectorConfig`, so
connections that use different values are served by separate connectors, each
with its own certificate refresh cache. Named connectors set it with
`ConnectorConfig.Builder.withSecurityProviderLibrary()` when they are
registered; the `cloudSqlTlsProvider` / `TLS_PROVIDER` connection property
applies to unnamed connectors.

### Using BouncyCastle JSSE (`BCJSSE`) on platforms without Conscrypt

The connector never reflectively instantiates unconfigured BouncyCastle JARs
that happen to be on the classpath transitively. However, if your application
explicitly registers `BouncyCastleProvider` (`BC`) and
`BouncyCastleJsseProvider` (`BCJSSE` from `org.bouncycastle:bctls-jdk18on`
**1.81 or later** with matching `org.bouncycastle:bcprov-jdk18on`) ahead of
`SunJSSE` in `Security.getProviders()` (or `java.security`), both `JDK` (for
JDBC) and `AUTO` (for JDBC and R2DBC) automatically detect `X25519MLKEM768` in
`BCJSSE`'s `ClientHello` and use it without loading Conscrypt. This provides a
pure-Java PQC option on Alpine/musl, GraalVM, or CPU architectures where
Conscrypt does not publish native binaries.

> **1-RTT tip for `BCJSSE`:** by default, `BCJSSE` includes `X25519MLKEM768` in
> `supported_groups` (`0x000a`) but sends only a classical `x25519` key share in
> its initial `ClientHello`, adding a `HelloRetryRequest` round-trip when the
> server selects `X25519MLKEM768`. Set
> `-Dorg.bouncycastle.jsse.client.earlyKeyShares=X25519MLKEM768` (or
> `X25519MLKEM768,x25519`) to send the hybrid key share in 1 RTT.

### Platform support for bundled Conscrypt

Conscrypt is a native library, published for these platforms:

| Operating system | Architectures |
| --- | --- |
| Linux (**glibc only**) | x86-64, ARM64 |
| macOS | x86-64, ARM64 |
| Windows | x86-64 |

Anywhere else, including **Alpine and other musl-based images**, 32-bit
platforms, s390x and ppc64le, Conscrypt's native library will not load. Unless
the JVM's default TLS 1.3 provider already advertises `X25519MLKEM768` (such as
JDK 27+, a JEP 527 LTS backport, or a caller-registered `BCJSSE >= 1.81`
provider), `AUTO` **silently falls back** to the JVM's default provider with
classical key exchange (logged once at `INFO`), while `CONSCRYPT` fails the
connection.

These build and runtime configurations also prevent Conscrypt's native library
from loading:

- **GraalVM native images.** Extracting and loading a JNI library at runtime is
  not supported in a native image, and Conscrypt ships no reachability
  metadata. Under native-image, `AUTO` falls back to the JVM default provider
  and `CONSCRYPT` fails. Leave `cloudSqlTlsProvider` unset or `AUTO` in a
  native image. Conscrypt support for native images is planned as a follow-up.
- **Uber jars that relocate packages.** Conscrypt's JNI symbols are bound to the
  `org.conscrypt` package name, so relocating it (for example with
  `maven-shade-plugin`'s `<relocation>`) breaks the native library. Exclude
  `org.conscrypt` from relocation.
- **Read-only or `noexec` temporary directories.** The native library is
  extracted from the jar at startup. Point it elsewhere with
  `-Dorg.conscrypt.native.workdir=/some/writable/path` if `java.io.tmpdir` is
  not usable.

### Cost

Loading Conscrypt's native library takes a fraction of a second and a few MB of
resident memory, paid once per JVM on the first connection that uses it.
Connections using `JDK`, or `AUTO` on a runtime whose default TLS 1.3 provider
already advertises `X25519MLKEM768` (or where `jdk.tls.namedGroups` excludes
`X25519MLKEM768`), never load Conscrypt. The capability probe generates an
in-memory `ClientHello` via `SSLEngine.wrap()` with zero network or disk I/O,
cached once per default `Provider` in `AUTO` and precomputed once per
certificate refresh (~1/hour) on `ConnectionMetadata` for R2DBC connections.

### FIPS

Conscrypt's published builds are not FIPS-validated. A deployment that must use
a FIPS-validated TLS stack (such as `BCFIPS` + `BCJSSE` in FIPS mode or RHEL
OpenJDK NSS-FIPS) should leave `cloudSqlTlsProvider` unset or explicitly set
`cloudSqlTlsProvider=JDK` (and `TLS_PROVIDER=JDK` for R2DBC), which keeps Cloud
SQL connections on the JVM's configured FIPS provider without loading bundled
Conscrypt.

### Middlebox compatibility & opting out (`~1.5 KB` `ClientHello`)

An `X25519MLKEM768` hybrid key share is 1,216 bytes, increasing the initial TLS
1.3 `ClientHello` record to roughly 1.4–1.5 KB instead of ~300 bytes for a
classical handshake. Legacy firewalls or middleboxes with strict single-packet
`ClientHello` inspection or constrained MTUs may drop or stall oversized
`ClientHello` records:

1. **Opt out of bundled Conscrypt** by setting `cloudSqlTlsProvider=JDK` (JDBC)
   or `TLS_PROVIDER=JDK` (R2DBC).
2. **If your JVM's built-in `SunJSSE` advertises `X25519MLKEM768` natively**
   (for example JDK 27+ or JDK 25.0.5+), restrict `jdk.tls.namedGroups` to
   classical groups (keeping `secp256r1` / `P-256` alongside `x25519` for Cloud
   SQL for SQL Server compatibility):
   ```
   -Djdk.tls.namedGroups=x25519,secp256r1,secp384r1
   ```

## How it works

When creating the `SSLContext` for an instance, the connector evaluates
`cloudSqlTlsProvider`:

1. `JDK` requests `SSLContext.getInstance("TLSv1.3")` (`provider = null`),
   using the JVM's configured `Security.getProviders()` priority order as-is and
   never loading bundled Conscrypt.
2. `AUTO` probes 1-arg `SSLContext.getInstance("TLSv1.3")` once per default
   `Provider` by generating an in-memory TLS 1.3 `ClientHello` via
   `SSLEngine.wrap()` and inspecting extension `0x000a` (`supported_groups`) for
   `0x11ec` (`X25519MLKEM768`). If advertised (or if `jdk.tls.namedGroups`
   explicitly excludes `X25519MLKEM768`), `AUTO` returns `null` without loading
   Conscrypt. Otherwise it loads a connector-scoped Conscrypt `Provider` and
   verifies once that it advertises `0x11ec` (requiring Conscrypt `2.6.0+`), or
   falls back to `null` (logging once at `INFO`) if Conscrypt is unavailable or
   older than `2.6.0`.
3. `CONSCRYPT` builds a connector-scoped Conscrypt `Provider` and requests
   `SSLContext.getInstance("TLSv1.3", provider)` from it, so BoringSSL
   establishes the database mTLS connection (logging once at `WARN` if
   `X25519MLKEM768` is not advertised, or failing fast with a non-retryable
   error if Conscrypt cannot load).

JDBC sockets are created directly from that `SSLContext`. R2DBC drivers build
their own Netty `SslContext`:

- With `JDK` (or no setting), the connector configures it identically to
  previous releases: TLS 1.2 only, with Netty's own provider selection (for
  example, an R2DBC driver may use `netty-tcnative` if it is on the classpath).
- With `AUTO` or `CONSCRYPT`, the connector enables TLS 1.3 (with TLS 1.2 as a
  fallback) and, whenever the connector selected a connector-scoped provider or
  the connector's `SSLContext` advertises `X25519MLKEM768`, selects Netty's
  `SslProvider.JDK` with `sslContextProvider()` bound to that JSSE `Provider`
  instance so drivers such as `r2dbc-mysql` do not bypass PQC via
  `netty-tcnative`.

Both `SunJSSE` and Conscrypt honor the `jdk.tls.namedGroups` system property.
Narrowing it to classical groups disables post-quantum key exchange across the
JVM (and causes `AUTO` on `SunJSSE` to observe that the default `ClientHello` no
longer advertises `X25519MLKEM768`).

## Verifying PQC is in effect

There is no standard JSSE API that reports the negotiated key exchange group yet
(see [JDK-8388519][jdk-api]), so neither the connector nor your application can
assert on it programmatically. `CONSCRYPT` guarantees that Conscrypt served the
connection, but if the server does not offer a hybrid group the handshake still
succeeds with a classical one.

**`SunJSSE` (JDK 27+, JDK 25.0.5+, and LTS backports)** names the negotiated
group in its handshake debug trace:

```
-Djavax.net.debug=ssl:handshake
```

With Conscrypt, inspect the handshake on the wire, for example with Wireshark
or `tshark` on port 3307: the `ServerHello` `key_share` extension shows the
selected group (`X25519MLKEM768` is `0x11ec`). Conscrypt and `SunJSSE` send the
hybrid key share in the initial `ClientHello`, making that record roughly
1.4–1.5 KB instead of ~300 bytes for a classical `ClientHello`.

If the negotiated group is classical (`x25519`, `secp256r1`, etc.), check in
order:

1. `cloudSqlTlsProvider` / `TLS_PROVIDER` is set to `AUTO` or `CONSCRYPT` (R2DBC
   defaults to TLS 1.2 under `JDK`, and JDBC under `JDK` only negotiates PQC
   when the JVM's default TLS 1.3 provider already advertises `X25519MLKEM768`).
2. Your logs do not show the `AUTO` fallback message. If they do, see
   [Platform support for bundled Conscrypt](#platform-support-for-bundled-conscrypt).
3. `jdk.tls.namedGroups` has not been narrowed to exclude `X25519MLKEM768`.
4. The Cloud SQL instance offers `X25519MLKEM768`. Both peers must support it.

[jep-527]: https://openjdk.org/jeps/527
[jep-496]: https://openjdk.org/jeps/496
[jdk-api]: https://bugs.openjdk.org/browse/JDK-8388519
[conscrypt]: https://github.com/google/conscrypt
