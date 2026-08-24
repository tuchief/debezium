/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.data.Envelope;
import io.debezium.junit.logging.LogInterceptor;
import io.debezium.relational.TableId;
import io.debezium.util.FailureLogLimiter;

public class BinlogDmlFailureLoggingTest {

    private static final long WINDOW = SECONDS.toNanos(60);
    private static final long EXPIRY = MINUTES.toNanos(10);
    private static final TableId TABLE_ID = new TableId("catalog", null, "customers");

    @Test
    public void shouldRateLimitUnknownTableWarningAndSummarizeSuppressedEvents() {
        final AtomicLong time = new AtomicLong();
        final FailureLogLimiter limiter = new FailureLogLimiter(1024, 20, WINDOW, EXPIRY, time::get);
        final String loggerName = "io.debezium.connector.binlog.BinlogDmlFailureLoggingTest.unknownTable";
        final Logger logger = LoggerFactory.getLogger(loggerName);
        final LogInterceptor interceptor = new LogInterceptor(loggerName);
        final DmlFailureLogger failureLogger = new DmlFailureLogger(logger, limiter);

        failureLogger.warnUnknownTable(TABLE_ID, Envelope.Operation.CREATE, Collections.singletonMap("pos", 100),
                "mariadb-bin.000001", 100, 120);
        failureLogger.warnUnknownTable(TABLE_ID, Envelope.Operation.CREATE, Collections.singletonMap("pos", 101),
                "mariadb-bin.000001", 120, 140);
        time.set(WINDOW);
        failureLogger.warnUnknownTable(TABLE_ID, Envelope.Operation.CREATE, Collections.singletonMap("pos", 102),
                "mariadb-bin.000001", 140, 160);

        final List<String> messages = interceptor.getLogEntriesThatContainsMessage("category=UNKNOWN_TABLE");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1)).contains("suppressedCount=1");
        assertThat(messages).allMatch(message -> !message.contains("row-value"));
    }

    @Test
    public void shouldLogBoundedFatalSchemaRowSizeContext() {
        final String loggerName = "io.debezium.connector.binlog.BinlogDmlFailureLoggingTest.schemaMismatch";
        final Logger logger = LoggerFactory.getLogger(loggerName);
        final LogInterceptor interceptor = new LogInterceptor(loggerName);
        final DmlFailureLogger failureLogger = new DmlFailureLogger(logger, new FailureLogLimiter());

        failureLogger.errorSchemaRowSizeMismatch(TABLE_ID, Envelope.Operation.UPDATE, "after", 2, 1,
                "mariadb-bin.000054", 914869070);

        final List<String> messages = interceptor.getLogEntriesThatContainsMessage("category=SCHEMA_ROW_SIZE_MISMATCH");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0))
                .contains("action=STOP", "table='catalog.customers'", "operation=UPDATE", "image=after",
                        "internalSchemaSize=2", "rowSize=1", "binlog='mariadb-bin.000054'", "position=914869070")
                .doesNotContain("row-value");
    }
}
