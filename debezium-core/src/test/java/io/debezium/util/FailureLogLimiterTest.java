/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.util;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class FailureLogLimiterTest {

    private static final long WINDOW = SECONDS.toNanos(60);
    private static final long EXPIRY = MINUTES.toNanos(10);

    @Test
    public void shouldReportSuppressedFailuresAtNextWindow() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1024, 20, WINDOW, EXPIRY, time::get);
        final String signature = "VALUE_CONVERSION|db.t|CREATE|c1|DataException";

        assertThat(limiter.acquire(signature).shouldLog()).isTrue();
        assertThat(limiter.acquire(signature).shouldLog()).isFalse();
        assertThat(limiter.acquire(signature).shouldLog()).isFalse();
        time.set(WINDOW);

        final FailureLogLimiter.Decision decision = limiter.acquire(signature);
        assertThat(decision.shouldLog()).isTrue();
        assertThat(decision.suppressedCount()).isEqualTo(2);
        assertThat(decision.overflow()).isFalse();
    }

    @Test
    public void shouldEnforceGlobalLimitAndCarrySuppressionIntoNextWindow() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1024, 2, WINDOW, EXPIRY, time::get);

        assertThat(limiter.acquire("a").shouldLog()).isTrue();
        assertThat(limiter.acquire("b").shouldLog()).isTrue();
        assertThat(limiter.acquire("c").shouldLog()).isFalse();
        time.set(WINDOW);

        final FailureLogLimiter.Decision decision = limiter.acquire("c");
        assertThat(decision.shouldLog()).isTrue();
        assertThat(decision.suppressedCount()).isEqualTo(1);
    }

    @Test
    public void shouldBoundTrackedSignaturesAndUseOverflowBucket() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(2, 20, WINDOW, EXPIRY, time::get);

        assertThat(limiter.acquire("a").overflow()).isFalse();
        assertThat(limiter.acquire("b").overflow()).isFalse();
        assertThat(limiter.acquire("c").overflow()).isTrue();
        assertThat(limiter.acquire("d").overflow()).isTrue();
        assertThat(limiter.trackedSignatureCount()).isEqualTo(2);
    }

    @Test
    public void shouldEvictExpiredSignatureWhenAdmittingNewSignature() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1, 20, WINDOW, EXPIRY, time::get);

        assertThat(limiter.acquire("old").overflow()).isFalse();
        time.set(EXPIRY);

        assertThat(limiter.acquire("new").overflow()).isFalse();
        assertThat(limiter.trackedSignatureCount()).isEqualTo(1);
    }

    @Test
    public void shouldContainClockFailure() {
        final FailureLogLimiter limiter = new FailureLogLimiter(1, 1, WINDOW, EXPIRY, () -> {
            throw new IllegalStateException("clock failed");
        });

        final FailureLogLimiter.Decision decision = limiter.acquire("a");

        assertThat(decision.shouldLog()).isFalse();
        assertThat(decision.suppressedCount()).isZero();
    }

    @Test
    public void shouldRemainBoundedUnderConcurrentUniqueFailures() throws Exception {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(128, 20, WINDOW, EXPIRY, time::get);
        final ExecutorService executor = Executors.newFixedThreadPool(16);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        try {
            for (int thread = 0; thread < 16; thread++) {
                final int threadId = thread;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 256; i++) {
                        limiter.acquire(threadId + "-" + i);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(limiter.trackedSignatureCount()).isLessThanOrEqualTo(128);
    }

    @Test
    public void shouldEmitOnlyOnceForConcurrentIdenticalFailures() throws Exception {
        final FailureLogLimiter limiter = new FailureLogLimiter(128, 20, WINDOW, EXPIRY, () -> 0);
        final ExecutorService executor = Executors.newFixedThreadPool(16);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger emitted = new AtomicInteger();
        final List<Future<?>> futures = new ArrayList<>();
        try {
            for (int thread = 0; thread < 16; thread++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    if (limiter.acquire("same").shouldLog()) {
                        emitted.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(emitted).hasValue(1);
        assertThat(limiter.trackedSignatureCount()).isEqualTo(1);
    }
}
