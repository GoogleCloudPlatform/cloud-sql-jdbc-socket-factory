# Changelog

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
