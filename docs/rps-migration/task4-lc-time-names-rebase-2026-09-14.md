# `lc_time_names` source metadata rebase

Date: 2026-09-14

Target baseline: Debezium `v3.6.2.Final` at
`02810e25b19c04e5095b2b6fbbdcbae549a69f19`.

## Scope and dependency finding

This phase qualified commits `4d1eaab04c`, `c0b185ed89`, `3bad97a272`, and
`7abd3bbc2c` behavior by behavior. RPS depends on the optional string field
`source.lc_time`: `GoldendbCapturer` reads it to filter events originating from
the target, while `MysqlSqlBuilder` writes the origin identifier through the
session `lc_time_names` variable.

The old implementation also had an undocumented binary prerequisite. The local
`mysql-binlog-connector-java:0.40.2` artifact had been modified to expose
`QueryEventData.statusVars`. Debezium 3.6.2 resolves the upstream
`io.debezium:mysql-binlog-connector-java:0.41.2`, whose `QueryEventData` no
longer exposes that block. Copying the historical connector code therefore
fails at compilation and would silently preserve a dependency on an obsolete
private fork. Task 4 initially solved that mismatch with connector-local Query
Event capture. Task 5 later superseded that temporary design by introducing the
aligned 0.41.2 RPS client fork required for compressed events; ordinary and
compressed Query Events now share that fork's retained status-variable API.

## RED and upstream qualification

- The initial test compilation failed because 3.6 had neither the `lc_time`
  source contract nor an API that retained Query Event status variables.
- MariaDB rejected `GRANT CN_SESSION_VARIABLES_ADMIN ...`; MySQL 3.6 already
  accepted it as a generic privilege identifier.
- Both current MySQL and MariaDB parsers already accepted
  `SET @@session.lc_time_names = 1024`.
- Both parsers already accepted a leading DBeaver-style block comment followed
  by `ALTER TABLE ... DROP INDEX`. The historical comment-removal and
  `DROP INDEX`-to-`DROP KEY` regular expressions are therefore obsolete.
- The historical INFO logging and `System.out` output were not migrated.

## Minimal 3.6 implementation

- Retained the raw status-variable block through the aligned 0.41.2 RPS client
  fork and decoded the two-byte, little-endian `Q_LC_TIME_NAMES_CODE` value in
  Debezium.
- Preserved the RPS wire contract as optional string field `source.lc_time`.
  A Query Event without the value explicitly clears the previous value.
- Kept bounds checks for malformed status blocks and corrected two historical
  protocol assumptions: legacy catalog includes a trailing zero, and updated
  database names are zero-terminated rather than length-prefixed.
- Added the missing MariaDB lexer token and privilege alternative for
  `CN_SESSION_VARIABLES_ADMIN`; no MySQL grammar change was needed.

The status layout follows the MySQL Query Event protocol documented at
https://dev.mysql.com/doc/dev/mysql-server/8.0.46/classbinary__log_1_1Query__event.html.

## Verification

- Final focused status capture/decoding suite: 4 tests, 0 failures, 0 errors,
  0 skipped.
- Binlog non-Docker suite: 80 tests, 0 failures, 0 errors, 0 skipped.
- MySQL parser plus source-info suite: 227 tests, 0 failures, 0 errors,
  3 skipped.
- MariaDB parser plus source-info suite: 220 tests, 0 failures, 0 errors,
  0 skipped.
- ANTLR QA: MySQL, MySQL legacy, MariaDB, and Oracle grammar roots completed
  with `BUILD SUCCESS`.
- DDL parser and binlog artifacts were installed locally as uncommitted
  `3.6.2.Final` builds so downstream module tests used the current worktree.

## Remaining acceptance boundary

No GoldenDB environment was used in this phase. Site acceptance must still
prove that GoldenDB writes the RPS numeric origin identifier as
`Q_LC_TIME_NAMES_CODE`, that ordinary Query Events carry it through to
`source.lc_time`, and that a subsequent event without the variable clears it.
Compressed Query Events were subsequently implemented and verified in
`RPS-BINLOG-COMPRESSED`; GoldenDB numeric-origin replay remains the site gate.
