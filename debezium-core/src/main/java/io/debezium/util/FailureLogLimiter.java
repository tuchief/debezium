/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.util;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Bounds repeated failure log emissions by signature and across a component.
 *
 * This class has no logging dependency and contains its own runtime failures so it can be used from error paths
 * without changing application behavior.
 */
public final class FailureLogLimiter {

    private static final int DEFAULT_MAX_SIGNATURES = 1024;
    private static final int DEFAULT_GLOBAL_LIMIT = 20;
    private static final long DEFAULT_WINDOW_NANOS = SECONDS.toNanos(60);
    private static final long DEFAULT_EXPIRY_NANOS = MINUTES.toNanos(10);

    private final int maxSignatures;
    private final int globalLimit;
    private final long windowNanos;
    private final long expiryNanos;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final State overflowState = new State();
    private final GlobalWindow globalWindow = new GlobalWindow();
    private final Object admissionLock = new Object();

    public FailureLogLimiter() {
        this(DEFAULT_MAX_SIGNATURES, DEFAULT_GLOBAL_LIMIT, DEFAULT_WINDOW_NANOS, DEFAULT_EXPIRY_NANOS, System::nanoTime);
    }

    FailureLogLimiter(int maxSignatures, int globalLimit, long windowNanos, long expiryNanos, LongSupplier nanoTime) {
        this.maxSignatures = Math.max(0, maxSignatures);
        this.globalLimit = Math.max(1, globalLimit);
        this.windowNanos = Math.max(1, windowNanos);
        this.expiryNanos = Math.max(this.windowNanos, expiryNanos);
        this.nanoTime = nanoTime;
    }

    /**
     * Determines whether a failure should be logged.
     *
     * @param signature a stable, low-cardinality failure signature
     * @return a non-null decision; runtime failures result in a non-emitting decision
     */
    public Decision acquire(String signature) {
        try {
            final long now = nanoTime.getAsLong();
            final StateHolder holder = stateFor(signature == null ? "<null>" : signature, now);
            final Candidate candidate = holder.state.acquire(now, windowNanos);
            if (!candidate.shouldLog) {
                return Decision.suppressed(holder.overflow);
            }
            if (!globalWindow.tryAcquire(now, windowNanos, globalLimit)) {
                holder.state.suppress();
                return Decision.suppressed(holder.overflow);
            }
            return Decision.emitted(candidate.suppressedCount, holder.overflow);
        }
        catch (RuntimeException ignored) {
            return Decision.suppressed(false);
        }
    }

    int trackedSignatureCount() {
        return states.size();
    }

    private StateHolder stateFor(String signature, long now) {
        State state = states.get(signature);
        if (state != null) {
            return new StateHolder(state, false);
        }

        synchronized (admissionLock) {
            state = states.get(signature);
            if (state != null) {
                return new StateHolder(state, false);
            }
            removeExpired(now);
            if (states.size() >= maxSignatures) {
                return new StateHolder(overflowState, true);
            }
            state = new State();
            states.put(signature, state);
            return new StateHolder(state, false);
        }
    }

    private void removeExpired(long now) {
        for (Map.Entry<String, State> entry : states.entrySet()) {
            final State state = entry.getValue();
            if (elapsed(now, state.lastSeen()) >= expiryNanos) {
                states.remove(entry.getKey(), state);
            }
        }
    }

    private static long elapsed(long now, long start) {
        if (start == Long.MIN_VALUE || now < start) {
            return 0;
        }
        return now - start;
    }

    public static final class Decision {
        private static final Decision SUPPRESSED = new Decision(false, 0, false);
        private static final Decision OVERFLOW_SUPPRESSED = new Decision(false, 0, true);

        private final boolean shouldLog;
        private final long suppressedCount;
        private final boolean overflow;

        private Decision(boolean shouldLog, long suppressedCount, boolean overflow) {
            this.shouldLog = shouldLog;
            this.suppressedCount = suppressedCount;
            this.overflow = overflow;
        }

        private static Decision emitted(long suppressedCount, boolean overflow) {
            return new Decision(true, suppressedCount, overflow);
        }

        private static Decision suppressed(boolean overflow) {
            return overflow ? OVERFLOW_SUPPRESSED : SUPPRESSED;
        }

        public boolean shouldLog() {
            return shouldLog;
        }

        public long suppressedCount() {
            return suppressedCount;
        }

        public boolean overflow() {
            return overflow;
        }
    }

    private static final class StateHolder {
        private final State state;
        private final boolean overflow;

        private StateHolder(State state, boolean overflow) {
            this.state = state;
            this.overflow = overflow;
        }
    }

    private static final class Candidate {
        private static final Candidate SUPPRESSED = new Candidate(false, 0);

        private final boolean shouldLog;
        private final long suppressedCount;

        private Candidate(boolean shouldLog, long suppressedCount) {
            this.shouldLog = shouldLog;
            this.suppressedCount = suppressedCount;
        }

        private static Candidate emitted(long suppressedCount) {
            return new Candidate(true, suppressedCount);
        }
    }

    private static final class State {
        private long windowStart = Long.MIN_VALUE;
        private long lastSeen = Long.MIN_VALUE;
        private long suppressedCount;

        private synchronized Candidate acquire(long now, long windowNanos) {
            lastSeen = now;
            if (windowStart == Long.MIN_VALUE || elapsed(now, windowStart) >= windowNanos) {
                final long previousSuppressedCount = suppressedCount;
                windowStart = now;
                suppressedCount = 0;
                return Candidate.emitted(previousSuppressedCount);
            }
            incrementSuppressedCount();
            return Candidate.SUPPRESSED;
        }

        private synchronized void suppress() {
            incrementSuppressedCount();
        }

        private synchronized long lastSeen() {
            return lastSeen;
        }

        private void incrementSuppressedCount() {
            if (suppressedCount != Long.MAX_VALUE) {
                suppressedCount++;
            }
        }
    }

    private static final class GlobalWindow {
        private long windowStart = Long.MIN_VALUE;
        private int emittedCount;

        private synchronized boolean tryAcquire(long now, long windowNanos, int limit) {
            if (windowStart == Long.MIN_VALUE || elapsed(now, windowStart) >= windowNanos) {
                windowStart = now;
                emittedCount = 0;
            }
            if (emittedCount >= limit) {
                return false;
            }
            emittedCount++;
            return true;
        }
    }
}
