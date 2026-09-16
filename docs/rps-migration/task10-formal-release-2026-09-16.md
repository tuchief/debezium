# Formal client and Debezium release

Date: 2026-09-16

Repository: `http://10.169.190.188:18587/repository/maven-releases/`

## Coordinates and source commits

- Client: `io.debezium:dataknown-mysql-binlog-connector-java:0.41.2-20260916.Final`
  from commit `097cef8` on `upgrade/mysql-binlog-0.41-rps`.
- Debezium runtime: `3.6.2-20260916.Final` from commit `c57e1e04b9`
  on `upgrade/debezium-3.6-rps`.
- Superseded initial BOM:
  `io.debezium:debezium-rps-bom:3.6.2-20260916.Final`.
- Active consumer BOM:
  `io.debezium:debezium-rps-custom-bom:3.6.2-20260916.Final`.

All 44 intended main JAR/POM paths returned HTTP 404 before publication.

## Verification before publication

- Client exact release tree: 127 tests, 0 failures, 0 errors, 0 skips.
- Connector common: 421 tests, no failures/errors/skips.
- Binlog: 90 tests, no failures/errors/skips.
- MySQL non-Docker: 388 tests, 0 failures/errors, 4 skips; ArchUnit 2/2.
- MariaDB non-Docker: 314 tests, no failures/errors/skips; ArchUnit 2/2.
- Oracle: 436 tests, 0 failures/errors, 3 skips; ArchUnit 2/2.
- MySQL and MariaDB each omit the nine-test `KafkaSchemaHistoryTest` from the
  non-Docker run because that unit-named class starts Testcontainers.
- Formal Debezium packaging compiled test sources, skipped test execution, and
  built/installed all 24 selected reactor modules successfully from release
  commit `c57e1e04b9`.

The first formal packaging attempt used `maven.test.skip=true` and correctly
failed because downstream modules require upstream test JARs. The successful
command used `skipTests`, which compiles/packages test JARs without re-running
tests.

## Corrected selective BOM behavior

The initial RPS BOM incorrectly overrode unchanged Debezium runtime artifacts
with the dated internal version. It remains immutable in Nexus but is
deprecated and must not be imported.

The active `debezium-rps-custom-bom` imports upstream
`io.debezium:debezium-bom:3.6.2.Final` and overrides exactly seven artifacts:

- `dataknown-mysql-binlog-connector-java:0.41.2-20260916.Final`;
- `debezium-connector-common`, `debezium-ddl-parser`,
  `debezium-connector-binlog`, `debezium-connector-mysql`,
  `debezium-connector-mariadb`, and `debezium-connector-oracle` at
  `3.6.2-20260916.Final`.

API, util, config, embedded, connect-plugins, storage, PostgreSQL, DB2, SQL
Server, and IBM i remain at official `3.6.2.Final`. The effective BOM and the
RPS dependency tree independently confirmed this split.

## Published and independently downloaded assets

The client main JAR, POM, sources, and javadoc all returned HTTP 200 and
matched their local SHA-256 values:

| Asset | SHA-256 |
| --- | --- |
| client JAR | `f6f72b4a5a2682990b5d0fdf0e2db17012594c692080fca586565761969edec0` |
| client POM | `bd4a375a54353dd0e3042b540423c18460e4f85417591bef49e5a91588a3cdb8` |
| client sources | `53a6aa2c82ad04641d554cbe508e60ca85616afcad3de1777d4f9e3e7e2539c4` |
| client javadoc | `6c99bda525577380463409f67b666e266537ce04cee9f4a86eacd976c2302432` |

Twenty-one Debezium components were initially published with POM metadata. All were
downloaded independently and matched local SHA-256. POM-only components also
verified that the main JAR remains HTTP 404.

This table is retained as publication history. Only connector-common,
ddl-parser, binlog, MySQL, MariaDB, and Oracle are active dated Debezium JARs.
The other dated assets are deprecated and unused; they were not deleted or
overwritten.

| Component | Packaging | Main SHA-256 |
| --- | --- | --- |
| `debezium-build-parent` | POM | `9a2377e3a5a83ac2c3aa4391ee2c29ee4c9967a9ab52e24ea5cb138d0a5f4689` |
| `debezium-bom` | POM | `e32b948887892561418a101cd2bd6f8b1d58e6e96971ebab66fac68c9a8b647d` |
| `debezium-rps-bom` | POM | `2afd3eda9b8cd2aaad93444f33f972a552c6558cbe89670982cdec13b3e89738` |
| `debezium-parent` | POM | `a3055ec0be38806814114fc74328a242d4510f933daa0c71babbe31846c2ab51` |
| `debezium-util` | JAR | `8ee2c7ebd6303edb33960e847bd26983738243335e771cd57da43fbd96a640bf` |
| `debezium-api` | JAR | `3008fa2f7265b647fed9eaf4a146d0af81adb29f2a043c85bd854dafadf2999e` |
| `debezium-config` | JAR | `bb2e8ef53c7056f8c80a1adcf06d1137c275e479d70128724c0747259423beca` |
| `debezium-openlineage` | POM | `c5d4cbfc4187450d1f5d6fa6078c54326fc7f2d6037ce9b107ad6875530a0ec8` |
| `debezium-openlineage-api` | JAR | `d4de4a1059014a9686d72b561d35cd658b67f28f3c60caa62b8424110e7295d0` |
| `debezium-connector-common` | JAR | `78076ec6cead5294d3a9827c14582be063ee184aaa7700e7487f87a95e916b92` |
| `debezium-ddl-parser` | JAR | `a4c9bb106326b1a08fc07712fb8f05254b4b0bbab921c2195fb21154cdb53dac` |
| `debezium-embedded` | JAR | `2ef972fdd403e03d3fdd5e943f72070feb43f14df4829ea9a0d199437bc6832c` |
| `debezium-connect-plugins` | JAR | `8ff3107ef2a7108a231dad7c70fe1bd14a1788ce0c70c36a674dac175bf5db16` |
| `debezium-core` | POM | `9c6ca5a3ede7644c7577007f3b00a681025409788b1a63a610faff6e20e2f935` |
| `debezium-storage` | POM | `18003dbcb37777a327d7bd4f727757ea847df3b5febbedd7402d4fadee6667f7` |
| `debezium-storage-kafka` | JAR | `89eee5c4a0fe11678ed5f2f15c661e01059434503ed74e9d4fe652f29b9481e8` |
| `debezium-storage-file` | JAR | `c453e533d1b959beeef4f41adfa7c34c4c49f718ab808e982ec7645cab5e0e61` |
| `debezium-connector-binlog` | JAR | `7daa2e9d7ede621785130ce32bd5e4175d21f01a61eea81712ee1f2d22131d62` |
| `debezium-connector-mysql` | JAR | `1dfd43a2773262b49a14962f5465378644543f86a74720287e5d1abe64bc2d24` |
| `debezium-connector-oracle` | JAR | `d167cf7aabc3821659d89146d6400c88edd329118e7769deed99fb1a01655b9f` |
| `debezium-connector-mariadb` | JAR | `558677291b4aac31bb732348b21a0790e976b475139ac91b68d2ff3c29df9e03` |

The corrected POM-only BOM was published separately and verified by an
independent download:

| Component | Packaging | Main SHA-256 |
| --- | --- | --- |
| `debezium-rps-custom-bom` | POM | `6650fcf115d71e4d4872ba2015c88ec66e8a1a0ab7faf5ae19c4bea241ad9388` |

Its POM returned HTTP 200, matched the source byte-for-byte, and its nonexistent
JAR path returned HTTP 404.

The failed anonymous client upload and the first POM-only command produced no
assets. Credentials were supplied only through permission-restricted temporary
settings files, removed after each command; no credential was written to either
repository or the user Maven settings.

## Corrected RPS develop alignment

RPS `develop` corrective commit `bbee51a785` imports
`io.debezium:debezium-rps-custom-bom:3.6.2-20260916.Final`. Its resolved
runtime contains:

- only the six changed Debezium modules at `3.6.2-20260916.Final`;
- `dataknown-mysql-binlog-connector-java:0.41.2-20260916.Final` exactly once;
- all unchanged Debezium components at official `3.6.2.Final`;
- official `debezium-connector-ibmi`, `ibmi-journal-parsing`, and
  `jt400-override-ccsid` at `3.6.2.Final`;
- Kafka Connect artifacts converged to RPS-managed `3.7.0`;
- one Oracle connector coordinate, `io.debezium:debezium-connector-oracle`.

The IBM i release comes from Maven Central through the company
`maven-public` group; it is not republished as an internal dated build. The
machine's default Maven global settings incorrectly mirror all repositories to
the hosted `maven-releases` repository, which cannot proxy Central. The clean
verification therefore used a temporary settings file pointing `mirrorOf=*`
to `maven-public`; the temporary file was removed afterward.

Under that correct repository route, JDK 17 `mvn -U clean package -DskipTests
-DskipITs` completed successfully for all five RPS reactor modules. Test
sources compiled, but tests were explicitly skipped; this is clean build
evidence, not an RPS runtime test pass.
