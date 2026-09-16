/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;

/** Runs a Debezium Embedded Engine for a bounded validation interval. */
public final class EmbeddedEngineRunner {

    public record Summary(int recordCount, boolean successful, String message) {
    }

    private EmbeddedEngineRunner() {
    }

    public static Summary run(Properties props, Duration duration, boolean printRecords) throws Exception {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Validation duration must be positive");
        }
        createStateParent(props, "offset.storage.file.filename");
        createStateParent(props, "schema.history.internal.file.filename");

        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger records = new AtomicInteger();
        AtomicBoolean successful = new AtomicBoolean();
        AtomicReference<String> completionMessage = new AtomicReference<>("engine did not report completion");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        DebeziumEngine<ChangeEvent<String, String>> engine = DebeziumEngine.create(Json.class)
                .using(props)
                .notifying(record -> {
                    records.incrementAndGet();
                    if (printRecords) {
                        System.out.println("CHANGE_EVENT: " + record.value());
                    }
                })
                .using((success, message, error) -> {
                    successful.set(success);
                    completionMessage.set(message);
                    if (error != null) {
                        failure.set(error);
                    }
                })
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rps-migration-validation-engine");
            thread.setDaemon(false);
            return thread;
        });
        executor.submit(() -> {
            try {
                engine.run();
            }
            catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
            finally {
                stopped.countDown();
            }
        });

        try {
            if (!stopped.await(duration.toMillis(), TimeUnit.MILLISECONDS)) {
                engine.close();
                if (!stopped.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Embedded engine did not stop within 30 seconds");
                }
            }
        }
        finally {
            engine.close();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException("Embedded engine validation failed: " + error.getMessage(), error);
        }

        Summary summary = new Summary(records.get(), successful.get(), completionMessage.get());
        System.out.printf("ENGINE_SUMMARY: records=%d, successful=%s, message=%s%n",
                summary.recordCount(), summary.successful(), summary.message());
        return summary;
    }

    static void runFromSystem(Properties props, ValidationConfig config) throws Exception {
        long runSeconds = config.positiveLong("validation.run.seconds", "RPS_VALIDATION_RUN_SECONDS", 60);
        boolean printRecords = config.bool("validation.print.records", "RPS_VALIDATION_PRINT_RECORDS", false);
        run(props, Duration.ofSeconds(runSeconds), printRecords);
    }

    private static void createStateParent(Properties props, String property) throws Exception {
        String value = props.getProperty(property);
        if (value != null) {
            Path parent = Path.of(value).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
    }
}
