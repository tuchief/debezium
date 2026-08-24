/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.util;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Functionality for dealing with logging.
 *
 * @author Chris Cranford
 */
public class Loggings {

    private static final Logger LOGGER = LoggerFactory.getLogger(Loggings.class);

    /**
     * Log a warning message and explicitly append the source of the warning as a separate log entry that uses
     * trace logging to prevent unintended leaking of sensitive data.
     *
     * @param logger the logger instance
     * @param record the record the log entry is based upon
     * @param message the warning message to be logged
     * @param arguments the arguments passed to the warning message
     */
    public static void logWarningAndTraceRecord(Logger logger, Object record, String message, Object... arguments) {
        logger.warn(message, arguments);
        LOGGER.trace("Source of warning is record '{}'", record);
    }

    /**
     * Logs a warning only when permitted by the supplied limiter. Runtime failures from the limiter, formatter,
     * record, or logging backend are contained so this diagnostic path cannot affect event processing.
     */
    public static void logRateLimitedWarningAndTraceRecord(Logger logger, FailureLogLimiter limiter, String signature,
                                                           Object record, String message, Object... arguments) {
        try {
            if (!logger.isWarnEnabled()) {
                return;
            }
            final FailureLogLimiter.Decision decision = limiter.acquire(signature);
            if (!decision.shouldLog()) {
                return;
            }

            String effectiveMessage = message;
            Object[] effectiveArguments = arguments;
            if (decision.suppressedCount() > 0 || decision.overflow()) {
                effectiveMessage += " [suppressedCount={}, overflow={}]";
                effectiveArguments = appendBeforeThrowable(arguments, decision.suppressedCount(), decision.overflow());
            }
            logger.warn(effectiveMessage, effectiveArguments);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Source of warning is record '{}'", record);
            }
        }
        catch (RuntimeException ignored) {
            // Diagnostics must never affect event processing.
        }
    }

    /**
     * Logs an error only when permitted by the supplied limiter and contains diagnostic runtime failures.
     */
    public static void logRateLimitedErrorAndTraceRecord(Logger logger, FailureLogLimiter limiter, String signature,
                                                         Object record, String message, Object... arguments) {
        try {
            if (!logger.isErrorEnabled()) {
                return;
            }
            final FailureLogLimiter.Decision decision = limiter.acquire(signature);
            if (!decision.shouldLog()) {
                return;
            }

            String effectiveMessage = message;
            Object[] effectiveArguments = arguments;
            if (decision.suppressedCount() > 0 || decision.overflow()) {
                effectiveMessage += " [suppressedCount={}, overflow={}]";
                effectiveArguments = appendBeforeThrowable(arguments, decision.suppressedCount(), decision.overflow());
            }
            logger.error(effectiveMessage, effectiveArguments);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Source of error is record '{}'", record);
            }
        }
        catch (RuntimeException ignored) {
            // Diagnostics must never affect event processing.
        }
    }

    /**
     * Logs an error without allowing logging runtime failures to affect the caller's control flow.
     */
    public static void logErrorNoThrow(Logger logger, String message, Object... arguments) {
        try {
            if (logger.isErrorEnabled()) {
                logger.error(message, arguments);
            }
        }
        catch (RuntimeException ignored) {
            // Diagnostics must never affect event processing.
        }
    }

    /**
     * Log a debug message and explicitly append the source of the debug entry as a separate log entry that uses
     * trace logging to prevent unintended leaking of sensitive data.
     *
     * @param logger the logger instance
     * @param record the record the log entry is based upon
     * @param message the debug message to be logged
     * @param arguments the arguments passed to the debug message
     */
    public static void logDebugAndTraceRecord(Logger logger, Object record, String message, Object... arguments) {
        logger.debug(message, arguments);
        LOGGER.trace("Source of debug is record '{}'", record);
    }

    /**
     * Log an error message and explicitly append the source of the error entry as a separate log entry that uses
     * trace logging to prevent unintended leaking of sensitive data.
     *
     * @param logger the logger instance
     * @param record the record the log entry is based upon
     * @param message the error message to be logged
     * @param arguments the arguments passed to the error message
     */
    public static void logErrorAndTraceRecord(Logger logger, Object record, String message, Object... arguments) {
        logger.error(message, arguments);
        LOGGER.trace("Source of error is record '{}'", record);
    }

    /**
     * Log an error message and explicitly append the source of the error entry as a separate log entry that uses
     * trace logging to prevent unintended leaking of sensitive data.
     *
     * @param logger the logger instance
     * @param record the record the log entry is based upon
     * @param message the error message to be logged
     * @param t the exception that caused the error
     */
    public static void logErrorAndTraceRecord(Logger logger, Object record, String message, Throwable t) {
        logger.error(message, t);
        LOGGER.trace("Source of error is record '{}'", record);
    }

    private static Object[] appendBeforeThrowable(Object[] arguments, Object... additions) {
        final boolean hasThrowable = arguments.length > 0 && arguments[arguments.length - 1] instanceof Throwable;
        final int argumentCount = hasThrowable ? arguments.length - 1 : arguments.length;
        final Object[] result = Arrays.copyOf(arguments, arguments.length + additions.length);
        System.arraycopy(additions, 0, result, argumentCount, additions.length);
        if (hasThrowable) {
            result[result.length - 1] = arguments[arguments.length - 1];
        }
        return result;
    }
}
