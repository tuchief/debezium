/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import java.util.Map;

import org.slf4j.Logger;

import io.debezium.data.Envelope;
import io.debezium.relational.TableId;
import io.debezium.util.FailureLogLimiter;
import io.debezium.util.Loggings;

/**
 * Emits bounded DML failure diagnostics without exposing row payloads or affecting event processing.
 */
final class DmlFailureLogger {

    private final Logger logger;
    private final FailureLogLimiter limiter;

    DmlFailureLogger(Logger logger, FailureLogLimiter limiter) {
        this.logger = logger;
        this.limiter = limiter;
    }

    void warnUnknownTable(TableId tableId, Envelope.Operation operation, Map<String, ?> offset,
                          String binlogFilename, long startPosition, long stopPosition) {
        final String signature = "UNKNOWN_TABLE|" + tableId + "|" + operationName(operation);
        Loggings.logRateLimitedWarningAndTraceRecord(logger, limiter, signature, null,
                "[DML_PROCESSING_FAILED] action=CONTINUE, category=UNKNOWN_TABLE, table='{}', operation={}, " +
                        "offset={}, binlog='{}', startPosition={}, stopPosition={}",
                tableId, operationName(operation), offset, binlogFilename, startPosition, stopPosition);
    }

    void errorUnknownTable(TableId tableId, Envelope.Operation operation, Map<String, ?> offset,
                           String binlogFilename, long startPosition, long stopPosition) {
        Loggings.logErrorNoThrow(logger,
                "[DML_PROCESSING_FAILED] action=STOP, category=UNKNOWN_TABLE, table='{}', operation={}, " +
                        "offset={}, binlog='{}', startPosition={}, stopPosition={}",
                tableId, operationName(operation), offset, binlogFilename, startPosition, stopPosition);
    }

    void errorSchemaRowSizeMismatch(TableId tableId, Envelope.Operation operation, String image,
                                    int internalSchemaSize, int rowSize, String binlogFilename, long position) {
        Loggings.logErrorNoThrow(logger,
                "[DML_PROCESSING_FAILED] action=STOP, category=SCHEMA_ROW_SIZE_MISMATCH, table='{}', operation={}, " +
                        "image={}, internalSchemaSize={}, rowSize={}, binlog='{}', position={}",
                tableId, operationName(operation), image, internalSchemaSize, rowSize, binlogFilename, position);
    }

    private static String operationName(Envelope.Operation operation) {
        return operation == null ? "UNKNOWN" : operation.name();
    }
}
