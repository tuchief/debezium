/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.storage.FileOffsetBackingStore;
import org.apache.kafka.connect.storage.OffsetStorageWriter;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.slf4j.LoggerFactory;

import io.debezium.config.Configuration;
import io.debezium.config.Instantiator;
import io.debezium.embedded.EmbeddedEngineConfig;
import io.debezium.embedded.EmbeddedWorkerConfig;
import io.debezium.embedded.KafkaConnectUtil;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Manual MariaDB embedded-engine reproducer.
 * <p>
 * The {@code repro.*} system properties are intentionally test-only. They allow this class to demonstrate that a
 * producer-side GTID failure is not delivered to the change-event consumer and is only delivered to application code
 * when a {@link DebeziumEngine.CompletionCallback} is registered.
 */
public class DebeziumMariaDBExample {

    private static final String DEFAULT_OFFSET_FILE = "/Users/tuchief/Downloads/debezium_local_test_data/mariadb/offsets.dat";
    private static final String DEFAULT_SCHEMA_HISTORY_FILE = "/Users/tuchief/Downloads/debezium_local_test_data/mariadb/schemahistory.dat";

    public static void main(String[] args) throws Exception {
        String validationMode = System.getProperty("repro.validation.mode");
        if (validationMode != null && !validationMode.isBlank()) {
            runValidationSuite(validationMode);
            return;
        }

        ((Logger) LoggerFactory.getLogger("io.debezium.relational")).setLevel(Level.DEBUG);
        ((Logger) LoggerFactory.getLogger("io.debezium.connector.binlog.jdbc")).setLevel(Level.DEBUG);

        Properties props = connectorProperties();
        seedOffsetWhenRequested(props);
        if (booleanProperty("repro.seed.only", false)) {
            System.out.println("SEED_ONLY: offset written, engine not started");
            return;
        }

        boolean registerCompletionCallback = booleanProperty("repro.register.completion.callback", false);
        long runSeconds = longProperty("repro.run.seconds", 0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger changeEventCount = new AtomicInteger();

        DebeziumEngine.Builder<ChangeEvent<String, String>> builder = DebeziumEngine.create(Json.class)
                .using(props)
                .notifying(record -> {
                    changeEventCount.incrementAndGet();
                    System.out.println("CHANGE_EVENT_CONSUMER: " + record.value());
                });

        if (registerCompletionCallback) {
            builder.using((success, message, error) -> {
                System.out.printf("COMPLETION_CALLBACK: success=%s, message=%s%n", success, message);
                printCauseChain(error);
            });
        }
        else {
            System.out.println("COMPLETION_CALLBACK: not registered");
        }

        DebeziumEngine<ChangeEvent<String, String>> engine = builder.build();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                engine.run();
                System.out.println("ENGINE_RUN: returned normally");
            }
            catch (Throwable error) {
                System.out.println("ENGINE_RUN: threw to application thread");
                printCauseChain(error);
            }
            finally {
                System.out.println("CHANGE_EVENT_CONSUMER_COUNT: " + changeEventCount.get());
                executorService.shutdown();
                latch.countDown();
            }
        });

        if (runSeconds > 0) {
            Thread closer = new Thread(() -> {
                try {
                    TimeUnit.SECONDS.sleep(runSeconds);
                    System.out.println("TEST_TIMEOUT: closing engine after " + runSeconds + " seconds");
                    engine.close();
                }
                catch (Exception error) {
                    printCauseChain(error);
                }
            }, "debezium-example-timeout");
            closer.setDaemon(true);
            closer.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                engine.close();
                executorService.shutdown();
                executorService.awaitTermination(30, TimeUnit.SECONDS);
            }
            catch (Exception error) {
                printCauseChain(error);
            }
            finally {
                latch.countDown();
            }
        }));

        latch.await();
        System.out.println("Debezium engine stopped");
    }

    static List<String> validationTestClassNames(String mode) {
        if (!"mariadb".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported repro.validation.mode: " + mode);
        }
        return List.of(
                "io.debezium.connector.mariadb.MariaDbGtidSetTest",
                "io.debezium.connector.mariadb.MariaDbAntlrDdlParserTest",
                "io.debezium.connector.mariadb.MariaDbStreamingChangeEventSourceTest",
                "io.debezium.connector.mariadb.MariaDbConnectorTaskTest",
                "io.debezium.connector.mysql.MySqlConnectorTaskTest",
                "io.debezium.relational.history.KafkaSchemaHistoryBufferingTest",
                "io.debezium.relational.history.KafkaSchemaHistoryTest",
                "io.debezium.util.BoundedConcurrentHashMapTest",
                "io.debezium.relational.history.SchemaHistoryBufferingTest",
                "io.debezium.ddl.parser.mariadb.MariaDBBackportTokenAliasTest",
                "io.debezium.ddl.parser.mariadb.MariaDBTokenCompatibilityTest",
                "io.debezium.ddl.parser.mariadb.MariaDBSystemVersioningTest",
                "io.debezium.connector.mariadb.SpecialCharactersIT",
                "io.debezium.connector.mariadb.UuidColumnIT",
                "io.debezium.connector.mariadb.MariaVectorIT",
                "io.debezium.connector.mariadb.SystemVersionedTableIT");
    }

    private static void runValidationSuite(String mode) throws ClassNotFoundException {
        String serverVersion = readMariaDbVersion();
        requireMariaDbVectorVersion(serverVersion);
        System.out.println("VALIDATION_ENVIRONMENT: MariaDB " + serverVersion);
        System.out.println("VALIDATION_SCOPE: MySQL integration tests require a separate MySQL 9+ amd64 environment and are not reported as passed here");
        System.out.println("VALIDATION_BLOCKED: binlog network timeout backport requires BinaryLogClient net read/write timeout APIs");

        List<String> classNames = validationTestClassNames(mode);
        Class<?>[] testClasses = new Class<?>[classNames.size()];
        for (int i = 0; i < classNames.size(); i++) {
            testClasses[i] = Class.forName(classNames.get(i));
            System.out.println("VALIDATION_SELECTED: " + classNames.get(i));
        }

        System.out.printf("VALIDATION_START: mode=%s, classes=%d%n", mode, testClasses.length);
        Result result = JUnitCore.runClasses(testClasses);
        for (Failure failure : result.getFailures()) {
            System.out.println("VALIDATION_FAILURE: " + failure);
        }
        System.out.printf("VALIDATION_RESULT: run=%d, failures=%d, ignored=%d, runtime_ms=%d%n",
                result.getRunCount(), result.getFailureCount(), result.getIgnoreCount(), result.getRunTime());
        if (!result.wasSuccessful()) {
            throw new AssertionError("Backport validation failed with " + result.getFailureCount() + " failure(s)");
        }
        System.out.println("VALIDATION_PASS: all selected MariaDB backport regressions passed");
    }

    private static String readMariaDbVersion() {
        String hostname = System.getProperty("database.hostname", "localhost");
        String port = System.getProperty("database.port", "3306");
        String user = System.getProperty("database.user", "snapper");
        String password = System.getProperty("database.password", "snapperpass");
        String url = "jdbc:mariadb://" + hostname + ":" + port + "/mysql?sslMode=disable";
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("MariaDB version query returned no rows");
            }
            return resultSet.getString(1);
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to verify the MariaDB validation environment", e);
        }
    }

    static void requireMariaDbVectorVersion(String version) {
        try {
            String[] components = version.split("-", 2)[0].split("\\.");
            int major = Integer.parseInt(components[0]);
            int minor = Integer.parseInt(components[1]);
            if (major < 11 || major == 11 && minor < 7) {
                throw new IllegalStateException("MariaDB 11.7 or newer is required so VECTOR tests cannot be silently skipped; found " + version);
            }
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to parse MariaDB version: " + version, e);
        }
    }

    private static Properties connectorProperties() {
        Properties props = new Properties();
        props.put("name", property("repro.engine.name", "debezium-embedded-mariadb"));
        props.put("connector.class", "io.debezium.connector.mariadb.MariaDbConnector");
        props.setProperty("snapshot.mode", property("repro.snapshot.mode", "schema_only"));
        props.put("database.hostname", externalProperty("repro.database.hostname", "MARIADB_HOST"));
        props.put("database.port", property("repro.database.port", "3306"));
        props.put("database.user", externalProperty("repro.database.user", "MARIADB_USER"));
        props.put("database.password", externalProperty("repro.database.password", "MARIADB_PASSWORD"));
        props.put("database.server.id", property("repro.database.server.id", "123123"));
        props.put("include.schema.changes", "true");
        props.put("database.ssl.mode", "disabled");
        props.put("topic.prefix", property("repro.topic.prefix", "dbz"));
        props.setProperty("schema.history.internal.store.only.captured.tables.ddl", "true");
        props.setProperty("offset.storage", FileOffsetBackingStore.class.getName());
        props.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        props.setProperty("offset.storage.file.filename", property("repro.offset.file", DEFAULT_OFFSET_FILE));
        props.setProperty("schema.history.internal.file.filename",
                property("repro.schema.history.file", DEFAULT_SCHEMA_HISTORY_FILE));
        props.setProperty("database.include.list", property("repro.database.include.list", "auto_mrdb_gdb_incr"));
        props.setProperty("table.include.list", property("repro.table.include.list", "auto_mrdb_gdb_incr.t_truncate"));
        props.setProperty("include.query", "true");
        props.setProperty("snapshot.max.threads", "32");
        props.setProperty("errors.max.retries", property("repro.errors.max.retries", "2"));
        props.setProperty("offset.flush.interval.ms", "1000");
        props.setProperty("bigint.unsigned.handling.mode", "precise");
        return props;
    }

    private static void seedOffsetWhenRequested(Properties props) throws Exception {
        String gtid = System.getProperty("repro.seed.gtid");
        if (gtid == null || gtid.isBlank()) {
            return;
        }

        Path offsetFile = Paths.get(props.getProperty("offset.storage.file.filename"));
        Path parent = offsetFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Configuration config = Configuration.from(props);
        Map<String, String> workerValues = new HashMap<>(config.asMap(EmbeddedEngineConfig.ALL_FIELDS));
        workerValues.put(WorkerConfig.KEY_CONVERTER_CLASS_CONFIG, JsonConverter.class.getName());
        workerValues.put(WorkerConfig.VALUE_CONVERTER_CLASS_CONFIG, JsonConverter.class.getName());
        WorkerConfig workerConfig = new EmbeddedWorkerConfig(workerValues);

        Map<String, String> converterConfig = Collections.singletonMap(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, "false");
        Converter keyConverter = Instantiator.getInstance(JsonConverter.class.getName());
        keyConverter.configure(converterConfig, true);
        Converter valueConverter = Instantiator.getInstance(JsonConverter.class.getName());
        valueConverter.configure(converterConfig, false);

        FileOffsetBackingStore offsetStore = KafkaConnectUtil.fileOffsetBackingStore();
        offsetStore.configure(workerConfig);
        offsetStore.start();
        CountDownLatch flushLatch = new CountDownLatch(1);
        try {
            OffsetStorageWriter writer = new OffsetStorageWriter(
                    offsetStore,
                    props.getProperty("name"),
                    keyConverter,
                    valueConverter);

            Map<String, Object> partition = Collections.singletonMap("server", props.getProperty("topic.prefix"));
            Map<String, Object> offset = new HashMap<>();
            offset.put("ts_sec", Instant.now().getEpochSecond());
            offset.put("file", property("repro.seed.file", "bin_log.000001"));
            offset.put("pos", longProperty("repro.seed.pos", 4));
            offset.put("gtids", gtid);
            offset.put("row", 0);
            offset.put("event", 0);
            offset.put("server_id", longProperty("repro.seed.server.id", 1));

            writer.offset(partition, offset);
            if (!writer.beginFlush()) {
                throw new IllegalStateException("No offset data was available to flush");
            }
            writer.doFlush((error, ignored) -> {
                if (error != null) {
                    printCauseChain(error);
                }
                flushLatch.countDown();
            });
            if (!flushLatch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while writing the reproduction offset");
            }
            System.out.printf("SEEDED_OFFSET: file=%s, pos=%s, gtids=%s, path=%s%n",
                    offset.get("file"), offset.get("pos"), offset.get("gtids"), offsetFile);
        }
        finally {
            offsetStore.stop();
        }
    }

    private static void printCauseChain(Throwable error) {
        if (error == null) {
            System.out.println("ERROR_CHAIN: <none>");
            return;
        }
        int depth = 0;
        while (error != null && depth < 20) {
            System.out.printf("ERROR_CHAIN[%d]: %s: %s%n", depth, error.getClass().getName(), error.getMessage());
            error = error.getCause();
            depth++;
        }
    }

    private static String property(String name, String defaultValue) {
        return System.getProperty(name, defaultValue);
    }

    private static String externalProperty(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentName);
        }
        return requireNonBlank(value, propertyName + " or " + environmentName);
    }

    static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required configuration is missing: " + name);
        }
        return value;
    }

    private static boolean booleanProperty(String name, boolean defaultValue) {
        return Boolean.parseBoolean(property(name, Boolean.toString(defaultValue)));
    }

    private static long longProperty(String name, long defaultValue) {
        return Long.parseLong(property(name, Long.toString(defaultValue)));
    }
}
