/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits bounded, single-line incremental DDL status messages without allowing logging failures to affect capture.
 */
final class IncrementalDdlLogger {

    static final String LOGGER_NAME = "io.debezium.connector.binlog.ddl";

    private static final int MAX_DDL_PREVIEW_LENGTH = 4096;
    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    private IncrementalDdlLogger() {
    }

    static void info(String status, String databaseName, String ddl, String details) {
        info(LOGGER, status, databaseName, ddl, details);
    }

    static void warn(String status, String databaseName, String ddl, String details) {
        warn(LOGGER, status, databaseName, ddl, details);
    }

    static void error(String status, String databaseName, String ddl, String details) {
        error(LOGGER, status, databaseName, ddl, details);
    }

    static void parseFailure(String databaseName, String ddl, boolean continueProcessing, String configName, Throwable exception) {
        try {
            if (continueProcessing && !LOGGER.isWarnEnabled()) {
                return;
            }
            if (!continueProcessing && !LOGGER.isErrorEnabled()) {
                return;
            }

            final String exceptionMessage = isSensitiveDdl(ddl) || (ddl != null && ddl.length() > MAX_DDL_PREVIEW_LENGTH)
                    ? "<redacted-sensitive-error>"
                    : ddlPreview(exception.getMessage());
            final String details = "action=" + (continueProcessing ? "CONTINUE" : "STOP") +
                    ", config=" + configName + "=" + continueProcessing +
                    (continueProcessing ? ", schemaStateMayBePartial=true" : "") +
                    ", exception=" + exception.getClass().getSimpleName() +
                    ", message=" + exceptionMessage;
            final String message = message("INCREMENTAL_DDL_PARSE_FAILED", databaseName, ddl, details);
            if (continueProcessing) {
                LOGGER.warn(message);
            }
            else {
                LOGGER.error(message);
            }
        }
        catch (RuntimeException ignored) {
            // Logging must never affect binlog processing.
        }
    }

    static void info(Logger logger, String status, String databaseName, String ddl, String details) {
        try {
            if (!logger.isInfoEnabled()) {
                return;
            }
            logger.info(message(status, databaseName, ddl, details));
        }
        catch (RuntimeException ignored) {
            // Logging must never affect binlog processing.
        }
    }

    static void warn(Logger logger, String status, String databaseName, String ddl, String details) {
        try {
            if (!logger.isWarnEnabled()) {
                return;
            }
            logger.warn(message(status, databaseName, ddl, details));
        }
        catch (RuntimeException ignored) {
            // Logging must never affect binlog processing.
        }
    }

    static void error(Logger logger, String status, String databaseName, String ddl, String details) {
        try {
            if (!logger.isErrorEnabled()) {
                return;
            }
            logger.error(message(status, databaseName, ddl, details));
        }
        catch (RuntimeException ignored) {
            // Logging must never affect binlog processing.
        }
    }

    static String ddlPreview(String ddl) {
        if (ddl == null) {
            return "<null>";
        }

        final int previewLength = Math.min(ddl.length(), MAX_DDL_PREVIEW_LENGTH);
        final String boundedDdl = ddl.substring(0, previewLength);
        if (containsSensitiveContent(boundedDdl)) {
            return "<redacted-sensitive-ddl length=" + ddl.length() + ">";
        }

        final StringBuilder preview = new StringBuilder(previewLength + 80);
        for (int i = 0; i < previewLength; i++) {
            final char character = boundedDdl.charAt(i);
            preview.append(Character.isISOControl(character) ? ' ' : character);
        }
        if (ddl.length() > MAX_DDL_PREVIEW_LENGTH) {
            preview.append("...[truncated, length=")
                    .append(ddl.length())
                    .append(']');
        }
        return preview.toString();
    }

    private static boolean containsSensitiveContent(String ddl) {
        final String upperCaseDdl = ddl.toUpperCase(Locale.ROOT);
        return upperCaseDdl.contains("PASSWORD") ||
                upperCaseDdl.contains("IDENTIFIED") ||
                upperCaseDdl.contains("CREDENTIAL") ||
                upperCaseDdl.contains("SECRET") ||
                upperCaseDdl.contains("PRIVATE_KEY") ||
                upperCaseDdl.contains("ACCESS_KEY") ||
                upperCaseDdl.contains("TOKEN") ||
                containsWord(upperCaseDdl, "GRANT") ||
                (containsWord(upperCaseDdl, "USER") &&
                        (containsWord(upperCaseDdl, "CREATE") || containsWord(upperCaseDdl, "ALTER")));
    }

    private static boolean isSensitiveDdl(String ddl) {
        if (ddl == null) {
            return false;
        }
        return containsSensitiveContent(ddl.substring(0, Math.min(ddl.length(), MAX_DDL_PREVIEW_LENGTH)));
    }

    private static boolean containsWord(String text, String word) {
        int index = text.indexOf(word);
        while (index >= 0) {
            final int end = index + word.length();
            final boolean startsAtBoundary = index == 0 || !isIdentifierCharacter(text.charAt(index - 1));
            final boolean endsAtBoundary = end == text.length() || !isIdentifierCharacter(text.charAt(end));
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            index = text.indexOf(word, index + 1);
        }
        return false;
    }

    private static boolean isIdentifierCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static String message(String status, String databaseName, String ddl, String details) {
        final StringBuilder message = new StringBuilder(160);
        message.append('[').append(status).append("] database='")
                .append(ddlPreview(databaseName)).append("', ddl='")
                .append(ddlPreview(ddl)).append('\'');
        if (details != null && !details.isEmpty()) {
            message.append(", ").append(ddlPreview(details));
        }
        return message.toString();
    }
}
