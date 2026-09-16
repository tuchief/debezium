# Task 3 upstream qualification checkpoint

Date: 2026-09-14

Target: unmodified Debezium `v3.6.2.Final` production code at
`02810e25b19c04e5095b2b6fbbdcbae549a69f19`.

This checkpoint qualifies historical RPS backports by running focused regression
tests without copying their production changes. A unit-level pass means the old
production patch is not a rebase candidate. It does not replace the listed
Docker, failover, or performance gate.

## Results

| Behavior | Unit decision | Evidence | Remaining gate |
| --- | --- | --- | --- |
| Embedded-backtick identifier quoting | `UPSTREAM_INTEGRATION_CONFIRMED` | Unit guard and MariaDB 11.8.9 `SpecialCharactersIT` passed | None for source-port decision |
| MariaDB failover GTID containment | `UPSTREAM_UNIT_CONFIRMED` | 9 focused `MariaDbGtidSet` tests passed | MariaDB failover integration scenario |
| Bounded map queue accounting | `UPSTREAM_UNIT_CONFIRMED` | 2 deterministic `BoundedConcurrentHashMapRegressionTest` tests passed | Connector soak and allocation comparison |
| Connector poll batch conversion | `UPSTREAM_UNIT_CONFIRMED` | `BaseSourceTaskTest.shouldConvertPolledEventsInOrderToMutableList` passed | Connector throughput and allocation comparison |
| UUID and VECTOR | `UPSTREAM_INTEGRATION_CONFIRMED` | MariaDB 11.8.9 `UuidColumnIT` and both `MariaVectorIT` scenarios passed | None for source-port decision |
| Kafka Schema History concurrent recovery and producer buffering | `UPSTREAM_INTEGRATION_CONFIRMED` | Real Kafka concurrent recovery plus 4 buffering regression tests passed | Old-state restart remains a release gate |
| Snapshot failure preservation while draining Schema History | `REBASE_IMPLEMENTED_RELEASED` | RED: missing `BufferingScope`/`buffering()`; GREEN: 2 exception-semantics tests passed | Full continuation transferred to product regression |
| Extended system-versioned table coverage | `UPSTREAM_INTEGRATION_CONFIRMED` | MariaDB 11.8.9 snapshot and streaming IT passed | Implicit hidden-column variant remains a release gate |

Focused Maven runs execute selected tests in both the normal and ArchUnit
Surefire executions. Counts above report unique test methods rather than double
counting those executions. All focused executions completed with zero failures
and zero errors.

Affected-module regression after adding the guards:

- `debezium-connector-common`: 410 tests, 0 failures, 0 errors, 0 skipped.
- `debezium-connector-binlog`: 76 tests, 0 failures, 0 errors, 0 skipped.
- `debezium-util`: 165 tests, 0 failures, 0 errors, 0 skipped.

Docker-backed evidence used MariaDB 11.8.9 on Linux arm64. Failsafe executed
5 tests with 0 failures, 0 errors, and 0 skips: one UUID snapshot, two VECTOR
snapshot/streaming scenarios, one system-versioned snapshot/streaming scenario,
and one special-character snapshot scenario. The MariaDB container was stopped
and removed by the post-integration-test phase.

## Decision

Do not port the old production implementations for the upstream-confirmed
behaviors. Retain the characterization tests as compatibility guards. Rebase
only the missing exception-preserving buffering scope; keep MariaDB failover,
implicit system-versioned columns, old-state restart, and performance soak as
release gates rather than claiming they were covered here.
