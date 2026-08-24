/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.slf4j.Logger;

public class IncrementalDdlLoggerTest {

    @Test
    public void shouldBoundAndFlattenDdlInLogMessages() {
        final String ddl = "ALTER TABLE t\nADD COLUMN c VARCHAR(8192) " + "x".repeat(5000);

        final String preview = IncrementalDdlLogger.ddlPreview(ddl);

        assertThat(preview).doesNotContain("\n", "\r");
        assertThat(preview.length()).isLessThanOrEqualTo(4200);
        assertThat(preview).contains("[truncated, length=5041, hash=");
    }

    @Test
    public void shouldNotPropagateLoggingRuntimeExceptions() {
        final Logger logger = mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isWarnEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("broken appender")).when(logger).info(anyString());
        doThrow(new IllegalStateException("broken appender")).when(logger).warn(anyString());
        doThrow(new IllegalStateException("broken appender")).when(logger).error(anyString());

        assertThatCode(() -> {
            IncrementalDdlLogger.info(logger, "INCREMENTAL_DDL_RECEIVED", "db1", "ALTER TABLE t ADD c INT", "");
            IncrementalDdlLogger.warn(logger, "INCREMENTAL_DDL_PARSE_FAILED", "db1", "bad ddl", "action=CONTINUE");
            IncrementalDdlLogger.error(logger, "INCREMENTAL_DDL_PARSE_FAILED", "db1", "bad ddl", "action=STOP");
        }).doesNotThrowAnyException();
    }

    @Test
    public void shouldNotBuildOrEmitMessagesForDisabledLevels() {
        final Logger logger = mock(Logger.class);

        IncrementalDdlLogger.info(logger, "INCREMENTAL_DDL_RECEIVED", "db1", "ALTER TABLE t ADD c INT", "");
        IncrementalDdlLogger.warn(logger, "INCREMENTAL_DDL_PARSE_FAILED", "db1", "bad ddl", "action=CONTINUE");
        IncrementalDdlLogger.error(logger, "INCREMENTAL_DDL_PARSE_FAILED", "db1", "bad ddl", "action=STOP");

        verify(logger, never()).info(anyString());
        verify(logger, never()).warn(anyString());
        verify(logger, never()).error(anyString());
    }
}
