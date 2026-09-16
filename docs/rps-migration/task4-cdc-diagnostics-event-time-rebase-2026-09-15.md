# Task 4 CDC diagnostics and binlog event-time rebase

Date: 2026-09-15

Status: implementation and non-Docker module qualification complete; changes
remain uncommitted. No publication, RPS dependency change, deployment, or live
failure injection was performed.

## Scope and decision

The historical bounded-diagnostics commits and the two last-binlog-event-time
commits were compared with Debezium `v3.6.2.Final`. Clean 3.6 did not contain
the required behavior:

- recoverable conversion and unknown-table warnings were not rate limited;
- the `WARN` value-conversion branch fell through to `SKIP` logging;
- repeated producer failures printed repeated terminal stack traces;
- DDL lifecycle status had no dedicated bounded/redacting logger;
- offsets did not persist the last non-heartbeat binlog event timestamp;
- restart loading did not reconcile legacy seconds with the new milliseconds
  field or prevent timestamp regression.

Both inventory records are therefore `REBASE_IMPLEMENTED_UNCOMMITTED`, not
`UPSTREAM` and not direct cherry-picks. The old `debezium-core` locations were
mapped to `debezium-connector-common` in 3.6.

## Implemented behavior

### Bounded diagnostics

- `FailureLogLimiter` uses a 60-second per-signature window, global 20-message
  cap, at most 1024 tracked signatures, a shared overflow bucket, ten-minute
  lazy expiry, monotonic time, saturated counters, and exception containment.
- `Loggings` skips limiter/message work when the target level is disabled,
  appends suppression counts without displacing a trailing throwable, and
  contains logger runtime failures.
- `TableSchemaBuilder` emits one bounded WARN per conversion signature and no
  longer falls through to the SKIP debug path. FAIL retains the actual caught
  exception as its cause.
- `ErrorHandler` records, queues, and logs only the first producer failure.
- binlog unknown-table and schema/row-size failures use a dedicated logger,
  reliable last-processed/reader positions, and never log row payloads.
- incremental DDL status uses a dedicated logger with single-line 4096-character
  previews, credential/account-DDL redaction, bounded exception messages, and
  logging-failure containment.
- invalid JSON WARN events are rate limited by column signature; SKIP remains
  non-WARN and raw binary JSON is not emitted at WARN/ERROR.

### Last binlog event timestamp

- the primary event listener records every non-heartbeat, non-zero event header
  timestamp before routing the event;
- offsets store `last_binlog_event_ts_ms` independently of the legacy
  `ts_sec` source timestamp;
- updates are monotonic and cannot move the value backward;
- MySQL and MariaDB loaders read both fields and keep the newer instant;
- legacy seconds are converted with `Math.multiplyExact`, producing a clear
  error when the value cannot be represented in milliseconds.

## TDD evidence

RED evidence:

- core tests initially failed compilation because `FailureLogLimiter` and the
  safe/rate-limited logging APIs did not exist;
- the TableSchema test emitted two WARN entries for the same conversion;
- binlog tests initially failed compilation because the two dedicated loggers
  did not exist;
- event-time tests initially failed compilation because the offset key,
  recording method, listener wrapper, and loader behavior did not exist.

GREEN evidence:

- focused connector-common diagnostics: 19 tests, 0 failures/errors/skips;
- focused TableSchema conversion behavior: 1 test, 0 failures/errors/skips;
- focused binlog DML/DDL loggers: 10 tests, 0 failures/errors/skips;
- focused inherited MySQL schema/value tests: 2 tests, 0 failures/errors/skips;
- focused inherited MariaDB schema/value tests: 2 tests, 0 failures/errors/skips;
- MySQL event-time/restart tests: 5 tests, 0 failures/errors/skips;
- MariaDB event-time/restart tests: 5 tests, 0 failures/errors/skips.

Fresh full non-Docker module results:

- connector-common: 421 tests, 0 failures, 0 errors, 0 skips;
- binlog: 90 tests, 0 failures, 0 errors, 0 skips;
- MySQL: 397 tests, 0 failures, 0 errors, 4 skips, plus 2 architecture tests;
- MariaDB: 323 tests, 0 failures, 0 errors, 0 skips, plus 2 architecture tests.

## Remaining gates

- Run controlled live conversion, unknown-table, and malformed-DDL injection
  without exposing business row values.
- Compare successful-path throughput/allocation and failure-storm suppression
  under a representative RPS workload.
- Repeat `last_binlog_event_ts_ms` stop/restart observation for MySQL and the
  packaged RPS runtime; MariaDB primary-to-replica continuation passed.
- Validate log routing/levels in the packaged RPS runtime.
