# MariaDB compressed-binlog redesign

Date: 2026-09-14

Debezium target: `v3.6.2.Final` at
`02810e25b19c04e5095b2b6fbbdcbae549a69f19`.

Binlog-client target: official `v0.41.2` at
`d01d65d957f905e7cc32c29cb6ce9218e78eb949`.

## Scope and RED evidence

Commits `77f114315e`, `7482d01c65`, `2367178a87`, and `7aa7314449`
depended on a separate `0.40.2` client fork. Official 0.41.2 supports MySQL
`TRANSACTION_PAYLOAD`, but its event enum ends MariaDB support at event 163 and
does not recognize MariaDB compressed Query or row events 165 through 171.

The existing MariaDB `TransactionPayloadIT` initially passed against official
0.41.2, but its rows were below the default compression threshold. After adding
a 1,000-character DDL and 2,000-character row payload, the DDL produced no
schema record and the test timed out after 30 seconds. This was the required
transport RED, independent of the already-fixed DDL grammar.

## 0.41.2 client redesign

A new isolated worktree was created at
`/Users/tuchief/workspace/mysql-binlog-connector-java-0.41-rps`, branch
`upgrade/mysql-binlog-0.41-rps`. Its local artifact is
`io.debezium:dataknown-mysql-binlog-connector-java:0.41.2-rps.1-SNAPSHOT`.
The final locally installed JAR SHA-256 is
`706992248d5ea121b7a7e46bf1d536a0c0dcd9590802f0db89c0f8dcb925b21e`.

The implementation contains only the required protocol surface:

- EventType values 165-171 and row-mutation classification.
- Strict MariaDB zlib payload decoding with big-endian original length,
  malformed-input bounds checks, and a 1 GiB protocol cap.
- Query decompression delegated to the standard Query Event parser.
- Status-variable retention for ordinary and compressed Query Events.
- Separate V1 and V2 row reconstruction. V2 preserves its variable-length
  post-header; this was missing from the historical implementation.
- Compatibility-mode propagation into the wrapped row deserializer. A real
  MariaDB test first returned `Date`; after this fix, `DATETIME(6)` returned the
  expected epoch value with no eight-hour shift.

The historical BinaryLogClient timeout doubling, Lombok/Guava/Hutool
dependencies, standalone listener, factory, and ad-hoc test applications were
not migrated.

The event-number and compression behavior is consistent with the MariaDB
documentation at https://mariadb.com/kb/en/compressing-events-to-reduce-size-of-the-binary-log/
and MariaDB 11.8.9 `sql/log_event.cc`.

## Debezium 3.6 integration

- The BOM, binlog module, and MySQL module now resolve the aligned RPS client.
- `BinlogStreamingChangeEventSource` routes compressed Query Events through the
  existing DDL path and all six compressed row types through the existing
  insert/update/delete paths.
- Compressed rows delegate to Debezium's current `RowDeserializers`, preserving
  conversion and failure-handling behavior. V1 and V2 are configured
  independently.
- The earlier Task 4 connector-local Query Event subclass was removed. Ordinary
  and compressed Query Events now share the fork's retained status-variable
  API, while Debezium retains only the `lc_time_names` parser and
  `source.lc_time` contract.
- MariaDB test setup sets `log_bin_compress_min_len=10`, ensuring the compressed
  tests actually exercise compressed event types.

## Verification

- Client focused protocol suite: 15 tests, no failure, error, or skip.
- Client unit suite: 127 tests, no failure, error, or skip.
- Real MariaDB 10.6 client test: compressed Query, INSERT, UPDATE, DELETE, and
  `DATETIME(6)` passed.
- Debezium MariaDB 11.4.3: all three `TransactionPayloadIT` scenarios passed,
  including compressed DDL/DML/DATETIME and restart/skip behavior. Runtime logs
  confirmed `WRITE_ROWS_COMPRESSED_V1` during restart.
- Binlog non-Docker suite, MySQL parser/source suite, and MariaDB parser/source
  suite passed after dependency alignment.
- Dependency tree contains only
  `dataknown-mysql-binlog-connector-java:0.41.2-rps.1-SNAPSHOT` for the binlog,
  MySQL, and MariaDB modules.

## Remaining acceptance boundary

The dependency is a local SNAPSHOT and has not been committed or published.
MariaDB 10.6 and 11.4.3 exercised V1 compressed row events; V2 reconstruction
has exact byte-level unit coverage but still needs a real server/binlog fixture
that emits event types 169-171. GoldenDB numeric `lc_time_names` on a compressed
Query Event also remains a site acceptance case.

## External MariaDB 11.8.5 replica-binlog acceptance

On 2026-09-15, `MariadbExternalCompressedEventsIT` connected the aligned
0.41.2 RPS client directly to the supplied `.137` replica binlog while writes
were made only to `.136`. Replica delay was temporarily changed from 300 to 0
and restored unconditionally.

The test used a unique schema and large DDL/row values above the configured
256-byte threshold. It received and decoded the exact server event types 165
through 168: compressed Query plus compressed V1 Write, Update, and Delete.
The insert retained the expected primary key, `DATETIME(6)` epoch value, and
4,000-character payload; update and delete values also matched.

The authorized test passed 1/1 in 19.67 seconds. A separate no-opt-in run
failed in `@BeforeClass` before opening a database connection, proving the
mutation guard. Independent post-test queries confirmed `.136` writable,
`.137` read-only, healthy `.136 -> .137` replication, no replication errors,
`Using_Gtid=Slave_Pos`, `SQL_Delay=300`, GTID `0-136-23475` on both nodes, and
no test schema.

Final harness review moved the replica-delay restoration flag ahead of the
first topology mutation and restores the JVM default time zone in unconditional
cleanup. The client unit suite passed 127/127 again after this safety-only
change.

This closes real MariaDB V1 compression through the same replica-binlog path
used by replica-only CDC. It confirms that this environment does not provide
real V2 evidence; 169-171 remain byte-fixture-only until a server or captured
binlog that emits those event types is supplied.

## MySQL 8.4.12 amd64 integration acceptance

The Debezium MySQL Docker harness was run on the arm64 host with its configured
`linux/amd64` platform and the official MySQL Community Server 8.4 image. The
server reported MySQL 8.4.12.

`TransactionPayloadIT` passed 3/3 against a real server for compressed
transaction DDL/DML/DATETIME, multiple write events, and restart/skip behavior.
The restart log recovered the prior file/position and
`last_binlog_event_ts_ms`, skipped the already processed row, and emitted no
duplicate.

The GTID primary/replica profile then ran `MySqlRestartIT`; it passed 1/1. The
connector recovered GTID `...:1-21`, observed `...:1-22`, registered the binlog
reader from the prior set, skipped previously processed row events, and ended
at `...:1-22` with a non-regressing event timestamp.

Each Maven lifecycle also completed the MySQL 397-test unit suite (4 skips),
the 2 architecture tests, service-registration enforcement, and Checkstyle.
The standalone profile removed its MySQL container; the GTID profile removed
both primary and replica containers. A post-run Docker query found no test
MySQL, Kafka, or Testcontainers container.

A later final inventory showed three unrelated exited containers created on
2025-09-04 and 2026-08-26. None carried this run's Testcontainers session, so
they were preserved rather than treated as cleanup targets.

This is functional amd64 image evidence under Docker Desktop emulation, not
native x86_64 host performance evidence.

## Clean replay and build closure

Both uncommitted patch sets were replayed onto detached worktrees at upstream
client `v0.41.2` and Debezium `v3.6.2.Final`, then built with JDK 21 against an
initially empty 564 MiB Maven repository. The client passed 127/127 tests. The
Debezium reactor completed all 22 selected modules after resuming one transient
partial-download failure; tests were skipped in that packaging reactor because
the real MySQL/MariaDB and affected unit suites were run separately.

The clean dependency tree contains exactly the aligned
`dataknown-mysql-binlog-connector-java:0.41.2-rps.1-SNAPSHOT` coordinate for
binlog, MySQL, and MariaDB. A same-JDK rebuild of the source client produced a
different archive checksum because ZIP entry timestamps are not normalized,
but the two extracted JAR trees and the two source trees were identical. See
`docs/rps-migration/task9-clean-patch-replay-build-2026-09-15.md` for commands,
checksums, and the dependency-lock boundary.
