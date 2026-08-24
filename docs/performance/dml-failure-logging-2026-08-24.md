# DML Failure Logging Performance Evidence

## Environment

- Feature commit under test: `63f39cce1ae7830f789230eead6301721adb1be9`
- Baseline code: `cd1eeefe3c`
- Baseline benchmark-only commit: `9e826107f6`
- JDK: Azul Zulu OpenJDK 21.0.12+8-LTS
- Host: Apple M1 Pro, 32 GiB RAM
- OS: Darwin 25.5.0 arm64
- JMH: 1.21

The baseline worktree contained the same `DmlRowConversionPerf` source cherry-picked onto the code immediately before the DML failure-logging implementation.

## Successful Row Conversion

Stable throughput command, run on both current and baseline builds:

```bash
java -jar debezium-microbenchmark/target/debezium-microbenchmark.jar \
  io.debezium.performance.core.DmlRowConversionPerf \
  -wi 5 -i 8 -f 3 -w 1s -r 1s -rf json
```

| Columns | Baseline ops/s | Current ops/s | Current vs baseline | Baseline 99.9% CI | Current 99.9% CI |
|---:|---:|---:|---:|---:|---:|
| 8 | 6,915,374 | 6,960,090 | +0.647% | 6,622,145–7,208,604 | 6,684,337–7,235,843 |
| 64 | 893,743 | 928,004 | +3.833% | 829,133–958,352 | 898,693–957,314 |

The feature build is not slower in either tested row width. Confidence intervals overlap, so the data does not support a regression claim.

Allocation command, run on both builds:

```bash
java -jar debezium-microbenchmark/target/debezium-microbenchmark.jar \
  io.debezium.performance.core.DmlRowConversionPerf \
  -wi 3 -i 5 -f 2 -w 1s -r 1s -prof gc -rf json
```

| Columns | Baseline B/op | Current B/op | Difference |
|---:|---:|---:|---:|
| 8 | 520.028711 | 520.034151 | +0.005441 |
| 64 | 3880.217274 | 3880.237534 | +0.020261 |

The differences are below one byte per row and fall inside the profiler error/rounding range. No limiter lookup or failure-signature construction exists on the successful conversion path; the measured row allocation profile is unchanged for operational purposes.

Raw JMH results for this run were written to:

- `/tmp/dml-row-current-stable.json`
- `/tmp/dml-row-baseline-stable.json`
- `/tmp/dml-row-current.json`
- `/tmp/dml-row-baseline.json`

## Repeated Failure Limiter

Command:

```bash
java -jar debezium-microbenchmark/target/debezium-microbenchmark.jar \
  io.debezium.performance.core.FailureLogLimiterPerf \
  -wi 3 -i 5 -f 2 -w 1s -r 1s -prof gc -rf json
```

Result:

- Throughput: `95,174,100 ± 2,157,477 decisions/s`
- Normalized allocation: approximately `0.000004 B/op`
- GC count: approximately zero

The benchmark uses one stable signature and fixed monotonic time, representing a sustained repeated failure after the first warning has been emitted. Unit tests separately prove the per-signature window, exact suppression count, global cap, 1024-signature bound, expiry, and concurrent behavior.

Raw result: `/tmp/failure-log-limiter-current.json`.

## Functional Verification

Direct JUnitCore results on the feature build:

| Suite group | Tests |
|---|---:|
| Core limiter, logging, schema conversion, ErrorHandler | 38 |
| Binlog DML/DDL logging | 5 |
| MariaDB value conversion and database schema | 28 |
| MySQL value conversion and database schema | 28 |
| Total | 99 |

The affected core, binlog, MySQL, MariaDB, and microbenchmark reactor package completed with `BUILD SUCCESS`.

## Conclusion

The implementation meets the performance acceptance criteria for the tested paths:

- No successful-path throughput regression was observed.
- No meaningful per-row allocation increase was observed.
- Repeated failures are suppressed at high throughput with effectively zero allocation.
- Functional tests prove bounded state, exact suppression summaries, logging exception containment, and unchanged failure semantics.
