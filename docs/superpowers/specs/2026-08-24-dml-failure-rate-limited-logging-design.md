# Rate-Limited DML Failure Logging Design

## Scope

This design adds bounded failure logging for relational DML event processing, starting with the shared Debezium core and binlog connector paths used by MySQL and MariaDB. It does not log successfully processed DML events or row values.

In row-based binlogs, Debezium does not parse DML SQL. The relevant failures are row-event deserialization, unknown table/schema metadata, schema-to-row size mismatch, value conversion, and event dispatch failures.

## Goals

- Emit enough context to diagnose every category of DML processing failure.
- Prevent repeated recoverable failures from producing an unbounded number of log entries.
- Preserve the existing `FAIL`, `WARN`, and `SKIP` behavior exactly.
- Ensure logging and rate-limiter failures cannot introduce a new connector failure.
- Add no work to the successful DML row-processing path.
- Keep limiter memory bounded for long-running connector tasks.

## Non-goals

- Logging successful INSERT, UPDATE, or DELETE events.
- Logging row values, primary-key values, or complete event payloads at INFO, WARN, or ERROR.
- Changing retry, offset, filtering, conversion, or schema-recovery behavior.
- Adding a user-facing configuration option in the first version.
- Applying the policy to snapshot progress or DDL status logging.

## Existing Failure Paths

The implementation must cover these existing paths without changing their semantics:

1. `TableSchemaBuilder` value conversion failures. `WARN` currently logs per row and falls through to the `SKIP` branch, which can produce a second DEBUG/TRACE entry.
2. `BinlogValueConverters` JSON and binlog value conversion failures.
3. `BinlogStreamingChangeEventSource` unknown-table warnings and schema/row column-count mismatches.
4. Fatal binlog event processing propagated through `ErrorHandler.setProducerThrowable()`.

Fatal failures clear binlog event handlers and stop or restart the connector, so they are naturally bounded. Recoverable `WARN` paths require explicit rate limiting.

## Log Policy

### Successful DML

No INFO, WARN, or ERROR log is emitted. The limiter is not called, no key is created, and no message is formatted.

### Recoverable failure

The first occurrence of a failure signature is logged immediately at WARN. Repeated occurrences are suppressed for 60 seconds. The next permitted warning includes the number suppressed in the previous window.

```text
[DML_PROCESSING_FAILED] action=CONTINUE, category=VALUE_CONVERSION,
table='db.t1', operation=CREATE, column='c1', exception='DataException',
suppressedCount=12843
```

### Fatal failure

Fatal failures bypass throttling and emit one structured ERROR before the original exception is propagated. The original exception instance, cause chain, connector action, and offset behavior are unchanged.

```text
[DML_PROCESSING_FAILED] action=STOP, category=SCHEMA_ROW_SIZE_MISMATCH,
table='db.t1', operation=CREATE, internalSchemaSize=2, rowSize=1,
binlog='mariadb-bin.000054', position=914869070
```

The existing generic producer failure remains the framework-level terminal record. Duplicate site-specific ERROR messages are removed when the structured error contains the same information.

### Log contents

Allowed fields are category, action, table identifier, operation, column name and type, exception class, schema size, row size, binlog file/position, and suppressed count.

Row values, keys, complete event objects, and exception messages containing source values are excluded from INFO/WARN/ERROR. If a raw record is retained at TRACE for diagnosis, it is emitted only when the corresponding rate-limit decision permits the warning.

## Rate Limiter

Add a small core utility named `FailureLogLimiter`, with no dependency on a logging implementation.

### Failure signature

```text
category + tableId + operation + column + exceptionClass
```

The signature must not contain a row value, primary key, exception message, offset, or timestamp. Those fields would create high-cardinality keys and defeat throttling.

### Bounds

- Per signature: one immediate warning and at most one warning per 60-second window.
- Global recoverable-warning cap: 20 emitted warnings per connector component per minute.
- Maximum signatures per limiter instance: 1024.
- Signatures inactive for 10 minutes are eligible for lazy eviction.
- When the signature bound is reached, new signatures share one overflow bucket instead of expanding the map.
- Limiter instances are scoped to connector-owned components, not static JVM-global state, so tasks do not suppress one another.

The first version uses fixed internal defaults. Configuration fields can be added later if operational evidence requires tuning.

### Decision API

The limiter returns a decision containing `emit`, `suppressedCount`, and `overflow`. It does not call the logger. Time is supplied by a monotonic clock abstraction; tests use a controllable clock and production uses `System.nanoTime()`.

All limiter and logger calls are inside failure branches. A runtime exception from key construction, limiting, message formatting, or the logging backend is caught and discarded. Fatal processing still rethrows the original processing exception; recoverable processing still follows the configured WARN/SKIP behavior.

## Integration

### Debezium core

- Add `FailureLogLimiter` and focused unit tests.
- Route recoverable conversion warnings in `TableSchemaBuilder` through a per-builder limiter.
- Fix the `WARN` switch branch so it does not fall through to `SKIP`.
- Keep raw-record TRACE output behind the same permit decision.
- In `ErrorHandler`, emit `Producer failure` only after the atomic first-failure check so repeated calls do not duplicate the terminal stack trace.

### Binlog connector

- Add a limiter to `BinlogStreamingChangeEventSource` for unknown-table WARN events.
- Replace schema/row-size mismatch's local unstructured ERROR with one structured fatal record, then throw the same `DebeziumException` as today.
- Route recoverable JSON/binlog conversion warnings through a limiter owned by the converter instance.
- Do not place limiter checks in the normal row loop before conversion or dispatch.

## Concurrency and Lifecycle

Limiter state uses a bounded concurrent map and atomic counters. It must be safe for parallel snapshot/conversion callers even though binlog streaming is normally single-threaded. State is discarded with its owning connector component and does not require a shutdown hook.

Clock rollback cannot extend a window because production timing is monotonic. Counter overflow is handled by saturation or reset at window rotation; it must not throw.

## Testing Strategy

### Core limiter unit tests

- First occurrence is emitted immediately.
- Identical failures within 60 seconds are suppressed.
- The next window reports the exact suppressed count.
- Different signatures are independent until the global cap is reached.
- The global cap produces one bounded overflow summary.
- More than 1024 unique signatures never grows the map beyond its limit.
- Inactive entries are evicted without scanning an unbounded collection.
- Concurrent callers remain within emission and map-size bounds.
- A disabled log level performs no message formatting.
- Logger and limiter runtime exceptions never escape.

### Processing behavior tests

- Successful row conversion never invokes the limiter or logger.
- `WARN` logs once and continues with existing fallback behavior.
- `WARN` does not fall through to `SKIP` logging.
- `SKIP` preserves existing behavior without WARN output.
- `FAIL` emits one structured ERROR and propagates the original failure/cause.
- Unknown-schema `WARN` events are suppressed and summarized by signature.
- Schema/row-size mismatch emits once and still stops the connector.
- Repeated `setProducerThrowable()` calls produce one terminal producer stack trace.
- Log messages contain no row values or primary-key values.

### Connector regression tests

- Run the inherited MySQL and MariaDB database-schema tests.
- Run binlog row-event tests for INSERT, UPDATE, DELETE, compressed rows, and conversion modes.
- Verify connector offsets and emitted records are identical before and after the logging change.

### Performance validation

Successful processing is the primary performance gate. The limiter is reachable only from exception branches, so the normal row loop must contain no limiter lookup, atomic increment, key allocation, or log-message construction.

Use a JMH comparison for successful row conversion and emission with representative narrow and wide tables:

- Throughput regression target: less than 1%, interpreted with confidence intervals rather than a brittle CI assertion.
- Additional successful-path allocation target: 0 bytes per row.
- Capture JFR or allocation-profiler evidence when results are ambiguous.

Use a separate failure-storm benchmark with one million identical recoverable failures and a fixed clock:

- Emitted warnings remain within the configured per-key/global bounds.
- Limiter map size remains bounded.
- Suppressed count is exact.
- No logger call is made for suppressed events.

Performance benchmarks are evidence recorded with the change, not flaky pass/fail unit tests.

## Acceptance Criteria

- No successful DML event produces INFO/WARN/ERROR output.
- Every fatal DML failure produces a diagnostic ERROR and retains the original connector failure behavior.
- Recoverable repeated failures produce bounded warnings with accurate suppression summaries.
- No INFO/WARN/ERROR message contains row data.
- Limiter/logger runtime failures do not escape into CDC processing.
- Limiter state cannot exceed its documented bounds.
- MySQL and MariaDB regression suites pass.
- Successful-path JMH shows no additional allocation and no statistically meaningful regression above 1%.

## Rollout

Publish the core and binlog artifacts under one new immutable Nexus version. Validate first with a controlled connector task using injected conversion failures, then monitor warning rate, suppressed counts, CPU, allocation, and connector offsets before wider deployment. No task restart or deployment is part of the code change without separate authorization.
