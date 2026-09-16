# Schema History snapshot buffering rebase

Date: 2026-09-14

Target baseline: Debezium `v3.6.2.Final` at
`02810e25b19c04e5095b2b6fbbdcbae549a69f19`.

## Required behavior

When snapshot schema persistence and the final buffered-write drain both fail,
the snapshot failure remains the primary exception and the drain failure is
retained as a suppressed exception. When only the drain fails, that failure is
propagated normally.

## RED evidence

The ported `SchemaHistoryBufferingTest` failed during test compilation on clean
3.6.2 with four errors: `SchemaHistory.BufferingScope` and `buffering()` did not
exist at both try-with-resources call sites.

## Minimal implementation

- Add a default `SchemaHistory.buffering()` method returning an
  `AutoCloseable` `BufferingScope`.
- Use that scope in the generic relational snapshot schema path.
- Use that scope in the binlog snapshot schema path.

No Kafka producer implementation, connector configuration, offset, or schema
history record format was changed.

## GREEN and regression evidence

- Focused exception-semantics test: 2 tests, 0 failures, 0 errors, 0 skipped in
  each selected Surefire execution.
- `debezium-connector-common`: 410 tests, 0 failures, 0 errors, 0 skipped.
- `debezium-connector-binlog`: 76 tests, 0 failures, 0 errors, 0 skipped.
- MariaDB 11.8.9 Failsafe: 5 tests, 0 failures, 0 errors, 0 skipped after the
  production change.

The first binlog regression attempt was excluded from evidence because it ran
in parallel with connector-common and resolved the previously installed common
artifact. After installing the current connector-common artifact, the serial
binlog rerun passed.
