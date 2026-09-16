# Binlog Client, MySQL, and Clean Build Design

## Goal

Close the Debezium-only release gates for the aligned MySQL binlog client,
execute real MySQL integration acceptance, and prove that the current two-repo
patch set builds with an empty Maven repository.

## Scope

The work covers `/Users/tuchief/workspace/mysql-binlog-connector-java-0.41-rps`
and `/Users/tuchief/workspace/debezium-3.6-rps`. It does not modify RPS, commit,
push, publish, or deploy artifacts.

## Design

### MariaDB compressed protocol

Use the supplied MariaDB 11.8.5 `.136 -> .137` topology without changing its
roles or replication direction. The test temporarily changes replica delay
from 300 to 0 and restores 300 unconditionally. A new opt-in external client test creates one
unique schema on `.136`, keeps a BinaryLogClient on `.137`, performs large
DDL/INSERT/UPDATE/DELETE statements, and asserts exact event types 165-168,
row values, DATETIME compatibility, replication health, and cleanup.

MariaDB Server does not emit V2 row events. Event types 169-171 therefore
remain covered by byte-level protocol fixtures and require a vendor server or
captured binlog that actually emits them. Compression being enabled is not
sufficient to create V2 events.

### MySQL real database

Use the existing Debezium Docker integration harness with the official MySQL
8.4 image and `linux/amd64` platform. Run focused transaction-payload,
DDL/DML, event-time, restart, and GTID scenarios. Docker containers must be
removed by `post-integration-test`; results are not reported as x86 host
performance evidence because Docker Desktop may emulate amd64 on arm64.

### Reproducible build

Create temporary detached worktrees from upstream `v0.41.2` and
`v3.6.2.Final`, stream tracked diffs into them, and copy only the source
worktrees' untracked files. Build first the client and then Debezium with JDK
21 and one empty temporary Maven repository. Record commands, checksums, test
counts, dependency trees, and excluded external gates. Temporary worktrees and
the Maven repository are removed only after their paths are explicitly
validated.

## Safety and acceptance

- Credentials are accepted only through an interactive environment variable
  and never persisted.
- The MariaDB test may touch only its unique schema and must restore roles,
  direction, health, and `SQL_Delay=300`.
- Any unexpected event type, existing test schema, replication error, or
  cleanup failure stops the run.
- Client and Debezium dependency trees must resolve only the aligned client.
- A clean build passes only when the empty-repository build exits zero and the
  produced JAR checksums are recorded.
- Real V2, GoldenDB numeric status variables, RPS runtime, publication, and
  x86 host performance remain explicitly outside this acceptance.
