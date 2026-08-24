# Rate-Limited DML Failure Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded, failure-only DML diagnostics to Debezium core and binlog connectors without changing successful row processing, connector error semantics, offsets, or emitted records.

**Architecture:** A logging-independent `FailureLogLimiter` in `debezium-core` makes per-signature and global emission decisions with bounded state. Existing core logging helpers consume those decisions safely, while `TableSchemaBuilder`, `BinlogValueConverters`, `BinlogStreamingChangeEventSource`, and `ErrorHandler` invoke the helpers only from existing failure branches. Fatal failures bypass throttling and propagate the original exception.

**Tech Stack:** Java 21, JUnit 4, AssertJ, Mockito, SLF4J/Logback test interceptors, Maven reactor, JMH, JFR.

**Spec:** `docs/superpowers/specs/2026-08-24-dml-failure-rate-limited-logging-design.md`

## Global Constraints

- Successful DML emits no INFO/WARN/ERROR and performs no limiter lookup, key allocation, atomic increment, or log formatting.
- Recoverable identical failures emit immediately, then at most once per 60 seconds with an exact suppressed count.
- Each limiter holds at most 1024 signatures and emits at most 20 recoverable warnings per minute.
- Row values, primary keys, and full event payloads never appear at INFO/WARN/ERROR.
- Limiter, key, formatter, or logging runtime failures never change `FAIL`, `WARN`, or `SKIP` processing.
- Fatal paths retain the original exception instance/cause and connector stop/restart behavior.
- Use explicit JDK 21: `/Users/tuchief/Library/Java/JavaVirtualMachines/azul-21.0.12/Contents/Home`.
- Maven `BUILD SUCCESS` is not test evidence; run the named JUnit classes directly with `JUnitCore`.
- Preserve all unrelated staged, modified, and untracked workspace files.

---

### Task 1: Bounded Failure Decision Engine

**Files:**
- Create: `debezium-core/src/main/java/io/debezium/util/FailureLogLimiter.java`
- Create: `debezium-core/src/test/java/io/debezium/util/FailureLogLimiterTest.java`

**Interfaces:**
- Produces: `FailureLogLimiter(int maxSignatures, int globalLimit, long windowNanos, long expiryNanos, LongSupplier nanoTime)`.
- Produces: `FailureLogLimiter.Decision acquire(String signature)`.
- Produces: `Decision.shouldLog()`, `Decision.suppressedCount()`, and `Decision.overflow()`.
- Default constructor uses 1024 signatures, 20 emissions, 60-second windows, 10-minute expiry, and `System::nanoTime`.

- [ ] **Step 1: Write failing limiter behavior tests**

Add literal tests for first emission, same-window suppression, exact next-window count, independent signatures, 20-per-minute global cap, 1024-key bound, overflow bucket, expiry, and a clock that advances through an `AtomicLong`.

```java
@Test
public void shouldReportSuppressedFailuresAtNextWindow() {
    AtomicLong time = new AtomicLong();
    long window = TimeUnit.SECONDS.toNanos(60);
    FailureLogLimiter limiter = new FailureLogLimiter(1024, 20, window,
            TimeUnit.MINUTES.toNanos(10), time::get);

    assertThat(limiter.acquire("VALUE_CONVERSION|db.t|CREATE|c1|DataException").shouldLog()).isTrue();
    assertThat(limiter.acquire("VALUE_CONVERSION|db.t|CREATE|c1|DataException").shouldLog()).isFalse();
    assertThat(limiter.acquire("VALUE_CONVERSION|db.t|CREATE|c1|DataException").shouldLog()).isFalse();
    time.set(window);

    FailureLogLimiter.Decision decision = limiter.acquire("VALUE_CONVERSION|db.t|CREATE|c1|DataException");
    assertThat(decision.shouldLog()).isTrue();
    assertThat(decision.suppressedCount()).isEqualTo(2);
}
```

- [ ] **Step 2: Run the test and verify RED**

Run JDK 21 `test-compile`, then direct `JUnitCore io.debezium.util.FailureLogLimiterTest`.

Expected: compilation or assertion failure because `FailureLogLimiter` does not exist.

- [ ] **Step 3: Implement the bounded limiter**

Use a `ConcurrentHashMap<String, State>` capped before insertion, an overflow state, per-state synchronized window rotation, a global window counter, monotonic time, saturating suppressed counters, and lazy expiry only when admitting a new signature. Do not import SLF4J.

- [ ] **Step 4: Add concurrency and internal-failure tests**

Run 16 threads against one signature and against 2048 unique signatures. Assert emission bounds, exact map bound, no thrown exception, and no counter below zero. Add a test whose `LongSupplier` throws and verify `acquire()` returns a non-emitting decision instead of propagating.

- [ ] **Step 5: Run GREEN and core regression tests**

Run direct `JUnitCore io.debezium.util.FailureLogLimiterTest`. Expected: all tests pass.

- [ ] **Step 6: Commit Task 1**

```bash
git add debezium-core/src/main/java/io/debezium/util/FailureLogLimiter.java \
        debezium-core/src/test/java/io/debezium/util/FailureLogLimiterTest.java
git commit -m "feat(core): add bounded failure log limiter"
```

### Task 2: Safe Rate-Limited Logging Adapter

**Files:**
- Modify: `debezium-core/src/main/java/io/debezium/util/Loggings.java`
- Modify: `debezium-core/src/test/java/io/debezium/util/LoggingsTest.java`

**Interfaces:**
- Consumes: `FailureLogLimiter.acquire(String)` from Task 1.
- Produces: `Loggings.logRateLimitedWarningAndTraceRecord(Logger, FailureLogLimiter, String signature, Object record, String message, Object... arguments)`.
- Produces: `Loggings.logErrorNoThrow(Logger, String message, Object... arguments)`.

- [ ] **Step 1: Write failing safe-logging tests**

Test immediate WARN, suppressed duplicate, summary containing `suppressedCount=2`, TRACE emitted only on a permitted warning, disabled WARN avoiding limiter/message work, and logger methods throwing `RuntimeException` without propagation.

```java
@Test
public void shouldNotPropagateAppenderFailure() {
    Logger logger = mock(Logger.class);
    when(logger.isWarnEnabled()).thenReturn(true);
    doThrow(new IllegalStateException("broken appender")).when(logger).warn(anyString(), any(Object[].class));

    assertThatCode(() -> Loggings.logRateLimitedWarningAndTraceRecord(
            logger, limiter, "VALUE_CONVERSION|db.t|CREATE|c1|DataException",
            new Object[] { "secret" }, "Failed conversion for '{}.{}'", "db.t", "c1"))
            .doesNotThrowAnyException();
}
```

- [ ] **Step 2: Run and verify RED**

Run `JUnitCore io.debezium.util.LoggingsTest`. Expected: missing methods or failed assertions.

- [ ] **Step 3: Implement minimal safe adapters**

Check log level before acquiring or formatting. Call the limiter before any `record.toString()`. Append only the numeric suppression count. Wrap limiter, formatting, and logger calls in a `RuntimeException` boundary that returns silently. Do not catch `Error`.

- [ ] **Step 4: Run GREEN**

Run `FailureLogLimiterTest` and `LoggingsTest`. Expected: all tests pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add debezium-core/src/main/java/io/debezium/util/Loggings.java \
        debezium-core/src/test/java/io/debezium/util/LoggingsTest.java
git commit -m "feat(core): add safe rate-limited failure logging"
```

### Task 3: Row Conversion WARN/SKIP/FAIL Semantics

**Files:**
- Modify: `debezium-core/src/main/java/io/debezium/relational/TableSchemaBuilder.java:68-160,315-355`
- Modify: `debezium-core/src/test/java/io/debezium/relational/TableSchemaBuilderTest.java:700-790`

**Interfaces:**
- Consumes: Task 2 logging adapter.
- Produces: one limiter owned by each `TableSchemaBuilder` instance.
- Preserves all existing public constructors and adds a package-visible test constructor accepting a limiter.

- [ ] **Step 1: Write failing conversion-mode tests**

Drive a real `TableSchema` converter with a converter that throws. Assert:

- `WARN` processes repeated rows without throwing and emits one WARN.
- `WARN` no longer falls through into the `SKIP` DEBUG/TRACE branch.
- `SKIP` emits no WARN and keeps its existing result.
- `FAIL` propagates a `DebeziumException` with the original cause.
- Successful conversion emits no log and never calls a fail-on-use limiter.
- WARN/ERROR entries do not contain the row's secret literal.

- [ ] **Step 2: Run and verify RED**

Run direct `JUnitCore io.debezium.relational.TableSchemaBuilderTest`. Expected: duplicate WARN/SKIP logging or missing rate limiting.

- [ ] **Step 3: Implement the failure-only integration**

Create the signature only inside `catch (Exception e)`. Use category `VALUE_CONVERSION` with table, column, and exception class. Add an explicit `break` or `return` after `WARN` so it cannot fall through. Keep successful-loop statements byte-for-byte unchanged except for the builder's limiter field.

- [ ] **Step 4: Run GREEN and mutation checks**

Run `TableSchemaBuilderTest`, then temporarily remove the WARN termination and verify the new test fails; restore and rerun GREEN.

- [ ] **Step 5: Commit Task 3**

```bash
git add debezium-core/src/main/java/io/debezium/relational/TableSchemaBuilder.java \
        debezium-core/src/test/java/io/debezium/relational/TableSchemaBuilderTest.java
git commit -m "fix(core): rate limit row conversion warnings"
```

### Task 4: Terminal Producer Failure Deduplication

**Files:**
- Modify: `debezium-core/src/main/java/io/debezium/pipeline/ErrorHandler.java:51-69`
- Modify: `debezium-core/src/test/java/io/debezium/pipeline/ErrorHandlerTest.java`

**Interfaces:**
- Consumes: existing atomic `producerThrowable` state.
- Produces: exactly one terminal `Producer failure` ERROR for repeated calls.

- [ ] **Step 1: Write a failing first-failure-only test**

Call `setProducerThrowable()` three times and use `LogInterceptor` to assert one `Producer failure` event while retaining the first exception in the queue and atomic reference.

- [ ] **Step 2: Run and verify RED**

Run `JUnitCore io.debezium.pipeline.ErrorHandlerTest`. Expected: three ERROR events.

- [ ] **Step 3: Move terminal logging behind compare-and-set**

Compute `first` before logging. Only the winning first call logs and updates the queue. Do not change retry classification or exception wrapping.

- [ ] **Step 4: Run GREEN**

Run `ErrorHandlerTest`. Expected: all tests pass and first throwable identity is preserved.

- [ ] **Step 5: Commit Task 4**

```bash
git add debezium-core/src/main/java/io/debezium/pipeline/ErrorHandler.java \
        debezium-core/src/test/java/io/debezium/pipeline/ErrorHandlerTest.java
git commit -m "fix(core): log producer failure only once"
```

### Task 5: Binlog Row Metadata Failure Logging

**Files:**
- Modify: `debezium-connector-binlog/src/main/java/io/debezium/connector/binlog/BinlogStreamingChangeEventSource.java:1050-1195`
- Create: `debezium-connector-binlog/src/test/java/io/debezium/connector/binlog/BinlogDmlFailureLoggingTest.java`

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: structured categories `UNKNOWN_TABLE` and `SCHEMA_ROW_SIZE_MISMATCH`.
- Owns one limiter instance for recoverable streaming warnings.

- [ ] **Step 1: Write failing row-event tests**

Use a test subclass/harness to feed repeated unknown-table events in WARN mode and a schema/row-size mismatch. Assert one WARN per signature, suppression summary after clock advance, one fatal ERROR, unchanged `DebeziumException`, and no row contents in logs.

- [ ] **Step 2: Run and verify RED**

Run direct `JUnitCore io.debezium.connector.binlog.BinlogDmlFailureLoggingTest`. Expected: repeated unknown-table WARN messages and duplicate mismatch ERROR output.

- [ ] **Step 3: Integrate failure logging without touching the success loop**

Rate-limit only the existing WARN branch in `informAboutUnknownTableIfRequired()`. For mismatch, build one structured fatal message and then throw the same exception; remove the duplicate unstructured local ERROR. Include only table, operation, sizes, and binlog metadata.

- [ ] **Step 4: Run GREEN and row-event regressions**

Run `BinlogDmlFailureLoggingTest`, `io.debezium.connector.mysql.MySqlDatabaseSchemaTest`, and `io.debezium.connector.mariadb.DatabaseSchemaTest`. Verify existing schema results and offsets remain unchanged.

- [ ] **Step 5: Commit Task 5**

```bash
git add debezium-connector-binlog/src/main/java/io/debezium/connector/binlog/BinlogStreamingChangeEventSource.java \
        debezium-connector-binlog/src/test/java/io/debezium/connector/binlog/BinlogDmlFailureLoggingTest.java
git commit -m "fix(binlog): bound row metadata failure logs"
```

### Task 6: Binlog Value Conversion Warnings

**Files:**
- Modify: `debezium-connector-binlog/src/main/java/io/debezium/connector/binlog/jdbc/BinlogValueConverters.java:400-440`
- Modify: `debezium-connector-binlog/src/test/java/io/debezium/connector/binlog/BinlogValueConvertersTest.java`
- Modify: `debezium-connector-mysql/src/test/java/io/debezium/connector/mysql/MySqlValueConvertersTest.java`
- Modify: `debezium-connector-mariadb/src/test/java/io/debezium/connector/mariadb/ValueConvertersTest.java`

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: category `BINLOG_VALUE_CONVERSION` with column and exception class.
- Owns one limiter per converter instance.

- [ ] **Step 1: Write failing malformed-value tests**

Convert the same malformed JSON/binlog value repeatedly under WARN, SKIP, and FAIL. Assert one WARN under WARN, no WARN under SKIP, original failure under FAIL, no raw bytes in WARN/ERROR, and an exact suppressed count after clock advance.

- [ ] **Step 2: Run and verify RED**

Run concrete MySQL and MariaDB value-converter test classes. Expected: repeated warning output.

- [ ] **Step 3: Implement failure-only limiting**

Acquire only inside the existing conversion catch. Preserve existing optional-column fallback values and exception behavior.

- [ ] **Step 4: Run GREEN**

Run shared and concrete converter tests. Expected: all tests pass.

- [ ] **Step 5: Commit Task 6**

```bash
git add debezium-connector-binlog/src/main/java/io/debezium/connector/binlog/jdbc/BinlogValueConverters.java \
        debezium-connector-binlog/src/test/java/io/debezium/connector/binlog/BinlogValueConvertersTest.java \
        debezium-connector-mysql/src/test/java/io/debezium/connector/mysql/MySqlValueConvertersTest.java \
        debezium-connector-mariadb/src/test/java/io/debezium/connector/mariadb/ValueConvertersTest.java
git commit -m "fix(binlog): rate limit value conversion warnings"
```

### Task 7: Full Regression and Performance Evidence

**Files:**
- Create: `debezium-microbenchmark/src/main/java/io/debezium/performance/core/DmlRowConversionPerf.java`
- Create: `debezium-microbenchmark/src/main/java/io/debezium/performance/core/FailureLogLimiterPerf.java`
- Create: `docs/performance/dml-failure-logging-2026-08-24.md`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: repeatable JMH commands and measured evidence for successful and failure-storm paths.

- [ ] **Step 1: Add the successful-path JMH benchmark**

Benchmark real `TableSchema` row conversion for a narrow table and a wide table. The benchmark must compile both before and after the feature so the same harness can be run on a baseline worktree.

- [ ] **Step 2: Add the failure-storm JMH benchmark**

Call one limiter with one million identical failure signatures and a fixed/controlled clock. Consume decisions with `Blackhole`; expose emitted count, suppressed count, and map size from benchmark teardown assertions.

- [ ] **Step 3: Build all affected modules and run direct suites**

With JDK 21, run reactor `test-compile/package` for core, binlog, MySQL, and MariaDB. Then run direct JUnitCore for:

```text
FailureLogLimiterTest
LoggingsTest
TableSchemaBuilderTest
ErrorHandlerTest
BinlogDmlFailureLoggingTest
MySQL concrete BinlogValueConvertersTest
MariaDB concrete BinlogValueConvertersTest
MySqlDatabaseSchemaTest
MariaDB DatabaseSchemaTest
```

Expected: zero failures. Run `git diff --check`.

- [ ] **Step 4: Run baseline/current JMH comparison**

Run the successful-row benchmark on the feature branch and on a clean baseline worktree at the design commit parent, using identical JDK, warmups, forks, and JVM flags. Record scores, errors, confidence intervals, and `-prof gc` allocation results.

Acceptance: no additional successful-path allocation and no statistically meaningful throughput regression greater than 1%.

- [ ] **Step 5: Run failure-storm and JFR verification**

Run the storm benchmark with `-prof gc`; confirm warning decisions and map size remain bounded. Run a short current-build JFR capture if JMH confidence intervals overlap the 1% boundary or allocation differs.

- [ ] **Step 6: Record evidence**

Write exact commands, commit IDs, JDK version, hardware, raw score table, allocation, limiter counts, and conclusion to `docs/performance/dml-failure-logging-2026-08-24.md`. Do not claim performance acceptance if the baseline/current evidence is inconclusive.

- [ ] **Step 7: Final review and commit**

Verify only intended files are included and no DML payload appears in non-TRACE messages.

```bash
git add debezium-microbenchmark/src/main/java/io/debezium/performance/core/DmlRowConversionPerf.java \
        debezium-microbenchmark/src/main/java/io/debezium/performance/core/FailureLogLimiterPerf.java \
        docs/performance/dml-failure-logging-2026-08-24.md
git commit -m "perf: verify bounded DML failure logging"
```

## Completion Gate

- All RED tests were observed failing for the intended missing behavior before implementation.
- All named direct JUnit suites pass on JDK 21.
- Core/binlog/MySQL/MariaDB reactor package is successful.
- No normal DML path invokes `FailureLogLimiter` or constructs a failure signature.
- Runtime failures from limiter/logging are contained and covered by tests.
- Map/global/per-key limits and suppression counts are proven under concurrency.
- Baseline/current JMH and allocation evidence satisfies the spec or is explicitly reported as inconclusive.
- No unrelated workspace changes are committed.
