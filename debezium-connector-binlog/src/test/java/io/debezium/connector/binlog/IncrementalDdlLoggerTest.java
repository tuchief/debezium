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

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.junit.logging.LogInterceptor;

import ch.qos.logback.classic.Level;

public class IncrementalDdlLoggerTest {

    @Test
    public void shouldUseDedicatedLoggerWhenDebeziumParentOnlyAllowsErrors() {
        final ch.qos.logback.classic.Logger parentLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.debezium");
        final ch.qos.logback.classic.Logger ddlLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(IncrementalDdlLogger.LOGGER_NAME);
        final Level originalParentLevel = parentLogger.getLevel();
        final Level originalDdlLevel = ddlLogger.getLevel();
        final LogInterceptor interceptor = new LogInterceptor(IncrementalDdlLogger.LOGGER_NAME);

        try {
            parentLogger.setLevel(Level.ERROR);
            ddlLogger.setLevel(Level.INFO);

            IncrementalDdlLogger.info("INCREMENTAL_DDL_RECEIVED", "db1", "ALTER TABLE t ADD c INT", "");

            assertThat(interceptor.getLogEntriesThatContainsMessage("[INCREMENTAL_DDL_RECEIVED]")).hasSize(1);
        }
        finally {
            parentLogger.setLevel(originalParentLevel);
            ddlLogger.setLevel(originalDdlLevel);
        }
    }

    @Test
    public void shouldBoundAndFlattenDdlInLogMessages() {
        final String ddl = "ALTER TABLE t\nADD COLUMN c VARCHAR(8192) " + "x".repeat(5000);

        final String preview = IncrementalDdlLogger.ddlPreview(ddl);

        assertThat(preview).doesNotContain("\n", "\r");
        assertThat(preview.length()).isLessThanOrEqualTo(4200);
        assertThat(preview).contains("[truncated, length=5041]");
    }

    @Test
    public void shouldRedactCredentialsFromAccountManagementDdl() {
        final String ddl = "CREATE USER 'alice'@'%' IDENTIFIED WITH mysql_native_password BY 'create-secret'; " +
                "CREATE USER bob IDENTIFIED VIA ed25519 USING PASSWORD('plugin-secret'); " +
                "ALTER USER 'alice'@'%' IDENTIFIED BY 'new-secret' REPLACE 'old-secret'; " +
                "SET PASSWORD FOR 'u=x'@'%' = OLD_PASSWORD ( 'set-secret' ); " +
                "GRANT SELECT ON db1.* TO 'bob'@'%' IDENTIFIED/**/BY 'grant-secret'";

        final String preview = IncrementalDdlLogger.ddlPreview(ddl);

        assertThat(preview)
                .startsWith("<redacted-sensitive-ddl")
                .contains("length=")
                .doesNotContain("alice", "bob", "create-secret", "plugin-secret", "new-secret", "old-secret",
                        "set-secret", "grant-secret");
    }

    @Test
    public void shouldRedactSensitiveDdlAndExceptionMessageFromParseFailureLog() {
        final ch.qos.logback.classic.Logger ddlLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(IncrementalDdlLogger.LOGGER_NAME);
        final Level originalDdlLevel = ddlLogger.getLevel();
        final LogInterceptor interceptor = new LogInterceptor(IncrementalDdlLogger.LOGGER_NAME);

        try {
            ddlLogger.setLevel(Level.WARN);
            IncrementalDdlLogger.parseFailure("db1", "CREATE USER bob IDENTIFIED BY 'ddl-secret'", true,
                    "schema.history.internal.skip.unparseable.ddl",
                    new IllegalArgumentException("mismatched input 'Q7v9K2xL' expecting end of statement"));

            final List<String> messages = interceptor.getLogEntriesThatContainsMessage("[INCREMENTAL_DDL_PARSE_FAILED]");
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0))
                    .contains("action=CONTINUE", "<redacted-sensitive-ddl")
                    .doesNotContain("bob", "ddl-secret", "Q7v9K2xL");
        }
        finally {
            ddlLogger.setLevel(originalDdlLevel);
        }
    }

    @Test
    public void shouldNotInspectParseFailureWhenItsLevelIsDisabled() {
        final ch.qos.logback.classic.Logger ddlLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(IncrementalDdlLogger.LOGGER_NAME);
        final Level originalDdlLevel = ddlLogger.getLevel();

        try {
            ddlLogger.setLevel(Level.ERROR);
            final IllegalArgumentException exception = new IllegalArgumentException() {
                @Override
                public String getMessage() {
                    throw new AssertionError("disabled logging inspected the exception message");
                }
            };

            assertThatCode(() -> IncrementalDdlLogger.parseFailure("db1", "bad ddl", true,
                    "schema.history.internal.skip.unparseable.ddl", exception)).doesNotThrowAnyException();
        }
        finally {
            ddlLogger.setLevel(originalDdlLevel);
        }
    }

    @Test
    public void shouldRedactParseFailureMessageForTruncatedDdl() {
        final ch.qos.logback.classic.Logger ddlLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(IncrementalDdlLogger.LOGGER_NAME);
        final Level originalDdlLevel = ddlLogger.getLevel();
        final LogInterceptor interceptor = new LogInterceptor(IncrementalDdlLogger.LOGGER_NAME);
        final String ddl = "ALTER TABLE t ADD COLUMN c VARCHAR(8192) " + "x".repeat(4096) +
                "; CREATE USER bob IDENTIFIED BY 'Q7v9K2xL'";

        try {
            ddlLogger.setLevel(Level.WARN);
            IncrementalDdlLogger.parseFailure("db1", ddl, true,
                    "schema.history.internal.skip.unparseable.ddl",
                    new IllegalArgumentException("mismatched input 'Q7v9K2xL' expecting end of statement"));

            final List<String> messages = interceptor.getLogEntriesThatContainsMessage("[INCREMENTAL_DDL_PARSE_FAILED]");
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0))
                    .contains("message=<redacted-sensitive-error>")
                    .doesNotContain("Q7v9K2xL");
        }
        finally {
            ddlLogger.setLevel(originalDdlLevel);
        }
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
