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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.junit.logging.LogInterceptor;

import ch.qos.logback.classic.Level;

class IncrementalDdlLoggerTest {

    @Test
    void shouldBoundAndFlattenDdlInLogMessages() {
        final String ddl = "ALTER TABLE t\nADD COLUMN c VARCHAR(8192) " + "x".repeat(5000);

        final String preview = IncrementalDdlLogger.ddlPreview(ddl);

        assertThat(preview).doesNotContain("\n", "\r").contains("[truncated, length=5041]");
        assertThat(preview.length()).isLessThanOrEqualTo(4200);
    }

    @Test
    void shouldRedactCredentialsFromAccountManagementDdl() {
        final String ddl = "CREATE USER bob IDENTIFIED BY 'create-secret'; " +
                "ALTER USER bob IDENTIFIED BY 'new-secret'; GRANT SELECT ON db1.* TO bob";

        assertThat(IncrementalDdlLogger.ddlPreview(ddl))
                .startsWith("<redacted-sensitive-ddl")
                .doesNotContain("bob", "create-secret", "new-secret");
    }

    @Test
    void shouldRedactSensitiveDdlAndExceptionMessageFromParseFailureLog() {
        final String loggerName = "io.debezium.connector.binlog.ddl.test";
        final LogInterceptor interceptor = new LogInterceptor(loggerName);
        final Logger logger = org.slf4j.LoggerFactory.getLogger(loggerName);

        IncrementalDdlLogger.parseFailure(logger, "db1", "CREATE USER bob IDENTIFIED BY 'ddl-secret'", true,
                "schema.history.internal.skip.unparseable.ddl",
                new IllegalArgumentException("mismatched input 'exception-secret'"));

        final List<String> messages = interceptor.getLogEntriesThatContainsMessage("[INCREMENTAL_DDL_PARSE_FAILED]");
        assertThat(messages).singleElement().satisfies(message -> assertThat(message)
                .contains("action=CONTINUE", "<redacted-sensitive-ddl")
                .doesNotContain("bob", "ddl-secret", "exception-secret"));
    }

    @Test
    void shouldNotInspectParseFailureWhenItsLevelIsDisabled() {
        final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ddl.disabled.test");
        final Level originalLevel = logger.getLevel();
        try {
            logger.setLevel(Level.ERROR);
            final IllegalArgumentException exception = new IllegalArgumentException() {
                @Override
                public String getMessage() {
                    throw new AssertionError("disabled logging inspected exception message");
                }
            };

            assertThatCode(() -> IncrementalDdlLogger.parseFailure(logger, "db1", "bad ddl", true,
                    "schema.history.internal.skip.unparseable.ddl", exception)).doesNotThrowAnyException();
        }
        finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void shouldRedactParseFailureMessageForTruncatedDdl() {
        final String loggerName = "ddl.truncated.test";
        final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        final Level originalLevel = logger.getLevel();
        final LogInterceptor interceptor = new LogInterceptor(loggerName);
        final String ddl = "ALTER TABLE t ADD COLUMN c VARCHAR(8192) " + "x".repeat(4096) +
                "; CREATE USER bob IDENTIFIED BY 'secret'";
        try {
            logger.setLevel(Level.WARN);
            IncrementalDdlLogger.parseFailure(logger, "db1", ddl, true,
                    "schema.history.internal.skip.unparseable.ddl", new IllegalArgumentException("message secret"));

            assertThat(interceptor.getLogEntriesThatContainsMessage("[INCREMENTAL_DDL_PARSE_FAILED]"))
                    .singleElement()
                    .satisfies(message -> assertThat(message).contains("message=<redacted-sensitive-error>")
                            .doesNotContain("message secret"));
        }
        finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void shouldNotPropagateLoggingRuntimeExceptions() {
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
    void shouldNotBuildOrEmitMessagesForDisabledLevels() {
        final Logger logger = mock(Logger.class);

        IncrementalDdlLogger.info(logger, "INCREMENTAL_DDL_RECEIVED", "db1", "ALTER TABLE t ADD c INT", "");
        IncrementalDdlLogger.warn(logger, "INCREMENTAL_DDL_PARSE_FAILED", "db1", "bad ddl", "action=CONTINUE");
        IncrementalDdlLogger.error(logger, "INCREMENTAL_DDL_PARSE_FAILED", "db1", "bad ddl", "action=STOP");

        verify(logger, never()).info(anyString());
        verify(logger, never()).warn(anyString());
        verify(logger, never()).error(anyString());
    }
}
