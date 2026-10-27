# Post-Quantum Cryptography (PQC) Support

Post-Quantum Cryptography (PQC) provides cryptographic algorithms that are
secure against future decryption threats from quantum computers. The Cloud SQL
Java Connector can protect the TLS 1.3 mTLS connection to your Cloud SQL
instance with the hybrid post-quantum key exchange group `X25519MLKEM768`
(ML-KEM combined with X25519), for both JDBC and R2DBC connections.

Depending on your Java Runtime (JRE) version, PQC support is either native or
requires selecting a JSSE provider that offers it.

> **Post-quantum key exchange is opt-in, and never breaks a connection unless
> you ask it to.** JDBC connections get it automatically on JDK 27+; R2DBC
> connections, and JDBC connections before JDK 27, need `cloudSqlTlsProvider` /
> `TLS_PROVIDER` set to `AUTO` or `CONSCRYPT`. When it is unavailable the
> connector completes a classical TLS handshake instead. Without the setting,
> the connector behaves exactly as it did before the setting existed. Read
> [Verifying PQC is in effect](#verifying-pqc-is-in-effect) before assuming a
> deployment is protected.

## 1. Native JRE support (JDK 27+)

[JEP 527][jep-527] adds hybrid post-quantum key agreement groups, including
`X25519MLKEM768`, to the JRE's default `SunJSSE` provider, and enables
`X25519MLKEM768` by default. On JDK 27 and later **JDBC users do not need to
do anything**: once both the client JDK and the Cloud SQL instance support it,
the connector negotiates `X25519MLKEM768` with no additional code or
configuration.

> **R2DBC:** without `TLS_PROVIDER`, R2DBC connections keep their historical
> TLS 1.2-only configuration, and TLS 1.2 has no post-quantum key exchange.
> On every Java version, set `TLS_PROVIDER` to `AUTO` (or `CONSCRYPT`) to use
> TLS 1.3 and post-quantum key exchange. On JDK 27+ `AUTO` uses the JRE's own
> provider; Conscrypt is not loaded.

To change which groups are offered, use the `jdk.tls.namedGroups` system
property:

```
-Djdk.tls.namedGroups=X25519MLKEM768,x25519,secp256r1
```

> **Note:** JDK 24 ships ML-KEM as a standalone cryptographic primitive
> ([JEP 496][jep-496]) but does **not** wire it into TLS. Java 8 through JDK 26
> all require the step in section 2 to use PQC on TLS connections.

## 2. JREs without native PQC (Java 8 through JDK 26)

On these releases the default JRE provider (`SunJSSE`) does not support
post-quantum key exchange. The connector can instead serve its connections from
[Conscrypt][conscrypt], a JSSE provider backed by BoringSSL, which offers
`X25519MLKEM768` on every Java version this connector supports.

**There is nothing to add to your build.** Conscrypt is already a dependency of
the Cloud SQL Java Connector. There are no providers to register and no startup
code to write. Set one property, `cloudSqlTlsProvider` (JDBC) or
`TLS_PROVIDER` (R2DBC), to `AUTO` or `CONSCRYPT`.

JDBC:

```java
config.addDataSourceProperty("cloudSqlTlsProvider", "CONSCRYPT");
```

```
jdbc:postgresql:///<DATABASE_NAME>?cloudSqlInstance=<CLOUD_SQL_CONNECTION_NAME>&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlTlsProvider=CONSCRYPT
```

R2DBC:

```java
ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
    .option(GcpConnectionFactoryProvider.TLS_PROVIDER, "CONSCRYPT")
    // ...more connection options
    .build();
```

```
r2dbc:gcp:postgres://<DB_USER>:<DB_PASS>@<CLOUD_SQL_CONNECTION_NAME>/<DATABASE_NAME>?TLS_PROVIDER=CONSCRYPT
```

Named connectors can set it with `ConnectorConfig.Builder.withTlsProvider()`:

```java
ConnectorConfig config = new ConnectorConfig.Builder()
    .withTlsProvider(TlsProvider.CONSCRYPT)
    .build();
```

| Value | Behavior |
| --- | --- |
| `JDK` (default) | Always use the JRE's default provider; behavior is unchanged from before the setting existed. For JDBC on JDK 27+ this is already post-quantum; before that it is not. R2DBC stays on TLS 1.2 (not post-quantum). |
| `AUTO` | Get post-quantum key exchange wherever it is available: the JRE's own provider on JDK 27+, otherwise Conscrypt where its native library loads, otherwise **silently** fall back to the JRE's provider and a classical handshake. |
| `CONSCRYPT` | Require Conscrypt on every Java version. Fail the connection instead of falling back. |

`CONSCRYPT` is the value to prefer for a deployment that intends to use PQC: an
unusable provider fails every connection attempt with an error, rather than
completing a quiet classical handshake. `AUTO` suits a single artifact deployed
to a mix of platforms, some of which Conscrypt does not support; it logs once,
at INFO, when it falls back.

The default is `JDK` so that selecting a different TLS implementation for Cloud
SQL traffic is always a deliberate choice. The connector requests an
`SSLContext` from Conscrypt for its own connections only, as a `Provider`
instance rather than by name, so the JRE's global security provider list and
the TLS behavior of everything else in the application are untouched.

The setting is connector-scoped: it is part of `ConnectorConfig`, so
connections that use different values are served by separate connectors, each
with its own certificate refresh cache. Named connectors set it with
`ConnectorConfig.Builder.withTlsProvider()` when they are registered; the
`cloudSqlTlsProvider` / `TLS_PROVIDER` connection property applies to unnamed
connectors.

### Platform support

Conscrypt is a native library, published for these platforms:

| Operating system | Architectures |
| --- | --- |
| Linux (**glibc only**) | x86-64, ARM64 |
| macOS | x86-64, ARM64 |
| Windows | x86-64 |

Anywhere else, including **Alpine and other musl-based images**, 32-bit
platforms, s390x and ppc64le, the native library will not load. `AUTO`
**silently falls back** to the JRE provider (classical key exchange, logged
once at INFO); `CONSCRYPT` fails the connection. There is no post-quantum
option for those platforms before JDK 27.

These build and runtime configurations also prevent the library from loading:

- **GraalVM native images.** Extracting and loading a JNI library at runtime is
  not supported in a native image, and Conscrypt ships no reachability
  metadata. Under native-image, `AUTO` falls back to the JDK provider
  (classical key exchange) and `CONSCRYPT` fails. Leave `cloudSqlTlsProvider`
  unset (or `AUTO`) in a native image. Conscrypt support for native images is
  planned as a follow-up.
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
resident memory, paid once per JVM, on the first connection that uses it.
Connections using the default `JDK` provider never load it. This matters most
for short-lived or cold-starting workloads such as Cloud Run services and Cloud
Functions, where it lands in the first connection's latency.

### FIPS

Conscrypt's published builds are not FIPS builds. A deployment that must use a
FIPS-validated TLS stack should leave `cloudSqlTlsProvider` unset, which keeps
Cloud SQL connections on the JRE's own provider.

## How it works

When creating the SSLContext for an instance, the connector reads
`cloudSqlTlsProvider`:

1. `JDK` requests `SSLContext.getInstance("TLSv1.3")` from the JVM's default
   provider, and never loads Conscrypt.
2. `AUTO` on JDK 27 and later behaves exactly like `JDK`, because the JRE
   already negotiates ML-KEM. On earlier versions it behaves like `CONSCRYPT`,
   except that it falls back to `JDK` when Conscrypt cannot be loaded.
3. `CONSCRYPT` builds a connector-scoped Conscrypt `Provider` and requests
   `SSLContext.getInstance("TLSv1.3", provider)` from it, so BoringSSL
   establishes the database mTLS connection.

JDBC sockets are created from that `SSLContext`. R2DBC drivers build their own
Netty `SslContext`:

- With `JDK` (or no setting), the connector configures it exactly as before
  the setting existed: TLS 1.2 only, and Netty's own provider selection (for
  example, an R2DBC driver may use `netty-tcnative` if it is on the
  classpath).
- With `AUTO` or `CONSCRYPT`, the connector enables TLS 1.3 (TLS 1.2 is only a
  fallback) and, when it chose Conscrypt, selects Netty's JDK `SslProvider`
  with `sslContextProvider()` set to the same connector-scoped Conscrypt
  `Provider` instance.

Conscrypt honors the `jdk.tls.namedGroups` system property. Narrowing it to
classical groups disables post-quantum key exchange for Cloud SQL connections
as well as everything else in the JVM.

## Verifying PQC is in effect

There is no JSSE API that reports the negotiated key exchange group yet (see
[JDK-8388519][jdk-api]), so neither the connector nor your application can
assert on it programmatically. `CONSCRYPT` guarantees that Conscrypt served the
connection, but if the server does not offer a hybrid group the handshake still
succeeds with a classical one.

**JDK 27+ using the default `SunJSSE` provider** names the group in its
handshake trace:

```
-Djavax.net.debug=ssl:handshake
```

With Conscrypt, inspect the handshake on the wire, for example with Wireshark
or `tshark` on port 3307: the ServerHello `key_share` extension shows the
selected group (`X25519MLKEM768` is `0x11ec`). Conscrypt sends the hybrid key
share in its first ClientHello, which makes that record roughly 1.4 KB instead
of the few hundred bytes a classical ClientHello occupies.

If the group is classical (`x25519`, `secp256r1`, etc.), check, in order:

1. `cloudSqlTlsProvider` / `TLS_PROVIDER` is set to `CONSCRYPT` (or to `AUTO`
   on a platform Conscrypt supports). It defaults to `JDK`, which never uses
   Conscrypt before JDK 27.
2. Your logs do not show the `AUTO` fallback message. If they do, see
   [Platform support](#platform-support).
3. `jdk.tls.namedGroups` has not been narrowed to exclude the hybrid groups.
4. The Cloud SQL instance offers a hybrid group. Both peers must support one.

[jep-527]: https://openjdk.org/jeps/527
[jep-496]: https://openjdk.org/jeps/496
[jdk-api]: https://bugs.openjdk.org/browse/JDK-8388519
[conscrypt]: https://github.com/google/conscrypt
