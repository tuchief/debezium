/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.storage.FileOffsetBackingStore;
import org.apache.kafka.connect.storage.OffsetStorageWriter;

import io.debezium.config.Configuration;
import io.debezium.embedded.EmbeddedEngineConfig;
import io.debezium.embedded.EmbeddedWorkerConfig;
import io.debezium.embedded.KafkaConnectUtil;

/** Writes an explicit MariaDB GTID starting offset when requested by an operator. */
final class MariaDbOffsetSeeder {

    private MariaDbOffsetSeeder() {
    }

    static boolean seedWhenRequested(Properties connectorProperties, ValidationConfig config) throws Exception {
        String gtid = config.optional("validation.seed.gtid", "RPS_VALIDATION_SEED_GTID", "");
        if (gtid.isBlank()) {
            return false;
        }

        Path offsetFile = Path.of(connectorProperties.getProperty("offset.storage.file.filename"));
        if (Files.exists(offsetFile) && Files.size(offsetFile) > 0) {
            throw new IllegalStateException("Offset file already exists; refusing to overwrite: " + offsetFile);
        }
        Path parent = offsetFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Converter keyConverter = KafkaConnectUtil.converterForOffsetStore();
        Converter valueConverter = KafkaConnectUtil.converterForOffsetStore();
        Map<String, String> workerValues = new HashMap<>(
                Configuration.from(connectorProperties).asMap(EmbeddedEngineConfig.ALL_FIELDS));
        workerValues.put(WorkerConfig.KEY_CONVERTER_CLASS_CONFIG, JsonConverter.class.getName());
        workerValues.put(WorkerConfig.VALUE_CONVERTER_CLASS_CONFIG, JsonConverter.class.getName());

        FileOffsetBackingStore offsetStore = KafkaConnectUtil.fileOffsetBackingStore();
        offsetStore.configure(new EmbeddedWorkerConfig(workerValues));
        offsetStore.start();
        try {
            OffsetStorageWriter writer = new OffsetStorageWriter(
                    offsetStore,
                    connectorProperties.getProperty("name"),
                    keyConverter,
                    valueConverter);
            Map<String, Object> partition = Map.of(
                    "server", connectorProperties.getProperty("topic.prefix"));
            Map<String, Object> offset = new HashMap<>();
            offset.put("ts_sec", Instant.now().getEpochSecond());
            offset.put("file", config.optional(
                    "validation.seed.file", "RPS_VALIDATION_SEED_FILE", "binlog.000001"));
            offset.put("pos", config.positiveLong(
                    "validation.seed.position", "RPS_VALIDATION_SEED_POSITION", 4));
            offset.put("gtids", gtid);
            offset.put("row", 0);
            offset.put("event", 0);
            offset.put("server_id", config.positiveLong(
                    "validation.seed.server.id", "RPS_VALIDATION_SEED_SERVER_ID", 1));

            writer.offset(partition, offset);
            if (!writer.beginFlush()) {
                throw new IllegalStateException("No offset data was available to flush");
            }
            CountDownLatch flushed = new CountDownLatch(1);
            var future = writer.doFlush((error, ignored) -> flushed.countDown());
            if (future == null || !flushed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while writing the MariaDB validation offset");
            }
            future.get(10, TimeUnit.SECONDS);
            System.out.printf("SEEDED_OFFSET: file=%s, position=%s, path=%s%n",
                    offset.get("file"), offset.get("pos"), offsetFile);
            return true;
        }
        finally {
            offsetStore.stop();
        }
    }
}
