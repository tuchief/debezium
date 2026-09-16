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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class FailureLogLimiterTest {

    private static final long WINDOW = SECONDS.toNanos(60);
    private static final long EXPIRY = MINUTES.toNanos(10);

    @Test
    void shouldReportSuppressedFailuresAtNextWindow() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1024, 20, WINDOW, EXPIRY, time::get);

        assertThat(limiter.acquire("conversion").shouldLog()).isTrue();
        assertThat(limiter.acquire("conversion").shouldLog()).isFalse();
        assertThat(limiter.acquire("conversion").shouldLog()).isFalse();
        time.set(WINDOW);

        final FailureLogLimiter.Decision decision = limiter.acquire("conversion");
        assertThat(decision.shouldLog()).isTrue();
        assertThat(decision.suppressedCount()).isEqualTo(2);
        assertThat(decision.overflow()).isFalse();
    }

    @Test
    void shouldEnforceGlobalLimitAndCarrySuppressionIntoNextWindow() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1024, 2, WINDOW, EXPIRY, time::get);

        assertThat(limiter.acquire("a").shouldLog()).isTrue();
        assertThat(limiter.acquire("b").shouldLog()).isTrue();
        assertThat(limiter.acquire("c").shouldLog()).isFalse();
        time.set(WINDOW);

        assertThat(limiter.acquire("c").suppressedCount()).isEqualTo(1);
    }

    @Test
    void shouldBoundTrackedSignaturesAndUseOverflowBucket() {
        final FailureLogLimiter limiter = new FailureLogLimiter(2, 20, WINDOW, EXPIRY, () -> 0);

        assertThat(limiter.acquire("a").overflow()).isFalse();
        assertThat(limiter.acquire("b").overflow()).isFalse();
        assertThat(limiter.acquire("c").overflow()).isTrue();
        assertThat(limiter.acquire("d").overflow()).isTrue();
        assertThat(limiter.trackedSignatureCount()).isEqualTo(2);
    }

    @Test
    void shouldEvictExpiredSignatureWhenAdmittingNewSignature() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1, 20, WINDOW, EXPIRY, time::get);

        assertThat(limiter.acquire("old").overflow()).isFalse();
        time.set(EXPIRY);

        assertThat(limiter.acquire("new").overflow()).isFalse();
        assertThat(limiter.trackedSignatureCount()).isEqualTo(1);
    }

    @Test
    void shouldContainClockFailure() {
        final FailureLogLimiter limiter = new FailureLogLimiter(1, 1, WINDOW, EXPIRY, () -> {
            throw new IllegalStateException("clock failed");
        });

        assertThat(limiter.acquire("a").shouldLog()).isFalse();
    }

    @Test
    void shouldEmitOnlyOnceForConcurrentIdenticalFailures() throws Exception {
        final FailureLogLimiter limiter = new FailureLogLimiter(128, 20, WINDOW, EXPIRY, () -> 0);
        final var executor = Executors.newFixedThreadPool(16);
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
    }

    @Test
    void shouldRemainBoundedUnderConcurrentUniqueFailures() throws Exception {
        final FailureLogLimiter limiter = new FailureLogLimiter(128, 20, WINDOW, EXPIRY, () -> 0);
        final var executor = Executors.newFixedThreadPool(16);
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
}
