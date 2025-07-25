# Changelog

## [1.25.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.25.1...v1.25.2) (2025-07-14)


### Dependencies

* Update Non-major dependencies ([#2163](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2163)) ([a33acf5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a33acf505b6ff6e8633801e6b9bc54eeed6e6e6d))
* use latest shared config ([#2171](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2171)) ([0fbbd10](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0fbbd10aaf57ac017fb90a08b69e22c7d1dc0c01))

## [1.25.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.25.0...v1.25.1) (2025-05-21)


### Dependencies

* Update Non-major dependencies ([#2155](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2155)) ([5b05cfe](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5b05cfe905778ea817814a609403c466a28f35be))

## [1.25.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.24.2...v1.25.0) (2025-04-28)


### Features

* Update TLS validation to use both SAN and CN fields. ([#2150](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2150)) ([e7d9cef](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e7d9cefa0bee8f14dcaf34294889c89cd638dadc))


### Bug Fixes

* Roll back dependency update that broke R2DBC + MariaDB IAM E2E Test ([#2154](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2154)) ([2ac2d04](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2ac2d049cde8d141642ec5e38947c052dd286976))


### Dependencies

* Update Non-major dependencies ([#2145](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2145)) ([119e644](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/119e644785766d32f09d8d4ffca5bec5d72027ff))

## [1.24.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.24.1...v1.24.2) (2025-04-15)


### Bug Fixes

* Remove unsupported GraalVM UnsupportedExperimentalVMOptions flag. ([#2141](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2141)) ([652a30d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/652a30d8782d48a807a2532e16aff922065ba80d)), closes [#2140](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2140)
* Roll back dependency update that broke R2DBC + MariaDB IAM E2E Test ([#2146](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2146)) ([8d2b2ef](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8d2b2efc2880328f3ff0e4d437f7ca4690ce51ec))


### Dependencies

* Update Non-major dependencies ([#2127](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2127)) ([1edc758](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1edc75857d592aeccaa1f489cad1fc76c7cecbb1))

## [1.24.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.24.0...v1.24.1) (2025-03-27)


### Bug Fixes

* only keep track of sockets opened using domain name ([#2130](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2130)) ([071085c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/071085cac1e2289dfaf4f685c6e3c3939e4f456a)), closes [#2129](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2129)

## [1.24.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.23.1...v1.24.0) (2025-03-19)


### Features

* Automatically reset DNS-configured connections on DNS change ([#2056](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2056)) ([88631fc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/88631fc3f17eb854c5d4e1751c1f918a69f9a77a))
* Use standard TLS hostname validation for instances with DNS names. ([#2125](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2125)) ([a892017](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a892017507a6dbf82e34b865fe4309cde8e592c5))


### Bug Fixes

* Add GraalVM configuration for the JDK's DNS Resolver. ([#2123](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2123)) ([d276672](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d276672de4a6291ccc26e5cbc6c3805a3a49b8d2))
* use daemon threads internally ([#2118](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2118)) ([c82d4ca](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c82d4ca2b01b3f8ab0c93a385465d2df38c16c40))


### Dependencies

* Update Non-major dependencies ([#2115](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2115)) ([3f91c24](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3f91c246d75d678bd979d7b76d52b9e4d43f7b59))

## [1.23.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.23.0...v1.23.1) (2025-02-14)


### Dependencies

* Update Non-major dependencies ([#2105](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2105)) ([2dac773](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2dac7732990fb4b9ec8da04d272ad32a46aa70ae))
* Update Non-major dependencies ([#2113](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2113)) ([bb44a33](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/bb44a337bbb4a2796d42c4378d188d5784596f5b))

## [1.23.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.22.0...v1.23.0) (2025-01-24)


### Features

* Automatically configure connections using DNS. Part of [#2043](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2043). ([#2047](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2047)) ([cb745f2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/cb745f2346907268fb326192de175fd268bc11ec))


### Documentation

* Add docs for connector configuration using DNS Names, Part of [#2043](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2043). ([#2102](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2102)) ([1860b76](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1860b768c8992f8cd3f0a4e2c8cf254b41a8b612))

## [1.22.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.21.2...v1.22.0) (2025-01-16)


### Features

* Support Customer Managed Private CA for server certificates. ([#2095](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2095)) ([d14b4e4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d14b4e4de2fd066a24f3a4e6db2ee3ce418f830d))


### Dependencies

* Update dependency maven to v3.9.9 ([#2057](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2057)) ([ffd8935](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ffd8935a5b36147e9657235d413cef4d35c840de))
* Update Non-major dependencies ([#2094](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2094)) ([293b0ee](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/293b0ee93be6d7aa34f860176cd849c3e7f4d0c0))

## [1.21.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.21.1...v1.21.2) (2024-12-10)


### Bug Fixes

* Update dependency versions to latest. ([#2092](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2092)) ([1d476a7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1d476a7dab53bb9d55287594b77be8b1e4da1512))


### Dependencies

* Update Non-major dependencies ([#2089](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2089)) ([ab122eb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ab122ebe46ae31f0affd409626fcfa1d33732bb2))

## [1.21.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.21.0...v1.21.1) (2024-11-21)


### Dependencies

* Update Non-major dependencies ([#2076](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2076)) ([5a7e08a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5a7e08a1d4c7044e5c799bea637bd22a9aa2f320))


### Documentation

* Add missing javadoc comments to public methods. ([#2080](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2080)) ([00e0b1a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/00e0b1a0d4008e9ac32bfa60525316a312be1db1))

## [1.21.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.20.1...v1.21.0) (2024-09-30)


### Features

* Support for CAS server certificate authority. ([#2060](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2060)) ([4332ffc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4332ffc34380ab4ba8b7cb9ed67f1fb985fb38ce))


### Dependencies

* Update Non-major dependencies ([#2055](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2055)) ([be764dc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/be764dc65bd70e41be9e5e03c896d2622518a58d))
* Update Non-major dependencies ([#2072](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2072)) ([d834afe](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d834afead7f3ab239a7dafde45cb13e6918b0680))

## [1.20.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.20.0...v1.20.1) (2024-09-05)


### Bug Fixes

* Lazy refresh should refresh tokens 4 minutes before expiration. ([#2063](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2063)) ([286051d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/286051d5a93eff9686ca3ef657a63d1bc3418820))


### Dependencies

* Update dependency maven to v3.9.8 ([#2025](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2025)) ([a75fa59](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a75fa599c76f8cb62c040a9fe2b9f535756d0fca))

## [1.20.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.19.1...v1.20.0) (2024-08-14)


### Features

* Retry API calls that return a 5xx error. Fixes [#2029](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2029). ([#2041](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2041)) ([d76e892](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d76e8926f8f8ed2e5a8be952cec22cc77fe3b6d3))


### Dependencies

* Update Non-major dependencies ([#2037](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2037)) ([b5e9b50](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b5e9b505e834b171615ca3128ea608b640fa6319))

## [1.19.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.19.0...v1.19.1) (2024-07-10)


### Dependencies

* Update dependency com.mysql:mysql-connector-j to v9 ([#2034](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2034)) ([d7e434f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d7e434f92577a572fc64f8aed1ec60c26e97c076))
* Update Non-major dependencies ([#2033](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2033)) ([3c2d81d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3c2d81dff217dcaa994a0287d9db23d5e0989e05))


### Documentation

* update serverless products lazy cert refresh is useful for ([#2022](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/2022)) ([322ca2d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/322ca2d7d461ed5692d271149c2413fa559153da))

## [1.19.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.18.1...v1.19.0) (2024-06-11)


### Features

* Add lazy refresh strategy to the connector. Fixes [#992](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/992). ([d84d082](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d84d0827cbfe733f33309579d8ac8728ef7d63d1))
* Configure java connector to check CN instance name. Fixes [#1995](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1995) ([#1996](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1996)) ([9346117](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/93461176c2426e6a62c15d4b2e101f4283e1e92b))


### Bug Fixes

* Add TrustManagerFactory workaround for Conscrypt bug, Fixes [#1983](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1983). ([#1993](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1993)) ([0735a91](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0735a916df2d89250f844b3c617e90564c3a950e))
* Remove native image flag that breaks GraalVM CE builds, Fixes [#1979](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1979). ([#1991](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1991)) ([d14892f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d14892fb5dffd872c4ad3a3fe0560a8887fae0fd))


### Dependencies

* Update com.google.http-client dependencies to v1.44.2 ([f1b1434](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f1b1434f60dbeca064380097c3856c2506010872))
* Update dependency com.google.api-client:google-api-client to v2.5.1 ([fcb20e5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fcb20e554166a62cf7c02bedafe4d77ac6a722af))
* Update dependency com.google.api-client:google-api-client to v2.6.0 ([ab3973c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ab3973ce5f50ac1367434aa146feb4c5d842f97d))
* Update dependency com.google.api:gax to v2.48.1 ([20ea693](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/20ea693a80a7a319f983027fc658a2419efdc6d2))
* Update dependency com.google.api:gax to v2.49.0 ([f7a4d70](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f7a4d700502b5438e2b035d1f12226ecebedc4c8))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240521-2.0.0 ([33ebcf8](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/33ebcf8a21dc31a542d1cd0c3ca876702866ebd4))
* Update dependency com.google.auto.value:auto-value-annotations to v1.11.0 ([0d73283](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0d73283ec9b4fd1482100b6dddc0c867199fd822))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.8.0 ([e3b7b19](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e3b7b196f1655ba2606e9a9bdd70f76c7dfd92cf))
* Update dependency com.google.errorprone:error_prone_annotations to v2.28.0 ([7e94182](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7e941820b6a3bc87d06572d29bc779ace2cbec96))
* Update dependency com.google.guava:guava to v33.2.1-android ([712ca24](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/712ca2442c39f090c06e3322a5b3605c6fb7f798))
* Update dependency com.microsoft.sqlserver:mssql-jdbc to v12.6.2.jre8 ([d1b0f77](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d1b0f77ba6322ddb66ada356c994b9845a1c25d4))
* Update dependency kr.motd.maven:os-maven-plugin to v1.7.1 ([94f8436](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/94f843690a0fbbfc3fa59eb576b9316e9ef4eb42))
* Update dependency maven to v3.9.7 ([81f1b2e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/81f1b2e9346f4ea54ab84c32eae87a39dd7c043a))
* Update dependency org.apache.maven.plugins:maven-checkstyle-plugin to v3.4.0 ([3e0cec3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3e0cec380bc2f1a71dc7af9250e106af03d35061))
* Update dependency org.apache.maven.plugins:maven-enforcer-plugin to v3.5.0 ([e790e74](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e790e7441760d4a859bc2fe0df3068bf9e8b2687))
* Update dependency org.apache.maven.plugins:maven-javadoc-plugin to v3.7.0 ([27389b6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/27389b674ec0f00821015eb45edf47779be0cab7))
* Update dependency org.checkerframework:checker-qual to v3.43.0 ([5c37897](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5c378970b254419bcd4e5af063eb3a000bd9ab39))
* Update dependency org.checkerframework:checker-qual to v3.44.0 ([69a2f39](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/69a2f3927d8ee9ca2f90a212c34cab63b0706929))
* Update dependency org.mariadb.jdbc:mariadb-java-client to v3.4.0 ([3796a7c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3796a7c6f5bd70b554515122204f5fbc771c6db1))
* Update dependency org.sonatype.plugins:nexus-staging-maven-plugin to v1.7.0 ([225454c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/225454c877569b68e3e9a2e76c1e59244dc45ad0))
* Update native-image.version to v0.10.2 ([dbffbf4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/dbffbf48c38b25f47b85fa50e4b938ebf88f9357))
* Update netty and r2dbc dependencies to v4.1.110.Final ([3486e1c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3486e1ccfe026e8fddab821b14ced003a0b8f2ac))

## [1.18.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.18.0...v1.18.1) (2024-05-14)


### Bug Fixes

* Configure unix socket library for Graalvm ([#1961](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1961)) ([e054059](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e0540592bf68045d1dbabaf217900833b9b8065e)), closes [#1940](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1940)


### Dependencies

* Update dependency com.google.api:gax to v2.48.0 ([#1954](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1954)) ([22d3bd1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/22d3bd1a1f234e8844dd40c97d1271dbc96bbc95))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.7.7 ([#1953](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1953)) ([a2ab407](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a2ab4071c5ef7f4f9713bcd51c5fbf5cc10b6bfc))
* Update dependency com.google.errorprone:error_prone_annotations to v2.27.1 ([#1962](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1962)) ([bc48ee4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/bc48ee4e053781295a04b6d75c94d7714ef6ac20))
* Update dependency com.google.guava:guava to v33.2.0-android ([#1974](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1974)) ([daa52bd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/daa52bd1c3f6e789abdb33c29b141f5f79fd8aa2))
* Update dependency com.google.oauth-client:google-oauth-client to v1.36.0 ([#1971](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1971)) ([4cac595](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4cac59572863a5b1d11fb07caa4e2db2db856867))
* Update dependency com.mysql:mysql-connector-j to v8.4.0 ([#1975](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1975)) ([b429616](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b429616437af27cd6e9c639dc024feffc2579edf))
* Update dependency commons-codec:commons-codec to v1.17.0 ([#1963](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1963)) ([4c2b0ee](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4c2b0ee8e603422b5ceb456769f1d60deeb86858))
* Update dependency io.projectreactor:reactor-core to v3.6.6 ([#1973](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1973)) ([9f49d3d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/9f49d3d147aa6c1a89efd79ad77de3c6fb35a8a4))
* Update dependency io.projectreactor.netty:reactor-netty to v1.1.19 ([#1972](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1972)) ([d4203a3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d4203a3a737045a77830cd1309cc16a808e43cae))
* Update dependency maven-wrapper to v3.3.1 ([#1957](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1957)) ([f5d0c96](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f5d0c96f1f5d7b0cf474db2c2d850bf8ec569f8e))
* Update dependency org.apache.maven.plugins:maven-gpg-plugin to v3.2.4 ([#1955](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1955)) ([2d1a3d3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2d1a3d3d0248772eba0ef3b548d739eed9343019))
* Update dependency org.apache.maven.plugins:maven-jar-plugin to v3.4.1 ([#1956](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1956)) ([2f5b25f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2f5b25f4aa95e3dde89d8b49767e8ed73e303714))
* Update dependency org.graalvm.sdk:nativeimage to v24.0.1 ([#1948](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1948)) ([8e3e0e6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8e3e0e6c2a2eddd55be78e8eea0b77237c7f961d))

## [1.18.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.17.1...v1.18.0) (2024-04-15)


### Features

* add support for debug logging ([#1935](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1935)) ([473ac85](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/473ac8544660b6ece972e0132d8b89a044ca8636))
* add support for TPC ([#1901](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1901)) ([f6764dd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f6764dd1cae4d314a23bfac0bc9b2d6533f6ed38))


### Bug Fixes

* Set Artifact ID using the static method. ([#1938](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1938)) ([d14bd65](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d14bd657dba5446e3c5be77ae889cc103cd76634))


### Dependencies

* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240324-2.0.0 ([#1923](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1923)) ([43e9538](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/43e9538bdc9ab1c8de28334106d8b7d91cc1f745))
* Update dependency io.asyncer:r2dbc-mysql to v1.1.3 ([#1930](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1930)) ([b5c8df4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b5c8df4450a440b5b5e84ff5d5e44d822de7fcf0))
* Update dependency io.projectreactor:reactor-core to v3.6.5 ([#1933](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1933)) ([3f2db4c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3f2db4c891c85fb018fd037895ec8387fd712d57))
* Update dependency io.projectreactor.netty:reactor-netty to v1.1.18 ([#1932](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1932)) ([f33073d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f33073daf82036ddc2212f4e44ee1ae9dea38d1b))
* Update dependency org.apache.maven.plugins:maven-assembly-plugin to v3.7.1 ([#1911](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1911)) ([be8c9c0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/be8c9c0d0cf7eb90b8d48398a6be4559848d1503))
* Update dependency org.apache.maven.plugins:maven-compiler-plugin to v3.13.0 ([#1914](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1914)) ([281b7cd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/281b7cd3af39e21a0ed9d3bfb97449eda190077b))
* Update dependency org.apache.maven.plugins:maven-gpg-plugin to v3.2.3 ([#1936](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1936)) ([822de7a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/822de7a7db8ecd84c6704df1041efce8b4ab18d2))
* Update dependency org.apache.maven.plugins:maven-jar-plugin to v3.4.0 ([#1942](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1942)) ([87c14a2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/87c14a2dca748a287fc0926d6e1a74f8d47257cc))
* Update dependency org.apache.maven.plugins:maven-source-plugin to v3.3.1 ([#1928](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1928)) ([9333696](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/9333696f18930cc54e2a233d9a3265646461df85))
* Update dependency org.graalvm.sdk:nativeimage to v24 ([#1916](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1916)) ([77c1d95](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/77c1d9512a18f1d0ebf1e850e38df3d64a68d59f))
* Update dependency org.jacoco:jacoco-maven-plugin to v0.8.12 ([#1926](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1926)) ([2d55cab](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2d55cabeb7eb8d9585e50b376fc40fbdd09430e6))
* Update dependency org.postgresql:r2dbc-postgresql to v1.0.5.RELEASE ([#1934](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1934)) ([95af7e3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/95af7e386d1e8a21b9aa3463c71912ad68dad620))
* Update dependency org.slf4j:slf4j-api to v2.0.13 ([#1941](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1941)) ([c8091fa](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c8091fa6f118f19b0a098f0c9c5e2e2e9e42dc34))
* Update netty and r2dbc dependencies to v4.1.109.Final ([#1945](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1945)) ([97918a2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/97918a2f7500d558ce5592670fd81ada73d111d6))
* Update org.ow2.asm dependencies to v9.7 ([#1920](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1920)) ([8132a1f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8132a1f8491acf5fc676f6f27bdaf139a0d75fb1))

## [1.17.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.17.0...v1.17.1) (2024-03-18)


### Bug Fixes

* Add scope for Credentials defined by a JSON file ([#1907](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1907)) ([e8377c3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e8377c3ff2cf9d50bd16071cac94d1360375c01d))


### Dependencies

* Update dependency com.google.api:gax to v2.46.0 ([#1902](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1902)) ([609408c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/609408c23aabb19aa490743a883cd8a6001d5377))
* Update dependency com.google.api:gax to v2.46.1 ([#1903](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1903)) ([f1774b9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f1774b93b4bea81f8371bf2a31780965d4cc8707))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.7.5 ([#1897](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1897)) ([0206a10](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0206a1022e73ad3742971b75ececcc9c90909305))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.7.6 ([#1900](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1900)) ([3510feb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3510feb1199f96928fda865fdab923629b86d856))
* Update dependency com.google.errorprone:error_prone_annotations to v2.26.1 ([#1890](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1890)) ([e5a86c5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e5a86c5c391989707e576570b8d92a7ba1d97a09))
* Update dependency org.apache.maven.plugins:maven-gpg-plugin to v3.2.1 ([#1906](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1906)) ([3c14e01](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3c14e012fbf499e9f3ae89bfa4fd4c09d3c5303b))
* Update dependency org.postgresql:postgresql to v42.7.3 ([#1899](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1899)) ([860a369](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/860a3691e04928bee3d1ccb88e4f0b9e6a1876cf))

## [1.17.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.16.0...v1.17.0) (2024-03-12)


### Features

* Add r2dbc support for MariaDB ([#1717](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1717)) ([d072780](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d0727809c16caa38dbbdb7a1550b951413c8c69b))


### Bug Fixes

* Guava dependency missing from jar-with-dependencies ([#1861](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1861)) ([fd05682](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fd056824f55825dafd7756411e21f27b0e981ef5))
* Update R2DBC connection validation to async ([#1842](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1842)) ([f954af3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f954af3ad1b83530525657756e08941b548a6717))


### Dependencies

* Update dependency com.github.jnr:jnr-ffi to v2.2.16 ([#1843](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1843)) ([8c056b6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8c056b625722318a9ea95434952ea2c7814f78cf))
* Update dependency com.github.jnr:jnr-unixsocket to v0.38.22 ([#1844](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1844)) ([c6c40a2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c6c40a23d7bc721b74800747f417425aeded7580))
* Update dependency com.google.api:gax to v2.45.0 ([#1874](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1874)) ([26c23a0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/26c23a00f1d55591401908e4f7a5e82eef7fba31))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240304-2.0.0 ([#1875](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1875)) ([c5664dd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c5664dde4cf7a41c6b2cf9f9a8720903d60fbcbe))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.7.4 ([#1871](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1871)) ([d90d4b6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d90d4b6c25e3bfe5ffc0e389288dd8c52a5eb3ca))
* Update dependency com.google.errorprone:error_prone_annotations to v2.26.0 ([#1882](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1882)) ([209a6ae](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/209a6aef1f940c7aebcbf68ab913aa11e7ae5e0e))
* Update dependency com.microsoft.sqlserver:mssql-jdbc to v12.6.1.jre8 ([#1856](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1856)) ([67d00ab](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/67d00ab10adb7dbd21b598ae2418d0700b1f79ca))
* Update dependency io.asyncer:r2dbc-mysql to v1.1.2 ([#1868](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1868)) ([397951e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/397951eeb2bbcfdfa1d6ebef8f344b77daed8721))
* Update dependency io.projectreactor:reactor-core to v3.6.4 ([#1885](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1885)) ([5d13c67](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5d13c6779d1ee6a930942ea14216e8f98d87653e))
* Update dependency io.projectreactor.netty:reactor-netty to v1.1.17 ([#1884](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1884)) ([47e0043](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/47e00435065006a31df1e3f355b892719335bdc6))
* Update dependency org.apache.maven.plugins:maven-assembly-plugin to v3.7.0 ([#1879](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1879)) ([abcff95](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/abcff955eb525ac21787fee8cdcdee42f6ac9e15))
* Update dependency org.apache.maven.plugins:maven-gpg-plugin to v3.2.0 ([#1880](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1880)) ([9ed722a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/9ed722a5aff536537adbef62c5a7ded2da45fa46))
* Update dependency org.mariadb.jdbc:mariadb-java-client to v3.3.3 ([#1857](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1857)) ([739e738](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/739e738b7d13119483c0c51a6cd6d4e66ab89e91))
* Update dependency org.postgresql:postgresql to v42.7.2 ([#1855](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1855)) ([adc2a09](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/adc2a09a77a4dbcca1ef36243028835167abc149))
* Update native-image.version to v0.10.1 ([#1852](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1852)) ([ac09838](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ac0983830b13182e8e2f1f0ebe58f7a3ed55fcf3))

## [1.16.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.15.2...v1.16.0) (2024-02-13)


### Features

* add option to specify quota project ([#1832](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1832)) ([6d2a589](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6d2a58922472376f53fa036025db26247e16daf6))
* Background refresh stops on unrecoverable Admin API response ([#1802](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1802)) ([3a8b18b](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3a8b18b296c3b0c4031c406507c8c3632d65055c))


### Bug Fixes

* ensure cert refresh recovers from sleep ([#1771](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1771)) ([560d8cb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/560d8cbaa7683ab0288627473e3d87b356f6dd71))


### Dependencies

* Update com.google.http-client dependencies to v1.44.1 ([#1810](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1810)) ([d7e7044](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d7e704485b229a15a1c2614f8ae3b57961a04b77))
* Update dependency com.google.api-client:google-api-client to v2.3.0 ([#1813](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1813)) ([a01221f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a01221f84e65899735695571d3ba3a3864cf6675))
* Update dependency com.google.api:gax to v2.40.0 ([#1794](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1794)) ([fa83e16](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fa83e16dc90ffd2a21424b48397f01134abb974d))
* Update dependency com.google.api:gax to v2.41.0 ([#1803](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1803)) ([344a515](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/344a51535d8002722e9ef52cf43f902f58007c64))
* Update dependency com.google.api:gax to v2.42.0 ([#1817](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1817)) ([568d539](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/568d53971442578d95c21ae2c18106e214c11f65))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240123-2.0.0 ([#1808](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1808)) ([615c894](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/615c89471170a1931ac640619f8907f5ba661d35))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240201-2.0.0 ([#1820](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1820)) ([f86bd48](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f86bd48ef7b0764625083d5bf59406a6db95b8c5))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240205-2.0.0 ([#1827](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1827)) ([f547857](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f547857f55d6c525f5a6c378878ab48750b50917))
* Update dependency com.google.http-client:google-http-client-jackson2 to v1.44.1 ([#1807](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1807)) ([07bd8a9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/07bd8a976165b00014680a6c9014687b7d411fbc))
* Update dependency com.google.oauth-client:google-oauth-client to v1.35.0 ([#1788](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1788)) ([0c13a02](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0c13a02ac025fd597d822236081dfb9fdfb416ab))
* Update dependency com.microsoft.sqlserver:mssql-jdbc to v12.6.0.jre8 ([#1818](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1818)) ([d09bdc9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d09bdc96c3a74952be9fb50e698fa17c248a0cdf))
* Update dependency com.mysql:mysql-connector-j to v8.3.0 ([#1833](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1833)) ([76c45b6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/76c45b6814401a24472ff227554f21f5db63dfeb))
* Update dependency io.asyncer:r2dbc-mysql to v1.1.1 ([#1828](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1828)) ([42f2ee1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/42f2ee13aa3709cefc49c4cd1deb1da0e3d4d303))
* Update dependency org.slf4j:slf4j-api to v2.0.12 ([#1825](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1825)) ([f51a101](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f51a1011c2d5dd0941ed17971a22227bdbdeb4cd))
* Update native-image.version to v0.10.0 ([#1819](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1819)) ([48b8feb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/48b8feb8d1c9f24d3df70f9009f0695124789803))
* Update netty and r2dbc dependencies to v4.1.106.Final ([#1795](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1795)) ([37de76d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/37de76da2bc87f1b5a63e6d936243a325d39838d))
* Update netty and r2dbc dependencies to v4.1.107.Final ([#1834](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1834)) ([07e0c93](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/07e0c938ae11ec7093390e38577fff742bc2b634))

## [1.15.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.15.1...v1.15.2) (2024-01-17)


### Bug Fixes

* Call force refresh when r2dbc connection is invalid ([#1746](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1746)) ([1b0c43c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1b0c43ca5ed0db5fd505d017773fdd30cec4788f))


### Dependencies

* Update com.google.auth dependencies to v1.21.0 ([#1760](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1760)) ([a8f1c00](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a8f1c00351a16b66fa498cf5771e06367ca82965))
* Update com.google.auth dependencies to v1.22.0 ([#1766](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1766)) ([0c42664](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0c42664a3f200a373504fa77451e24e86f972d6b))
* Update dependency com.google.api:gax to v2.39.0 ([#1761](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1761)) ([48f0a1c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/48f0a1c065aec87f4192bfbefe2f180e75cb4334))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240101-2.0.0 ([#1767](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1767)) ([b87f6c2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b87f6c22bdd6ac613071d78b078860927850c1e0))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20240115-2.0.0 ([#1782](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1782)) ([aaa2b5f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/aaa2b5f844082aa337bfe271d323c1a25d898899))
* Update dependency com.google.errorprone:error_prone_annotations to v2.24.0 ([#1754](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1754)) ([fa948cf](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fa948cf3b1f1461ead9837420fe41df5d7b041c5))
* Update dependency com.google.errorprone:error_prone_annotations to v2.24.1 ([#1758](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1758)) ([62dcac7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/62dcac7358dfa8702acba762272a6b0180b94d07))
* Update dependency com.google.guava:guava to v33 ([#1751](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1751)) ([999c076](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/999c07682f78789305e558d868abde80696d2183))
* Update dependency io.asyncer:r2dbc-mysql to v1.0.6 ([#1774](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1774)) ([36ed489](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/36ed48952fcef259cdf47186ad5e11ef081ce1a1))
* Update dependency io.projectreactor:reactor-core to v3.6.2 ([#1769](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1769)) ([abe4065](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/abe4065965f096fe7478eb10c4d5ec3b8e8d0ac7))
* Update dependency io.projectreactor.netty:reactor-netty to v1.1.15 ([#1768](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1768)) ([063e2f8](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/063e2f8300ef041280bd2c42dfc570540acc2106))
* Update dependency org.apache.maven.plugins:maven-compiler-plugin to v3.12.0 ([#1750](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1750)) ([74e33cc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/74e33ccdcd75aa3902df9f733781ba7d934bb86f))
* Update dependency org.apache.maven.plugins:maven-compiler-plugin to v3.12.1 ([#1756](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1756)) ([72433ee](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/72433ee22cb261201a7a1cf6fe7c84a0baf56bd5))
* Update dependency org.apache.maven.plugins:maven-surefire-plugin to v3.2.3 ([#1741](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1741)) ([9313397](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/9313397fb0250f2fb63a1597984f7cb4f2f22227))
* Update dependency org.apache.maven.plugins:maven-surefire-plugin to v3.2.5 ([#1770](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1770)) ([a02e6f4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a02e6f4950b85ae6eb45dd61b45356afcbfb9e42))
* Update dependency org.checkerframework:checker-qual to v3.42.0 ([#1749](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1749)) ([ffcc2ea](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ffcc2ea8e34bd6cc48916524d21f1243640907e0))
* Update dependency org.graalvm.sdk:nativeimage to v23.1.2 ([#1781](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1781)) ([6ce0780](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6ce078005eb579512cfaf01f93b4769427cee5eb))
* Update dependency org.mariadb.jdbc:mariadb-java-client to v3.3.2 ([#1752](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1752)) ([7e25ca7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7e25ca71297349a4c4c00321fa6afdacceb6ea5e))
* Update dependency org.postgresql:r2dbc-postgresql to v1.0.4.RELEASE ([#1772](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1772)) ([3649ad3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3649ad3e70e3e82becfa27d50ed8a994f9ff88b1))
* Update dependency org.slf4j:slf4j-api to v2.0.10 ([#1757](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1757)) ([3ad9e29](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3ad9e296bc55dc4ce604d0f0d2e4166aa0a7419d))
* Update dependency org.slf4j:slf4j-api to v2.0.11 ([#1764](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1764)) ([a4e77f4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a4e77f457de3e5ea2b56c6bd036627d01770f869))
* Update netty and r2dbc dependencies to v4.1.103.Final ([#1739](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1739)) ([0c8dd92](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0c8dd9219a9391337c6959d8d15bb3c02885f1fa))
* Update netty and r2dbc dependencies to v4.1.104.Final ([#1748](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1748)) ([748e3b3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/748e3b3d267a3667e286a8b1ea37332a1ec589a5))
* Update netty and r2dbc dependencies to v4.1.105.Final ([#1780](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1780)) ([9ef12b6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/9ef12b63e4227daafbc9570e07a94ce06f7c08a6))

## [1.15.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.15.0...v1.15.1) (2023-12-12)


### Bug Fixes

* Adjust default timeouts to accomodate more retries. Fixes [#1695](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1695). ([#1696](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1696)) ([a2dc813](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a2dc81346cd32c6c528757eee22440ec7302fc98))


### Dependencies

* Update dependencies for github ([#1701](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1701)) ([6a4e484](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6a4e4845573439113232c90c28d4b79787392a31))
* Update dependencies for github ([#1706](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1706)) ([db864cc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/db864cc830ecc0f614996f0a0277ed5a6d4d427b))
* Update dependency com.google.api:gax to v2.38.0 ([#1710](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1710)) ([a11e266](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a11e266ef571bb54022cd6da39e1d9baed75f26c))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20231128-2.0.0 ([#1716](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1716)) ([516ff01](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/516ff019a9af43ec2eff06df4e1cc7a52ab7637c))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20231208-2.0.0 ([#1727](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1727)) ([ba95a98](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ba95a98749a5cc9f0f25b579d60f08dc846fbe53))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.7.1 ([#1724](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1724)) ([6bce7a1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6bce7a137743447e2e9287850ccd7e1c33d5a3b1))
* Update dependency cryptography to v41.0.6 [SECURITY] ([#1705](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1705)) ([470b4cd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/470b4cd6f21da98d31461b4dd8137688415285a5))
* Update dependency google-cloud-core to v2.4.1 ([#1723](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1723)) ([b3c9cf6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b3c9cf631e5a1ac59d698936d6650e12b9ef2484))
* Update dependency importlib-metadata to v7 ([#1713](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1713)) ([131dca3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/131dca37afc9c3c93de76a56531d6078bbc04c1c))
* Update dependency io.projectreactor:reactor-core to v3.6.1 ([#1731](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1731)) ([b7c5cac](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b7c5cac91e957a2bd7b1b5d792157aa607f4b23a))
* Update dependency io.projectreactor.netty:reactor-netty to v1.1.14 ([#1730](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1730)) ([0465b83](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0465b83f523bc4df2fae6aac4dd534dc162e2f67))
* Update dependency maven to v3.9.6 ([#1711](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1711)) ([883cf4e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/883cf4ec2dcd09fffeb2b2515eceeaf537663a80))
* Update dependency org.apache.maven.plugins:maven-javadoc-plugin to v3.6.3 ([#1714](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1714)) ([00f57fc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/00f57fc79a706ecec565df661ff985af3d7307fc))
* Update dependency org.checkerframework:checker-qual to v3.41.0 ([#1715](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1715)) ([702b797](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/702b7972eabb8ab5cf478fccca5eb0a63f5aae83))
* Update dependency org.codehaus.mojo:versions-maven-plugin to v2.16.2 ([#1700](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1700)) ([2089c4f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2089c4f959c0ba0b99e105356a71adcf40492495))
* Update dependency org.mariadb.jdbc:mariadb-java-client to v3.3.1 ([#1709](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1709)) ([025235b](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/025235bb836185bf2a65329e93aea48c5e85a294))
* Update dependency org.postgresql:postgresql to v42.7.0 ([#1703](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1703)) ([4fb0347](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4fb0347058301a89a8057406a6cd0a02abc5452b))
* Update dependency org.postgresql:postgresql to v42.7.1 ([#1718](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1718)) ([88c27ef](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/88c27efaefda0dba23629152527a66b6819297e3))
* Update dependency org.postgresql:r2dbc-postgresql to v1.0.3.RELEASE ([#1732](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1732)) ([349b0f4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/349b0f476fd4f8aa97ca35c0b5ac13f104ade8ed))
* Update dependency protobuf to v4.25.1 ([#1698](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1698)) ([47e8bd6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/47e8bd6d54993ea440abe20573fb2042bbeb6e41))
* Update github/codeql-action action to v2.22.7 ([#1699](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1699)) ([895076c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/895076c0d1dcb51a859d72b908d755e212a4e5a6))
* Update github/codeql-action action to v2.22.9 ([#1720](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1720)) ([145d9de](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/145d9de3086f1301bfbd429db3ce0fc26ba65169))
* Update google-github-actions/get-secretmanager-secrets action to v2 ([#1726](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1726)) ([568b567](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/568b567232eb908afc591139431e629e2df012db))
* Update python dependencies for kokoro ([#1702](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1702)) ([6294ad0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6294ad057dc9edbdaa98c2e74d8b677ec01dbbf4))
* Update python dependencies for kokoro ([#1712](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1712)) ([b5c5bf4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b5c5bf41518eac70bc07209e9eb2af0d6142ebf8))
* Update python dependencies for kokoro ([#1722](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1722)) ([76ce3c6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/76ce3c633929b0eab5bf74c3908d84a79e6ee878))
* Update python dependencies for kokoro ([#1725](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1725)) ([e97b687](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e97b687487ffab4d67b7c70ba7d62c3ada2c91ee))

## [1.15.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.14.1...v1.15.0) (2023-11-14)


### Features

* Add ConnectorRegistry.reset() and update shutdown()  ([438f075](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/438f075989c1095dc6bd77f90fbfda825a9316f6)), closes [#1687](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1687) [#776](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/776)
* Add public API to configure connectors. ([#1604](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1604)) ([310624d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/310624d240d107b23a36de207d688d21cb434c69))
* add support for configuring Admin API URL ([#1617](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1617)) ([bd2f0ce](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/bd2f0cefb705ccd989f29701899158fcdd5a7060))
* Identify the connector for a ConnectionConfig by it's unique configuration. ([#1654](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1654)) ([6a36bed](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6a36bed065a42277ac12b940c6f3ff9609bf993b))
* make ConnectorConfig part of the public api, move ConnectionConfig to internal. ([#1672](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1672)) ([650362a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/650362a933047be9171c50c21550ed2b225afcd8))
* non-blocking rate limiting on refresh. ([#1574](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1574)) ([d41bf27](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d41bf27cdb5770a54069502ba6ea5ad38b7131b8))
* Set the GoogleCredentials to use on a connector ([#1675](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1675)) ([a8616b8](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a8616b8a7fb9b419fa605485cf71866ccc635c25)), closes [#1670](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1670)
* use SLF4J for logging ([#1680](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1680)) ([b2c86b8](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b2c86b808ce161e9725aa7a3263d6ed39fc7e05f))


### Dependencies

* Update actions/checkout action to v4.1.1 ([#1619](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1619)) ([fe9f048](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fe9f048dbb3522cb6c5f763861e76b2524005bff))
* Update actions/github-script action to v7 ([#1689](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1689)) ([79d4884](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/79d48840cb0b2a70d4347ae6470f78682e61cda7))
* Update dependency charset-normalizer to v3.3.1 ([#1632](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1632)) ([977a7a6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/977a7a69082cd00dacc5b5e5e517fc065a6f7117))
* Update dependency com.google.api:gax to v2.36.0 ([#1628](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1628)) ([7556008](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/755600849dd23117548f1229ea075eb9ac797a2e))
* Update dependency com.google.api:gax to v2.37.0 ([#1655](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1655)) ([07ec1c7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/07ec1c7d0c2e55eb41e55318e0063d652da257bc))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20231017-2.0.0 ([#1631](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1631)) ([bbe62a9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/bbe62a919b6cdcdae33b2228082c810efe0b5e62))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20231029-2.0.0 ([#1664](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1664)) ([502fa76](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/502fa76b76fe4938c82ca0abae681d34ef72989b))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20231108-2.0.0 ([#1684](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1684)) ([f754ad5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f754ad5c26c483a443766fd202a7a69581e4262f))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.6.0 ([#1620](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1620)) ([af75eb8](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/af75eb8e7570184b91e7c80f03d1c81125f67ee2))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.6.1 ([#1650](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1650)) ([acdfd7f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/acdfd7fe38a9c114b387a13708fd813403dcb281))
* Update dependency com.google.errorprone:error_prone_annotations to v2.23.0 ([#1625](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1625)) ([05feda9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/05feda9f898e923cf431248574ef80bf3928d735))
* Update dependency com.microsoft.sqlserver:mssql-jdbc to v12.4.2.jre8 ([#1646](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1646)) ([f088f77](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f088f773da4c357f9b9178bf9f76fbf55b83c91a))
* Update dependency google-api-core to v2.13.0 ([#1674](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1674)) ([a94f358](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a94f3580512402e5854a12278ed0b1c0d8ef6249))
* Update dependency google-api-core to v2.13.1 ([#1681](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1681)) ([ae86eca](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ae86ecafd649c655be21d4d4b22738bff35ec529))
* Update dependency google-api-core to v2.14.0 ([#1682](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1682)) ([2371be4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2371be48f8db66117dea5a0ada5aba8ccc310dbf))
* Update dependency io.asyncer:r2dbc-mysql to v1.0.5 ([#1610](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1610)) ([c23faaa](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c23faaaa530781a6a75d1384f16b2e8aa148ca62))
* Update dependency io.projectreactor:reactor-core to v3.6.0 ([#1690](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1690)) ([53eade9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/53eade920ebd0f76dad4d919e622a1b8633d890b))
* Update dependency io.projectreactor.netty:reactor-netty to v1.1.13 ([#1692](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1692)) ([5151da4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5151da4c8c814d615be802b544ceffabe87d41de))
* Update dependency org.apache.maven.plugins:maven-checkstyle-plugin to v3.3.1 ([#1637](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1637)) ([b3366c7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b3366c79f48936d8cc332bf89b5fcee12d28faef))
* Update dependency org.apache.maven.plugins:maven-dependency-plugin to v3.6.1 ([#1633](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1633)) ([21ee31c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/21ee31c0590f06fc6410a0df5dfe18cf17e2edb3))
* Update dependency org.apache.maven.plugins:maven-javadoc-plugin to v3.6.2 ([#1668](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1668)) ([a79b30c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a79b30c5fc0250c0d5e13e8fed0761ca84819583))
* Update dependency org.apache.maven.plugins:maven-surefire-plugin to v3.2.1 ([#1636](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1636)) ([d69fe49](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d69fe49f12528a6db1a0687d338333e248699d7c))
* Update dependency org.apache.maven.plugins:maven-surefire-plugin to v3.2.2 ([#1669](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1669)) ([56f0019](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/56f0019fe981bc9d8ada07f7ebd44885c105af3e))
* Update dependency org.checkerframework:checker-compat-qual to v2.5.6 ([#1660](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1660)) ([878abc4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/878abc4adfebd18f83aa260cdb5e53b89c93dbd6))
* Update dependency org.checkerframework:checker-qual to v3.40.0 ([#1657](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1657)) ([3da1995](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3da1995e57e6eaab55466da117bd9211989a9e60))
* Update dependency org.graalvm.sdk:nativeimage to v23.1.1 ([#1627](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1627)) ([2c4f767](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2c4f767b918d6c39abcbbe29e507926df84a1f71))
* Update dependency org.jacoco:jacoco-maven-plugin to v0.8.11 ([#1616](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1616)) ([7843798](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7843798f48af2a2bab66f6d60de8a372c91dddf0))
* Update dependency org.mariadb.jdbc:mariadb-java-client to v3.3.0 ([#1677](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1677)) ([027cc8f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/027cc8fd65de421d4b77cfe1a308084da9418705))
* Update dependency protobuf to v4.25.0 ([#1658](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1658)) ([35b0142](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/35b014207233041a8bc7231102bd3e9654961ec1))
* Update dependency urllib3 to v2.0.7 [SECURITY] ([#1624](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1624)) ([8ff9a48](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8ff9a48bd5d0b395796316f7650d2edc4a7ea9ab))
* Update dependency wheel to v0.41.3 ([#1649](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1649)) ([e9b16e9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e9b16e960740c95089247b21f1e60aff573dea4f))
* Update github/codeql-action action to v2.22.2 ([#1611](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1611)) ([e56d6ee](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e56d6eebf2e3d21c393deb229d4f49ce8d626ea0))
* Update github/codeql-action action to v2.22.3 ([#1613](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1613)) ([5949797](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5949797ff682c3542d35e3fabde419363364fa7c))
* Update github/codeql-action action to v2.22.4 ([#1630](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1630)) ([94bd622](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/94bd622768889caa0dfbf727c75e825be3a47654))
* Update github/codeql-action action to v2.22.5 ([#1647](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1647)) ([b8c4221](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b8c422103dddb78b94d567345f89ed79a9154222))
* Update github/codeql-action action to v2.22.6 ([#1691](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1691)) ([d9b3a10](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d9b3a10303e26c49bad75860e480e828daf8bab9))
* Update graalvm/setup-graalvm digest to 0b782b6 ([#1663](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1663)) ([d66a580](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d66a580fafdd3c4a6ac63587176d3ef627286fcc))
* Update graalvm/setup-graalvm digest to 2b3d0bd ([#1626](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1626)) ([b7735d9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b7735d983562c91adf17d1dea62f74a2d540a2bc))
* Update graalvm/setup-graalvm digest to b8dc5fc ([#1665](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1665)) ([80bc75b](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/80bc75b412f0e75fa514b919807d88bee95e05b1))
* Update junit5 monorepo ([#1667](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1667)) ([c7b5bbe](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c7b5bbe70e17e02fbc2bab834c2dd55f697d0a71))
* Update native-image.version to v0.9.28 ([#1629](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1629)) ([82dcd2e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/82dcd2eb857f9883a03ef75ff8c52b1df7181041))
* Update netty and r2dbc dependencies to v4.1.101.Final ([#1678](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1678)) ([daab200](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/daab2009e9ceae79bae6488f00390b5de7486409))
* Update ossf/scorecard-action action to v2.3.1 ([#1634](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1634)) ([176177d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/176177d38a3338ea9d455b94800ee318a31210d4))
* Update python dependencies for kokoro ([#1580](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1580)) ([016f3ea](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/016f3ea5d8eba68666f426eb90e83bfb7deb513f))
* Update python dependencies for kokoro ([#1639](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1639)) ([9780562](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/97805623ffef7ed5eedab8ac24ceba865ba3b53e))
* Update python dependencies for kokoro ([#1651](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1651)) ([51be920](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/51be920a49e0d92d351eda3288f1e680b4be32ff))
* Update python dependencies for kokoro ([#1685](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1685)) ([f1eaf9d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f1eaf9d0bf2251b65847e15b16d793e84d3e8960))


### Documentation

* Add docs for connector configuration. Part of [#1226](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1226) [#1670](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1670). ([#1676](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1676)) ([533fb04](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/533fb0427bf7014ac2d03ff0fb940894efdeb4f0))
* Consolidate all JDBC docs onto one page ([#1605](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1605)) ([6871a3b](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6871a3b088b3dc804c3578811fc538fdb19d2e7c)), closes [#1550](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1550)
* Consolidate all R2DBC docs onto one page. ([#1606](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1606)) ([cfe4f15](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/cfe4f1529273e1a069d66e928668bc49bcdcae6c))
* update titles ([#1638](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1638)) ([4c75dc3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4c75dc34f75a83520bf1700f2a6d7a0996010d83))

## [1.14.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.14.0...v1.14.1) (2023-10-11)


### Bug Fixes

* Improve handling of futures and threads during refresh. ([#1573](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1573)) ([f3458a6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f3458a661d83f3f7f4d272e3af19a1e68eb6ddd6))


### Dependencies

* Update actions/checkout action to v4 ([#1510](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1510)) ([8aef1d5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8aef1d55868e4d99058d2b4dec9e68970da7ab6a))
* Update actions/checkout action to v4.1.0 ([#1548](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1548)) ([d6a6f1f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d6a6f1fa743df410f966c202aee8c85ab722db7e))
* Update com.google.auth dependencies to v1.20.0 ([#1581](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1581)) ([e7787dc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e7787dc9f1d42c78196069180c9783df654e13f9))
* Update dependencies for github ([#1536](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1536)) ([a8fb96f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a8fb96fe5a1de9172a65b4a51597a41bbde30cc2))
* Update dependencies for github ([#1597](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1597)) ([ebf9199](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ebf91994cc0da04313f39ff7206483ce51c1fa9a))
* Update dependencies related to netty and r2dbc ([#1430](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1430)) ([c72da09](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c72da09596daca7c168a768047ef5abe0174b26d))
* Update dependency charset-normalizer to v3 ([#1513](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1513)) ([479b503](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/479b503d696d54b3192e61c3b17ae1275db4ace0))
* Update dependency com.github.jnr:jnr-unixsocket to v0.38.21 ([#1486](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1486)) ([193e5e9](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/193e5e9248a28a8c1ca43376acf0ed5df42556a1))
* Update dependency com.google.api:gax to v2.34.0 ([#1547](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1547)) ([1d80e75](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1d80e75581031e427c791cc8f2cdb47b1662550c))
* Update dependency com.google.api:gax to v2.34.1 ([#1553](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1553)) ([e9643c3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e9643c3ad2893fecfde4b1c919dfdab9c711d7b2))
* Update dependency com.google.api:gax to v2.35.0 ([#1598](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1598)) ([6dac415](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6dac4150f0a49c7e2ff9cef30b02dacf3b324850))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20231004-2.0.0 ([#1599](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1599)) ([efd9709](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/efd97092567a14db6342235dcd88434078648ad2))
* Update dependency com.google.cloud:google-cloud-shared-config to v1.5.8 ([#1588](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1588)) ([366cd3a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/366cd3adc93594aa4a965e2ca5e1b4a6cc83215a))
* Update dependency com.google.errorprone:error_prone_annotations to v2.22.0 ([#1545](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1545)) ([c2d56dd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c2d56dd05e9bd9d36dd3869a5cf20c5c0a3c4ca5))
* Update dependency com.google.guava:guava to v32.1.3-android ([#1607](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1607)) ([fd7e9fd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fd7e9fd1111e1fbc007cb7998b80311fc94878d1))
* Update dependency google-api-core to v2.11.1 ([#1480](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1480)) ([eb32e96](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/eb32e96f69d0bfbf4b2f68b50020350ffd2b58f8))
* Update dependency google-api-core to v2.12.0 ([#1551](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1551)) ([497f5e3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/497f5e399b6fdd43214a9550cd746d2950dd1a30))
* Update dependency google-auth to v2.23.0 ([#1481](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1481)) ([1f2f660](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1f2f660c3c69d9f4c3e9cb57a7b6d1410783d84a))
* Update dependency google-auth to v2.23.1 ([#1555](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1555)) ([c8bab2b](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c8bab2bc535d1ab2eff90df594a9b2919cf27fa8))
* Update dependency google-cloud-storage to v2.10.0 ([#1482](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1482)) ([a2124ea](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a2124ea6122a1b2e7ede3c1991f73b95862c4fe5))
* Update dependency google-resumable-media to v2.6.0 ([#1484](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1484)) ([120d549](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/120d54996b5a2118d0965348c576a9c02d4225bd))
* Update dependency googleapis-common-protos to v1.60.0 ([#1491](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1491)) ([024d6b2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/024d6b252271970d1a9f3c1cd3cd210b7a2e2fe2))
* Update dependency graalvm to v23.1.0 ([#1542](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1542)) ([d78052e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d78052e3eb2eeeef1436e2757d431b1b86daf7da))
* Update dependency importlib-metadata to v4.13.0 ([#1493](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1493)) ([5fe7f95](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5fe7f9554d70da0c864a5677a2788feb2ce3cf91))
* Update dependency io.asyncer:r2dbc-mysql to v1.0.3 ([#1538](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1538)) ([12d7c41](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/12d7c4100274c6c0aa645aa7bace493649abef86))
* Update dependency io.asyncer:r2dbc-mysql to v1.0.4 ([#1596](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1596)) ([4dad8fd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4dad8fdfe229a1544b9fd77f742ed9e5d2c929c3))
* Update dependency io.projectreactor:reactor-core to v3.5.10 ([#1487](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1487)) ([2ff54c5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2ff54c5cc0afdc1e13ac44fa7f514e98f70afcae))
* Update dependency io.projectreactor:reactor-core to v3.5.11 ([#1602](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1602)) ([fb976dc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fb976dcd4e45b60fe3638918987b0784bfe2f29a))
* Update dependency jinja2 to v3.1.2 ([#1494](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1494)) ([0e6a5d6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0e6a5d67986d498c400fea64f859aa4d40f5d289))
* Update dependency keyring to v23.13.1 ([#1495](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1495)) ([8536e60](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8536e603fb39d2bef7e991067b50bba92db73a9e))
* Update dependency markupsafe to v2.1.3 ([#1496](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1496)) ([a2b7434](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a2b7434b9c2bcc4734cb4737f3a4825ad2cc164b))
* Update dependency maven to v3.9.5 ([#1583](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1583)) ([40faaac](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/40faaac81ba75fe70c2802850b44ca6668015deb))
* Update dependency org.apache.maven.plugins:maven-enforcer-plugin to v3.4.1 ([#1497](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1497)) ([e49ec73](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e49ec7379c1bf40af5045f7665739aca3cb5c16a))
* Update dependency org.apache.maven.plugins:maven-javadoc-plugin to v3.6.0 ([#1528](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1528)) ([fc94cfa](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fc94cfaddc49170c65e5708668d034c1e1b6013b))
* Update dependency org.checkerframework:checker-qual to v3.38.0 ([#1498](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1498)) ([8231af2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8231af2476bc7fcf5e51bbffda6af66f48205c57))
* Update dependency org.checkerframework:checker-qual to v3.39.0 ([#1569](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1569)) ([65673b7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/65673b7ed9dd885d07db9e8340c17844d8653c5e))
* Update dependency org.codehaus.mojo:versions-maven-plugin to v2.16.1 ([#1541](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1541)) ([aeefd5a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/aeefd5a76cfba69736b2c0d1841dd83f7e4f5435))
* Update dependency org.mariadb.jdbc:mariadb-java-client to v3.2.0 ([#1499](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1499)) ([8117719](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8117719dbf82fae324a439e5551e3dd474088311))
* Update dependency pyasn1 to v0.5.0 ([#1500](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1500)) ([2991344](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2991344f422941af7f5e5b6738254e331bf32508))
* Update dependency pyasn1-modules to v0.3.0 ([#1501](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1501)) ([1aeb9a7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1aeb9a7bdccd4ca5c685ff37b90ad3b4223fab3b))
* Update dependency pyjwt to v2.8.0 ([#1502](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1502)) ([1b27f4f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1b27f4ff7abff3df02f126fde2976fc8518854cc))
* Update dependency typing-extensions to v4.8.0 ([#1532](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1532)) ([345f550](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/345f550963204508aa00316bb5a8ea60ef1283b3))
* Update dependency urllib3 to v2.0.6 ([#1568](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1568)) ([17afbf5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/17afbf5ac70afa5c3d1e98044dd81f4da6ae668e))
* Update dependency wheel to v0.41.2 ([#1507](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1507)) ([eef30e0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/eef30e0000244c62c301f78858a7f911851277ac))
* Update dependency zipp to v3.16.2 ([#1508](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1508)) ([a5afb6c](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a5afb6cbf078406c5d283a37766a5414f64b3904))
* Update github/codeql-action action to v2.21.6 ([#1524](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1524)) ([507a546](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/507a5469efd0d46a7633137a956cbbeca4a448a2))
* Update github/codeql-action action to v2.21.7 ([#1526](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1526)) ([4d2e5ca](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4d2e5ca891e89b13c1b17b748abcf52c82a4527a))
* Update github/codeql-action action to v2.21.9 ([#1556](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1556)) ([787a27d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/787a27d75358947afc8a4f75601134796a77686c))
* Update github/codeql-action action to v2.22.0 ([#1591](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1591)) ([33fa428](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/33fa4288a8821976d8f1ed1916aaabdda7701184))
* Update graalvm/setup-graalvm digest to 6c7d417 ([#1537](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1537)) ([fbc1eb0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fbc1eb049d06070b2e34b9eda86f0c8814ba2577))
* Update native-image.version to v0.9.27 ([#1527](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1527)) ([41dc714](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/41dc714cbca14140865c74b6b156741c359ef85c))
* Update netty and r2dbc dependencies ([#1492](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1492)) ([de76d89](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/de76d890fc2119c85610b44ddccc3327f53b4146))
* Update netty and r2dbc dependencies ([#1603](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1603)) ([40aa650](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/40aa650aefd912d2b23d3adc906d2e0a22ed6c16))
* Update netty and r2dbc dependencies to v4.1.98.Final ([#1543](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1543)) ([76e4ee3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/76e4ee31f82a40fe250928149f7e192bb50a7416))
* Update netty and r2dbc dependencies to v4.1.99.Final ([#1557](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1557)) ([b10c6c3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b10c6c33b910b730a7234e7a126e0e5258707a60))
* Update org.ow2.asm dependencies to v9.6 ([#1567](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1567)) ([1eb62a4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1eb62a4d9e43d73f3f430111aec946f38c5bc74e))
* Update python dependencies for kokoro ([#1521](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1521)) ([8726b26](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8726b2628b0a5093271bc35870079468eeb4955f))
* Update python dependencies for kokoro ([#1522](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1522)) ([3428a39](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3428a39d1e695a177f72bd0ef3797ef544ae4c99))
* Update python dependencies for kokoro ([#1533](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1533)) ([88172dc](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/88172dc6a0cb51748b9f586aed0c91f8ded898c7))
* Update python dependencies for kokoro ([#1559](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1559)) ([0e83bbf](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0e83bbf4b337968967f4458e170a436a650489a9))
* Update python dependencies for kokoro ([#1560](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1560)) ([cc75556](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/cc75556798f33299493cc134bb6afeec9d0f1289))

## [1.14.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.13.1...v1.14.0) (2023-09-12)


### Features

* Add service account impersonation credentials factory. ([#1425](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1425)) ([6e21931](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6e21931af3d41b64cb91fc0979b6f3a5ea884330))
* Add support for service account impersonation. ([#1426](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1426)) ([7206a62](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7206a621c3c759784cf3498ca1756f11e76ec657))


### Bug Fixes

* re-use existing connection info during force refresh ([#1441](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1441)) ([769de5e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/769de5e1945cf1ec0143f479e11d4c1b547573ff))
* Use guava rate limiter instead of dev.failsafe ([#1393](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1393)) ([d27f2a6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d27f2a66f73e6d2d5ad7b7bd5d61bcc3f87fdc20))


### Documentation

* Add documentation for Service Account Impersonation feature. ([#1427](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1427)) ([f0a0936](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f0a09364ec6f8e174dad0502d15f94ba65367cc3)), closes [#1168](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1168)
* correct typo in jdbc-mysql.md ([#1436](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1436)) ([7750bcd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7750bcdaad2477197b2fc704390610cc1fa11598))


### Dependencies

* Update actions/checkout action to v3.6.0 ([#1470](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1470)) ([63ef58a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/63ef58a3de90098746e33965adeb25050c704a8e))
* Update actions/setup-java action to v3.12.0 ([#1429](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1429)) ([51c5c0e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/51c5c0ed6d83086b4bbd899c511c3a8e74829a15))
* Update actions/upload-artifact action to v3.1.3 ([#1464](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1464)) ([b3a6d95](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/b3a6d95e9427b45c0e40034bf3de9ab69668d200))
* Update dependency attrs to v22.2.0 ([#1471](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1471)) ([eb9a4e6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/eb9a4e606260c3f36eed62794323b66a91ee66bd))
* Update dependency charset-normalizer to v2.1.1 ([#1472](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1472)) ([894dc37](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/894dc37001f9c86bf4c8eeb514e2a6a4a0effdca))
* Update dependency click to v8.1.7 ([#1473](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1473)) ([e571422](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e57142215d75e26b2365f97597c24e4d71f277bf))
* Update dependency com.github.jnr:jnr-ffi to v2.2.15 ([#1485](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1485)) ([83eb5f7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/83eb5f7bb23bc71fb203add5ac2aa3d834ca17c1))
* Update dependency com.google.api:gax to v2.33.0 ([#1474](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1474)) ([37e6c7a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/37e6c7a3d85aa49ab97c1a7d215bd36c4497e00e))
* Update dependency com.google.apis:google-api-services-sqladmin to v1beta4-rev20230831-2.0.0 ([#1418](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1418)) ([7d91d32](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7d91d32fc8236eaa8c7aa75d846d77f0819c89fd))
* Update dependency com.google.auto.value:auto-value-annotations to v1.10.4 ([#1465](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1465)) ([35a8fdb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/35a8fdbee6b8f54217dfe573f22b55cf83357b8c))
* Update dependency com.google.errorprone:error_prone_annotations to v2.21.1 ([#1475](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1475)) ([5a7ba73](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5a7ba73491d61b00edd15af9ea8540e23edb9787))
* Update dependency com.microsoft.sqlserver:mssql-jdbc to v12.4.1.jre8 ([#1478](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1478)) ([753a112](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/753a1125542598a4d3a48b6e3934fe5d5c311b32))
* Update dependency gcp-docuploader to v0.6.5 ([#1466](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1466)) ([f75fb6f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/f75fb6f60c1c2d6811947c6cefcf383ef1143a27))
* Update dependency gcp-releasetool to v1.16.0 ([#1479](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1479)) ([6facdc1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6facdc1e31b7365585ed0480e0ebecb1d5d29e8b))
* Update dependency google-cloud-core to v2.3.3 ([#1467](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1467)) ([048af3a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/048af3a95dd1ae2dab1b1502bbec8f9c3142daf8))
* Update dependency google-crc32c to v1.5.0 ([#1483](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1483)) ([d9be9af](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d9be9afc0c62ab8d88338e466910c5f7256359cf))
* Update dependency io.projectreactor:reactor-core to v3.5.9 ([#1420](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1420)) ([7473f98](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7473f98d8a0f36e14d52f5611f79763b38446c6d))
* Update dependency io.r2dbc:r2dbc-pool to v1.0.1.RELEASE ([#1408](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1408)) ([067e67a](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/067e67a711f81f1c135f600d86e43ea7a101ccfa))
* Update dependency org.graalvm.sdk:graal-sdk to v23 ([#1411](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1411)) ([2c78283](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2c782836dd2df5139feaf4c1e3ee03c7619d1f79))
* Update dependency org.postgresql:r2dbc-postgresql to v1.0.2.RELEASE ([#1413](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1413)) ([dbad4aa](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/dbad4aa484f8585b1c259267d4c945fb42ba6e99))
* Update dependency protobuf to v3.20.3 ([#1468](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1468)) ([dfc7234](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/dfc72340cddbc283fdbe23c36b72941aef1cbf5a))
* Update dependency urllib3 to v1.26.16 ([#1469](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1469)) ([16938d3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/16938d3dea6b4f435420729dc405aa243c106154))
* Update graalvm/setup-graalvm digest to 0e29e36 ([#1462](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1462)) ([a97f4eb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/a97f4eb81afd62ef9dd39e214eef6b95a345f046))
* Update junit5 monorepo ([#1366](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1366)) ([fdf3eac](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fdf3eac55caf8ba8383287ba5d50b6d5395bf039))
* Update multiple dependencies. ([#1417](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1417)) ([e18f930](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e18f93059b2edd573cd263192773cf7278d80993))
* Update native-image.version to v0.9.26 ([#1421](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1421)) ([0a0a2a1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0a0a2a1de0965210f0c879b9c64470046f65a5d5))

## [1.13.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.13.0...v1.13.1) (2023-07-20)


### Bug Fixes

* Increase threadpool count to avoid deadlocks ([#1391](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1391)) ([75fef46](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/75fef46f38732877e01fd7e4f1c5df189794eee7)), closes [#1314](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1314)
* remove race condition bug in refresh logic ([#1390](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1390)) ([c0a5d58](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c0a5d580bfcfe92954872522d8354a38f57f3d80)), closes [#1209](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1209) [#1159](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1159)

## [1.13.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.12.0...v1.13.0) (2023-07-11)


### Features

* Add support for PSC network connections ([#1347](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1347)) ([4474f16](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4474f16b986c0043c980f4f60dffc06801a63a16))


### Bug Fixes

* Use explicit project version in distributionManagement section. This ([#1338](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1338)) ([86afe6f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/86afe6f55a7909d877d598e46b7fa5a020b8b54c))

## [1.12.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.11.2...v1.12.0) (2023-06-12)


### Features

* Use new certificate refresh logic ([5ad6103](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/5ad61033dd598cf4b1170d159a0b822d04fc61fd))


### Bug Fixes

* Fix refresh futures to avoid a hanging future when an api request fails during refresh. ([#1319](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1319)) ([1277b5e](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1277b5eddcccf1828c45dbf2a37f5bc1f0da8e5c))
* log error when token is invalid ([#1313](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1313)) ([2130317](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2130317355ea4b4c7bfc1e1a62ee8e51168320a4)), closes [#1174](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1174)
* Retry when attempting to get the auth token ([#1301](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1301)) ([2694cc5](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2694cc593c64175780282cdc8b7ea21bdb14aa19)), closes [#1288](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1288) [#1127](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1127)

## [1.11.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.11.1...v1.11.2) (2023-05-10)


### Bug Fixes

* deprecate support for MySQL connector/J 5 ([#1278](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1278)) ([44d6e51](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/44d6e51f2a6a2bce77659007e534dfba50ddd961))
* update dependencies to latest versions ([#1285](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1285)) ([52b3715](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/52b37158e46bf623f3ecf98b7c8ccfbc1e29edd8))

## [1.11.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.11.0...v1.11.1) (2023-04-10)


### Bug Fixes

* throw when token is expired or empty ([#1233](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1233)) ([970eed0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/970eed076c6544ac05f1e57871dc28723db3a2b4))

## [1.11.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.10.0...v1.11.0) (2023-02-27)


### Features

* add support for MariaDB SocketFactory connector ([#1169](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1169)) ([6890cb6](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6890cb6ce301365842d3045b85029fdc803b8744))

## [1.10.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.9.0...v1.10.0) (2023-02-07)


### Features

* improve reliability of refresh operations ([#1147](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1147)) ([e7c7bdd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e7c7bdd669001373b5f2775816aa5ed1c52bb1d6))

## [1.9.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.8.3...v1.9.0) (2023-01-23)


### Features

* enable setting ipType connection option for r2dbc drivers ([#937](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/937)) ([559952f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/559952f8246112c4d328d0cc620f0eb3806bbbc5))

## [1.8.3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.8.2...v1.8.3) (2023-01-18)


### Bug Fixes

* support Google OAuth Client credentials without expiration time ([#1117](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1117)) ([7b2aff4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/7b2aff4c5d4ef17c4ffb46abefa5ab5a3c1160c7))

## [1.8.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.8.1...v1.8.2) (2023-01-13)


### Bug Fixes

* support credentials from Google OAuth Client ([#1097](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1097)) ([cf024eb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/cf024eb609cb79f4e819ecf25b74f1787885616a))

## [1.8.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.8.0...v1.8.1) (2023-01-11)


### Bug Fixes

* throw exception on invalid IAM Authn config ([#1082](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1082)) ([2100d24](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2100d2424bae696dba857e57235bb2e374938614))


## [1.8.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.7.2...v1.8.0) (2022-12-08)


### Features

* enable setting ipType configuration option for SQL Server connector ([#936](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/936)) ([e76518d](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/e76518dbe4896d674d042ffc77233f462587d4cf))
* support MySQL Automatic IAM Authentication ([#981](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/981)) ([dc7d7ba](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/dc7d7babf807fa9029fa72b8f884f1e0eecf22bd))


### Bug Fixes

* add compatibility for GraalVM 22.2.0 ([#1025](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/1025)) ([bd153cd](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/bd153cda0e82cbd1ea9d290c9123d510bdca9d22))

## [1.7.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.7.1...v1.7.2) (2022-11-02)


### Bug Fixes

* downscope credentials used for IAM AuthN login ([#999](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/999)) ([acb57cb](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/acb57cb9ce803062651a4b4764e9181237d84a23))

## [1.7.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.7.0...v1.7.1) (2022-10-20)


### Bug Fixes

* eliminate race condition in underlying auth library ([c6df99f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c6df99f3e894d95ffe66b0b129085ae6777f1667))

## [1.7.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.6.3...v1.7.0) (2022-09-09)


### Features

* **jdbc/mysql-j-8:** Add native image support for jdbc/mysql-j-8 ([#966](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/966)) ([15e01f4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/15e01f41e4802ff49090cd7cd62dec7b6b2c6a57))


### Bug Fixes

* Add Automatic-Module-Name to MANIFEST.MF for JDK9+ module compatibility ([#953](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/953)) ([1dfceaf](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/1dfceafc01ea0d02778485ba1d25ed3746995d45))
* default to using TLSv1.3 ([#939](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/939)) ([3b1c713](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3b1c7132959b7b24657246da03cdb1a92bdb5764))

## [1.6.3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.6.2...v1.6.3) (2022-08-02)


### Bug Fixes

* update dependencies to latest versions ([#932](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/932)) ([23c2779](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/23c277919556b2006b7f5ec9ed8ed40c3d6f784d))

## [1.6.2](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.6.1...v1.6.2) (2022-07-12)


### Bug Fixes

* add back missing dependencies to JAR files ([#904](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/904)) ([3028ece](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3028ecef050c0fa063ee6bd62197029e54dedaa9))

## [1.6.1](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.6.0...v1.6.1) (2022-06-07)


### Bug Fixes

* upgrade dependencies to latest versions ([#868](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/868)) ([fb814e7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fb814e72e7ed95dda1bc619899040068a5bf3460))

## [1.6.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.5.0...v1.6.0) (2022-05-03)


### Features

* **jdbc/postgres:** add compatibility for GraalVM native image ([#805](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/805)) ([c00c255](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c00c25533e31caadf0db8c50077cc1c310fd106c))

## [1.5.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.4.4...v1.5.0) (2022-04-01)


### Features

* Add language support policy ([#787](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/787)) ([0b7e9a7](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/0b7e9a79f49d7e50bf3ce5982e0d021d2ed45eea))

### [1.4.4](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.4.3...v1.4.4) (2022-02-25)


### Bug Fixes

* include value of INSTANCE_CONNECTION_NAME when invalid ([#752](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/752)) ([12e3e7b](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/12e3e7bcd82cba3d625d5c05bec7f0120c8f2ad6))

### [1.4.3](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.4.2...v1.4.3) (2022-02-01)


### Bug Fixes

* update dependencies to latest versions ([#730](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/730)) ([907f759](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/907f7598b757e2dbf66e97c5e51850f5cc1cfa49))

### [1.4.2](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.4.1...v1.4.2) (2022-01-04)


### Bug Fixes

* Ensure all required dependencies are declared ([#634](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/634)) ([2fe4bf4](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2fe4bf4a6dae64da16d07528830af3327efd599b))
* **r2dbc:** parse 'enable_iam_authn' as String or Boolean as needed ([#688](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/688)) ([d294864](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/d2948640cb111f5b0f621da0cdaa68843feefe7b))

### [1.4.1](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.4.0...v1.4.1) (2021-12-07)


### Bug Fixes

* update dependencies to latest versions ([#671](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/671)) ([ae81368](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/ae81368bc6e0e7d838c6d1b11e936e186359ffec))

## [1.4.0](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.3.4...v1.4.0) (2021-11-02)


### Features

* improve reliability of refresh operations ([#635](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/635)) ([9d4ebe4](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/9d4ebe405c9c67f5c7ac741c0f6c4fde784d7f7c))


### Bug Fixes

* add undeclared dependency ([#648](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/648)) ([4017d25](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4017d25cf16c4c9a48d87f23f0c0f84c5dd5f2ac))

### [1.3.4](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.3.3...v1.3.4) (2021-10-05)


### Bug Fixes

* update dependencies to latest versions ([#617](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/617)) ([6be109a](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/6be109a95f224c43d1c677e9d1be97b6d05f3b8b))

### [1.3.3](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.3.2...v1.3.3) (2021-09-07)


### Bug Fixes

* update dependencies to latest versions ([#597](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/597)) ([3cba563](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3cba563023ac1cfcafd94d877865d1d315bb78a6))

### [1.3.2](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.3.1...v1.3.2) (2021-08-03)


### Bug Fixes

* only replace refresh result if successful or current result is invalid ([#561](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/561)) ([01226b0](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/01226b000b4596c76ed5809d02853b353725b669))
* **r2dbc:** fetch updated SSLData for each new connection ([#554](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/554)) ([007759c](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/007759ce9f63ed4247dbeef61772f86f6708d0ac))
* remove dependency on internal sun.security.x509 classes ([#564](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/564)) ([79250e2](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/79250e24bbe89ab39d506910b5fb1160a4c1f695))
* strip padding from access tokens if present ([#566](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/566)) ([406bb66](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/406bb66a37f34ba8b7a7a6f84a64a9e20e9cc925))

### [1.3.1](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.3.0...v1.3.1) (2021-07-13)


### Bug Fixes

* exclude unreachable dependencies ([#512](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/512)) ([8b69577](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8b69577c8653ca61fa6ac66e021cd03d565f05e8))

## [1.3.0](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.2.3...v1.3.0) (2021-05-27)


### Features

* add support for Postgres IAM authentication in JDBC and R2DBC connectors ([#490](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/490)) ([3799c78](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/3799c78c257d3aafcb535ca5d339f87dddee8843))


### Bug Fixes

* exclude unreachable optional dependency in r2dbc-core ([#510](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/510)) ([448a353](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/448a35339edde00be43b509239457b1c856d855a))
* require TLSv1.3 when connecting using IAM authentication ([#506](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/506)) ([822a203](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/822a203ce25d49c58cd1d6a843b60da23d16fbd1))

### [1.2.3](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.2.2...v1.2.3) (2021-05-03)


### Bug Fixes

* declare used maven dependencies ([#478](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/478)) ([8483003](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/8483003e2a6b0ac5bc813b9ad8995ff8acdd14ae))

### [1.2.2](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.2.1...v1.2.2) (2021-04-06)


### Bug Fixes

* update dependencies to latest versions ([#452](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/452)) ([48c4c83](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/48c4c837c9aee87277cd64650139597c033e0bae))

### [1.2.1](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.2.0...v1.2.1) (2021-02-16)


### Documentation

* fix error in r2dbc URLs ([#384](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/384)) ([52990d0](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/52990d00254d1e36e8e1fc8e788a6953ef1b3722))

## [1.2.0](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.1.0...v1.2.0) (2020-11-18)


### Features

* add r2dbc support for MS SQL Server ([#328](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/328)) ([fddcc7f](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/fddcc7f42f63caa9fc3026fa6348df5cf0751af8))
* **mysql:** Deprecated the mysql-socket-factory-connector-j-6 artifact ([#342](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/342)) ([c11b63a](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/c11b63a574d4c55c71d191ef2070b5e770b17b9e))
* add SQL Server JDBC support ([#263](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/263)) ([2a60a67](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/2a60a67d3a7a31f4138894e8cc8e5dfd6b3a2c04))
* use regionalized instance ids to prevent global conflicts with sqladmin v1 ([#303](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/303)) ([4bacca4](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/4bacca40b49cc1867a06cf4b2b7cc04c94ad9a07))


## [1.1.0](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/compare/v1.0.16...v1.1.0) (2020-09-15)


### Features

* Add r2dbc support for postgresql and mysql ([#231](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/231)) ([279c619](https://www.github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/279c619e66c83bf94d9168d6ab512512c3042c68))
* Fix dependency convergence errors ([#235](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/235))([462fc4f](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/commit/462fc4ffeb20b3f7f5243e86619bcecaa24157f0))
