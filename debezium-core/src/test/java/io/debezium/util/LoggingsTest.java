/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.util;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.junit.logging.LogInterceptor;

import ch.qos.logback.classic.Level;

public class LoggingsTest {

    private static final long WINDOW = SECONDS.toNanos(60);
    private static final long EXPIRY = MINUTES.toNanos(10);

    @Test
    public void shouldRateLimitWarningAndReportSuppressedCountWithoutRecordValue() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1024, 20, WINDOW, EXPIRY, time::get);
        final String loggerName = "io.debezium.util.LoggingsTest.rateLimited";
        final Logger logger = LoggerFactory.getLogger(loggerName);
        final LogInterceptor interceptor = new LogInterceptor(loggerName);
        interceptor.setLoggerLevel(LoggingsTest.class, Level.WARN);

        Loggings.logRateLimitedWarningAndTraceRecord(logger, limiter, "conversion", "secret-row-value",
                "Failed conversion for '{}.{}'", "db.t", "c1");
        Loggings.logRateLimitedWarningAndTraceRecord(logger, limiter, "conversion", "secret-row-value",
                "Failed conversion for '{}.{}'", "db.t", "c1");
        time.set(WINDOW);
        Loggings.logRateLimitedWarningAndTraceRecord(logger, limiter, "conversion", "secret-row-value",
                "Failed conversion for '{}.{}'", "db.t", "c1");

        final List<String> messages = interceptor.getLogEntriesThatContainsMessage("Failed conversion");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1)).contains("suppressedCount=1");
        assertThat(messages).allMatch(message -> !message.contains("secret-row-value"));
    }

    @Test
    public void shouldAvoidLimiterAndFormattingWhenWarningIsDisabled() {
        final AtomicInteger clockCalls = new AtomicInteger();
        final FailureLogLimiter limiter = new FailureLogLimiter(1, 1, WINDOW, EXPIRY, () -> {
            clockCalls.incrementAndGet();
            return 0;
        });
        final Logger logger = mock(Logger.class);
        when(logger.isWarnEnabled()).thenReturn(false);

        Loggings.logRateLimitedWarningAndTraceRecord(logger, limiter, "conversion", "secret-row-value",
                "Failed conversion for '{}.{}'", "db.t", "c1");

        assertThat(clockCalls).hasValue(0);
    }

    @Test
    public void shouldNotPropagateWarningOrErrorLoggerFailure() {
        final FailureLogLimiter limiter = new FailureLogLimiter(1, 1, WINDOW, EXPIRY, () -> 0);
        final Logger logger = mock(Logger.class);
        when(logger.isWarnEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("broken warning appender")).when(logger).warn(anyString(), any(Object[].class));
        doThrow(new IllegalStateException("broken error appender")).when(logger).error(anyString(), any(Object[].class));

        assertThatCode(() -> {
            Loggings.logRateLimitedWarningAndTraceRecord(logger, limiter, "conversion", "secret-row-value",
                    "Failed conversion for '{}.{}'", "db.t", "c1");
            Loggings.logErrorNoThrow(logger, "Fatal conversion for '{}.{}'", "db.t", "c1");
        }).doesNotThrowAnyException();
    }
}
