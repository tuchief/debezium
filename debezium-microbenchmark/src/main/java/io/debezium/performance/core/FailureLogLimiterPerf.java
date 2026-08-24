/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.performance.core;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import io.debezium.util.FailureLogLimiter;

@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class FailureLogLimiterPerf {

    private static final int OPERATIONS = 1_000;
    private static final String SIGNATURE = "VALUE_CONVERSION|catalog.schema.table|CREATE|c1|DataException";

    private final FailureLogLimiter limiter = new FailureLogLimiter(
            1024, 20, TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(10), () -> 0);

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public void suppressRepeatedFailure(Blackhole blackhole) {
        for (int i = 0; i < OPERATIONS; i++) {
            blackhole.consume(limiter.acquire(SIGNATURE));
        }
    }
}
