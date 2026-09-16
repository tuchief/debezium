# Task 6 Oracle custom behavior rebase

Date: 2026-09-14

Status: implementation complete with direct Oracle 10g, Oracle 11g, and
two-node RAC LogMiner row-path acceptance. Full embedded-engine continuation,
RPS filter acceptance, RAC failover, and RAW/HEXTORAW remain open. No commit,
push, publication, RPS dependency change, or deployment was performed.

## Scope

This phase rebases the two Oracle-specific RPS commits onto Debezium
`v3.6.2.Final`:

- `bd7cc1feec`: expose the Oracle transaction name as `source.txName` for the
  RPS self-written transaction filter.
- `f838ded6ae`: qualify LogMiner package references with `SYS` and read Oracle
  10g `V$THREAD` last-redo columns.

The old artifact rename and hard-coded final JAR name were intentionally not
ported. Artifact naming belongs to the later dependency/publication phase.

## Implemented design

### Transaction name

- `TX_NAME` is appended after the existing optional LogMiner columns.
- `LogMinerColumnIndexes` computes its actual ordinal after the configured
  optional columns; existing optional resolver counts and positions remain
  unchanged.
- `LogMinerEventRow` reads the value and buffered transactions retain the first
  available non-null name in their transaction metadata. The commit path
  applies the cached value to `OracleOffsetContext` immediately before each
  change event is dispatched, falling back to the COMMIT row only when needed.
- Memory, Ehcache, and Infinispan transaction implementations carry the field.
  Ehcache appends it to the serialized form and remains able to read legacy
  entries that end after `clientId`; Infinispan uses a new optional protobuf
  field number 8.
- The value is cleared immediately after dispatch so a heartbeat or unrelated
  transaction cannot inherit a stale transaction name.
- `OracleSourceInfoStructMaker` exposes the exact RPS contract name
  `source.txName` as an optional string.

The current RPS Oracle configuration uses the buffered LogMiner path. The
newer 3.6 unbuffered adapter is not claimed as accepted by this phase.

### Oracle 10g implementation

- LogMiner add-file, start-session options, and data-dictionary calls use
  `SYS.DBMS_LOGMNR` / `SYS.DBMS_LOGMNR_D` consistently.
- `OracleConnection.getRedoThreadState()` reads the last-redo columns for
  Oracle major version 10 and later.
- Oracle 10g lacks the 3.6 projection columns `START_SCN`, `COMMIT_SCN`,
  `START_TIMESTAMP`, and `CLIENT_ID`. The version-aware query builder projects
  compatible aliases (`NULL`, or `CSCN` for commit SCN), disables client-ID
  predicates, and keeps JDBC column ordinals aligned with the row parser.
- An explicit client-ID include/exclude configuration fails fast on Oracle 10g
  instead of silently broadening the capture set.
- The 10g behavior is supported by a mocked JDBC contract test. Oracle's 10g
  `V$THREAD` reference documents `LAST_REDO_SEQUENCE#`, `LAST_REDO_BLOCK`,
  `LAST_REDO_CHANGE#`, and `LAST_REDO_TIME`:
  https://docs.oracle.com/cd/B19306_01/server.102/b14237/dynviews_2166.htm
- Oracle's 10g package reference identifies `DBMS_LOGMNR_D` as the package for
  dictionary extraction and requires `EXECUTE_CATALOG_ROLE`:
  https://docs.oracle.com/html/B14258_02/d_logmnrd.htm

The implementation was subsequently executed against the supplied Oracle
10.2.0.4 service; see the live evidence below.

## TDD evidence

RED evidence:

- Source schema and row tests initially failed to compile because transaction
  name accessors did not exist.
- Buffered dispatch test failed because `setTransactionName("RPS_ORIGIN")` was
  never called.
- Three LogMiner session tests failed because 3.6 still emitted unqualified
  package constants.
- The Oracle 10g redo-state test failed because last-redo columns were read
  only for major version 11 or later.

GREEN evidence:

- Source/schema/query/row tests: 42 tests, 0 failures, 0 errors, 0 skips.
- Oracle 10g SQL/query-builder/redo-state focused unit tests: 37 tests,
  0 failures, 0 errors, 0 skips.
- Oracle 10.2.0.4 live LogMiner tests: 4 tests, 0 failures, 0 errors, 0 skips.
- Full Oracle non-database suite: 436 tests, 0 failures, 0 errors, 3 skips.
- Architecture suite: 2 tests, 0 failures, 0 errors, 0 skips.
- `git diff --check` passed.

The first full suite run found seven failures because the first implementation
counted mandatory `TX_NAME` as an optional resolver. The implementation was
corrected to retain the 3.6 optional-resolver invariant, and the full suite
then passed.

## Open acceptance gates

1. Run an actual `SET TRANSACTION NAME 'RPS_ORIGIN'` transaction and prove the
   emitted record contains `source.txName=RPS_ORIGIN` and the RPS consumer
   suppresses only that transaction.
2. Repeat across a LogMiner query-window boundary and connector restart.
3. Repeat the direct Oracle 10g and Oracle 11g tests with the production
   connector account and its exact grants.
4. Run RAC node-failure/failover and non-UTF-8 RAW/HEXTORAW acceptance
   scenarios on matching environments.
5. Validate continuation from copied 3.0.3 offsets and schema history in Task
   7 before any release artifact is published.

## Oracle 11g live evidence — 2026-09-15

The supplied service `oracle11g` was validated without storing credentials in
the repository or test reports:

- Oracle Database 11g Enterprise Edition 11.2.0.4, 64-bit.
- Database name `oracle11`, service name `oracle11g`, user/schema `DATAKNOWN`.
- `ARCHIVELOG`, supplemental minimum logging `IMPLICIT`, supplemental all
  columns `YES`.
- Database character set `AL32UTF8`; national character set `UTF8`.
- The session exposes `CREATE SESSION`, `EXECUTE ANY PROCEDURE`,
  `FLASHBACK ANY TABLE`, `SELECT ANY DICTIONARY`, and
  `SELECT ANY TRANSACTION`.
- `V$DATABASE`, `V$THREAD`, `V$LOG`, `V$LOGFILE`, `V$ARCHIVED_LOG`,
  `V$TRANSACTION`, and `V$LOGMNR_LOGS` were readable.
- `V$LOGMNR_CONTENTS.TX_NAME` exists, and `V$THREAD` exposes all four
  `LAST_REDO_*` columns used by the 10g/11g compatibility change.
- `SYS.DBMS_LOGMNR.START_LOGMNR` and `SYS.DBMS_LOGMNR.END_LOGMNR` executed
  successfully with the 3.6 online-catalog/continuous-mining options.

`LogMinerTransactionNameIT` creates a unique table, executes
`SET TRANSACTION NAME 'RPS_ORIGIN_A09D99'`, commits one INSERT, and then uses
the actual 3.6 `BufferedLogMinerQueryBuilder`, `LogMinerSessionContext`,
`LogMinerColumnIndexes`, and `LogMinerEventRow` against the real
`V$LOGMNR_CONTENTS` result set. The single-window case confirms that both the
DML row and matching COMMIT row carry the expected transaction name.

The second live case intentionally splits the same type of named transaction
across two LogMiner windows and restarts the mining session at the split SCN.
Oracle 11g returns the name on the first-window INSERT but returns null on the
second-window COMMIT. This disproved the old patch's commit-row-only
assumption. A RED buffered-processor test reproduced the resulting null source
field. The implementation now retains the DML transaction name in the
transaction cache and reuses it at commit. The final live characterization
passed 2/2, the cache regression passed, and Checkstyle completed successfully.

Mutation evidence was also recorded: temporarily replacing the real
transaction-name read with `null` caused the same live test to fail with
`expected RPS_ORIGIN_A09D99 but was null`; restoring the parser made the test
pass again. The unique test table was dropped after every completed run and a
separate cleanup removed the table left by an interrupted engine attempt.

Ehcache persistence is covered separately: a new-format transaction retains
the transaction name after serialization, and a byte stream written in the
legacy format remains readable with a null transaction name. This protects
rolling restart compatibility for pre-upgrade Ehcache state. Infinispan uses
an additive optional protobuf field and its existing embedded-cache suite
passes in the full Oracle unit run.

### Environment boundary found

A full embedded-engine `snapshot.mode=no_data` attempt was started but not
counted as acceptance. This shared Oracle instance contains a large number of
historical test schemas and tables. The Oracle schema-registration phase
enumerated and registered thousands of objects and did not reach streaming in
the bounded run even with a single table include. The run was interrupted and
cleaned up. A streaming-only custom snapshotter was evaluated and rejected
because a fresh connector without an existing offset has no
`OracleOffsetContext`; 3.6 correctly cannot enter LogMiner streaming that way.

Therefore the remaining full connector/RPS filter, restart, and cross-query
tests require either a dedicated small Oracle schema/instance or copied
production-compatible offset plus schema-history state. The live LogMiner
row-path result is positive evidence, but it is not presented as full RPS
end-to-end acceptance.

### LOB and savepoint rollback evidence

The Oracle 11g live test table was changed to use a CLOB column. A named
transaction inserted `before-savepoint`, established a savepoint, updated the
CLOB to a 5KB value, rolled back to the savepoint, and committed. Acceptance
verified all of the following:

- the final database CLOB value remained `before-savepoint`;
- LogMiner exposed a rollback-marked row for the same transaction;
- the INSERT retained the expected Oracle transaction name;
- the matching COMMIT retained the same transaction name in a single window.

The complete live class passed 3/3. Separately, the existing buffered
`testSavepointRollbackInsertWithNullLob` test passed for both the memory and
embedded Infinispan cache implementations (2/2), confirming that the rollback
event is not dispatched. This covers the database row path and buffered
processor path independently. Full Embedded Engine/RPS output remains part of
the continuation gate because schema-only initialization is not bounded on
this shared instance.

The Oracle 11g service is standalone and `AL32UTF8`. Oracle 10g and non-UTF-8
row-path evidence were supplied by the separate 10.2.0.4 environment below;
RAC redo-thread evidence was supplied by the two-node environment recorded
after it.

## Oracle 10g live evidence — 2026-09-15

The supplied `ora10g` service was validated without storing credentials in the
repository or test reports:

- Oracle Database 10g Enterprise Edition 10.2.0.4.0, 64-bit.
- `ARCHIVELOG`; supplemental minimum and all-column logging both `YES`.
- Database character set `ZHS16GBK`; national character set `AL16UTF16`.
- `V$THREAD` exposes all four `LAST_REDO_*` fields used by the version change.
- `V_$LOGMNR_CONTENTS` does not expose `START_SCN`, `COMMIT_SCN`,
  `START_TIMESTAMP`, or `CLIENT_ID`; it exposes `CSCN` and `TX_NAME`.

The original unqualified package call reproduced the exact compatibility
failure: `DBMS_LOGMNR.END_LOGMNR` returned `PLS-00201`/Oracle error 6550.
The `SYS.DBMS_LOGMNR.END_LOGMNR` form resolved the package and returned the
expected error 1307 because no mining session was active. The production
`LogMinerSessionContext` then started and ended real mining sessions through
the SYS-qualified path during all live tests.

The first 3.6 query failed with `ORA-00904: CLIENT_ID`; after omitting that
unsupported field it failed with `ORA-00904: START_TIMESTAMP`. The final
version-aware projection uses `NULL AS START_SCN`, `CSCN AS COMMIT_SCN`, and
`NULL AS START_TIMESTAMP`, omits `CLIENT_ID`, and shifts `TX_NAME` through the
shared `LogMinerColumnIndexes` calculation. Both buffered and unbuffered query
builders have focused regression coverage; the RPS runtime acceptance remains
the buffered path.

`LogMinerTransactionNameIT` passed 4/4 against Oracle 10.2.0.4. It proved:

- named DML and COMMIT transaction names;
- the Oracle 10g cross-window behavior (COMMIT retains `TX_NAME`, unlike 11g);
- 5KB CLOB savepoint rollback and rollback-marker handling;
- production `getRedoThreadState()` reading all four last-redo fields;
- a Chinese `VARCHAR2` value remained intact in LogMiner `SQL_REDO` through
  the ZHS16GBK database and JDBC decoding path.

After the run, independent queries reported `test_table_remaining=0` and
`logminer_logs_remaining=0`. This is direct non-UTF-8 row-path evidence, but it
does not yet cover the separate RAW/HEXTORAW scenario or full Embedded Engine
emission into RPS.

## Oracle RAC live evidence — 2026-09-15

The supplied environment is one Oracle 11.2.0.4 RAC database with two OPEN
instances. The supplied `rac1` and `rac2` values are SIDs rather than listener
service names; service-style URLs reproduced `ORA-12514`, while SID-style URLs
connected successfully. `GV$INSTANCE` and `GV$THREAD` confirmed:

- instance `rac1` uses public/open redo thread 1;
- instance `rac2` uses public/open redo thread 2;
- `cluster_database=TRUE`, database role `PRIMARY`, and open mode `READ WRITE`;
- `ARCHIVELOG`, `FORCE_LOGGING=YES`, and all-column supplemental logging;
- database character set `ZHS16GBK` and national character set `AL16UTF16`.

The external LogMiner test was extended with separate mining and writer JDBC
URLs and an expected writer-thread assertion. RED evidence used node 1 for
both connections while requiring thread 2 and failed with `expected: 2 but
was: 1`. GREEN evidence used node 1 for mining and node 2 for writing; the
same test recovered the named transaction with thread 2, the expected
`TX_NAME`, and intact Chinese `SQL_REDO`. The reverse direction, node 2 mining
and node 1 writing, recovered thread 1 with the same metadata guarantees.

A read-only `LogFileCollector` case confirmed that the current log selection
contains both redo threads and has no duplicate `(thread, sequence)` identity.
The upstream IT that forces multiple `ALTER SYSTEM SWITCH LOGFILE` operations
was intentionally not run on this shared environment.

The first full five-case RAC run exposed transient remote-COMMIT visibility in
the hand-written two-window test: the first attempt did not yet contain the
COMMIT, while an unchanged rerun succeeded. Production 3.6 continuously opens
new windows and caps the upper bound using the minimum `LAST_REDO_SCN` of all
open threads. The test now mirrors repeated window execution with a bounded
15-second condition wait; a permanently missing COMMIT still fails. The final
node-1-mining/node-2-writing suite passed 5/5, including named DML, 5KB CLOB
savepoint rollback, restarted windows, redo-thread state, and log-file
deduplication.

Independent cleanup verification reported `test_table_remaining=0`,
`logminer_logs_remaining=0`, and two open/public redo threads. No log switch,
instance stop, failover, listener change, or server configuration mutation was
performed. RAC node-failure recovery remains a separate failure-injection
gate.
