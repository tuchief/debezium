# G1 Clean Debezium 3.6.2 Baseline

## Scope

- Worktree: `/Users/tuchief/workspace/debezium-3.6-rps`
- Branch: `upgrade/debezium-3.6.2-rps`
- Upstream baseline: `v3.6.2.Final`
- Commit: `02810e25b19c04e5095b2b6fbbdcbae549a69f19`
- JDK: Azul Zulu 21.0.12.1
- Maven Wrapper: 3.9.12

No Debezium production source was changed before these checks.

## Production compilation evidence

The following modules completed independent Maven `package` commands with exit code 0:

- `debezium-connector-common`
- `debezium-storage/debezium-storage-kafka`
- `debezium-connector-binlog`
- `debezium-connector-mysql`
- `debezium-connector-mariadb`
- `debezium-connector-oracle`

The MariaDB command reported `BUILD SUCCESS` in 6.613 seconds. The Oracle command reported `BUILD SUCCESS` in 1 minute 1 second after its first dependency resolution. Enforcer Java and Maven version rules passed.

## Deliberately excluded evidence

- Test resources were not copied.
- Test sources were not compiled.
- Surefire and Failsafe tests were skipped.
- Docker integration tests were skipped.
- Checkstyle was skipped.
- Revapi was skipped.
- The `qa` profile and its exhaustive ANTLR example traversal were disabled with `-Dquick` for the production compile baseline.
- The test-only `debezium-testing-testcontainers` module was excluded from the final reactor selection; connector POM resolution still downloaded some of its released test-scope dependencies.

This checkpoint proves production compilation only. It is not a test pass and does not close G1 until Task 2 Step 3 runs executed unit tests with recorded counts.

## Non-Docker unit-test evidence

Task 2 Step 3 completed with the following fresh results:

| Module | Tests | Failures | Errors | Skipped | Notes |
|---|---:|---:|---:|---:|---|
| `debezium-connector-common` | 406 | 0 | 0 | 0 | Includes queue, JDBC, schema, converter, and common source-task tests. |
| `debezium-storage-kafka` | 0 | 0 | 0 | 0 | Surefire reported `No tests to run`; broker-backed history coverage lives in connector tests. |
| `debezium-connector-binlog` | 72 | 0 | 0 | 0 | Does not include broker-backed or database `*IT` coverage. |
| `debezium-connector-mysql` | 367 | 0 | 0 | 4 | Explicit `*Test.java` set; excludes `KafkaSchemaHistoryTest` and 64 database `*IT` classes. |
| `debezium-connector-mariadb` | 291 | 0 | 0 | 0 | Explicit `*Test.java` set; excludes `KafkaSchemaHistoryTest` and 62 database `*IT` classes. |
| `debezium-connector-oracle` | 425 | 0 | 0 | 3 | 423 normal tests plus 2 ArchUnit tests; excludes 48 `*IT` classes. |
| `debezium-embedded` | 63 | 0 | 0 | 0 | Exercises Embedded and AsyncEmbedded engine lifecycle and offset behavior without an external database. |
| **Surefire total** | **1624** | **0** | **0** | **7** | Executed tests only. |

The DDL parser module has no Java test sources in this baseline. Its bound ANTLR QA profile completed with `BUILD SUCCESS`. Based on its four configured `exampleFiles` roots, it visited 569 non-`.tree` SQL/script files: 64 under MySQL examples, the 36 MySQL legacy files a second time in the dedicated legacy execution, 24 MariaDB files, and 445 Oracle files. The plugin emits known parser diagnostics for some legacy examples while still returning success, so this result is a harness pass, not proof that every statement parsed without a diagnostic.

Task 2 Step 3 and Step 4 are complete for the clean 3.6.2 non-Docker baseline. Kafka broker recovery, MySQL/MariaDB database behavior, and Oracle database behavior remain integration-test work and are not included in the 1,624 count.

## Baseline observations

- `debezium-core` is a relocation POM in 3.6.2; production classes are in `debezium-connector-common`.
- The 3.6.2 parent resolves Kafka `4.3.0`.
- The binlog connector resolves `io.debezium:mysql-binlog-connector-java:0.41.2`, so the internal `0.40.2-*` fork must be assessed against this API rather than copied unchanged.
- Oracle production compilation generated protobuf sources successfully on macOS ARM64.
- Oracle dependency resolution emitted warnings for old JAXB POMs selected while Maven evaluated the Ehcache version range. Several old POMs refer to `${tools.jar}`, which is invalid on JDK 21; these warnings did not fail the Oracle package command.

## Superseded attempts

Two broader reactor attempts were interrupted intentionally:

1. `-DskipTests` still resolved and compiled test dependencies, which expanded the baseline beyond production compilation.
2. `-Dmaven.test.skip=true` without `-Dquick` still ran the DDL parser QA traversal.

The final module commands corrected both issues and produced explicit exit-code-zero evidence. Interrupted attempts are not counted as build passes or failures.

Two MySQL test-selection attempts were also interrupted and excluded from all counts:

1. The default Surefire selection included `KafkaSchemaHistoryTest`, which attempted to pull `quay.io/debezium/testcontainers-ryuk:0.13.0`; the registry returned EOF and Testcontainers began retrying.
2. `-Dtest='*,!KafkaSchemaHistoryTest'` excluded that class but the wildcard also selected database `*IT` classes. Those classes failed immediately with an unexpanded `${protocol}` JDBC URL because the Docker integration-test profile was intentionally absent.

The final MySQL command explicitly enumerated the 18 non-broker `*Test.java` classes and produced the 367-test successful result shown above.
