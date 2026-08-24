/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import org.slf4j.Logger;

/**
 * Emits bounded, single-line incremental DDL status messages without allowing logging failures to affect capture.
 */
final class IncrementalDdlLogger {

    private static final int MAX_DDL_PREVIEW_LENGTH = 4096;

    private IncrementalDdlLogger() {
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
        final StringBuilder preview = new StringBuilder(previewLength + 80);
        for (int i = 0; i < previewLength; i++) {
            final char character = ddl.charAt(i);
            preview.append(Character.isISOControl(character) ? ' ' : character);
        }
        if (ddl.length() > MAX_DDL_PREVIEW_LENGTH) {
            preview.append("...[truncated, length=")
                    .append(ddl.length())
                    .append(", hash=")
                    .append(Integer.toHexString(ddl.hashCode()))
                    .append(']');
        }
        return preview.toString();
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
