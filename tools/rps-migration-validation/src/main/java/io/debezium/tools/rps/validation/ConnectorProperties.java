/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import java.util.Properties;

import org.apache.kafka.connect.storage.FileOffsetBackingStore;

final class ConnectorProperties {

    private static final String FILE_SCHEMA_HISTORY = "io.debezium.storage.file.history.FileSchemaHistory";

    private ConnectorProperties() {
    }

    static Properties binlog(
                            ValidationConfig config,
                            String connectorName,
                            String connectorClass,
                            String defaultPort,
                            String defaultServerId) {
        Properties props = base(config, connectorName, connectorClass, defaultPort);
        props.setProperty("database.server.id", config.optional(
                "validation.mysql.server.id", "RPS_VALIDATION_MYSQL_SERVER_ID", defaultServerId));
        props.setProperty("database.include.list", config.required(
                "validation.database.include.list", "RPS_VALIDATION_DATABASE_INCLUDE_LIST"));
        props.setProperty("table.include.list", config.required(
                "validation.table.include.list", "RPS_VALIDATION_TABLE_INCLUDE_LIST"));
        props.setProperty("database.ssl.mode", config.optional(
                "validation.database.ssl.mode", "RPS_VALIDATION_DATABASE_SSL_MODE", "disabled"));
        props.setProperty("include.query", Boolean.toString(config.bool(
                "validation.include.query", "RPS_VALIDATION_INCLUDE_QUERY", true)));
        props.setProperty("schema.history.internal.skip.unparseable.ddl", Boolean.toString(config.bool(
                "validation.skip.unparseable.ddl", "RPS_VALIDATION_SKIP_UNPARSEABLE_DDL", false)));
        props.setProperty("bigint.unsigned.handling.mode", "precise");
        return props;
    }

    static Properties oracle(ValidationConfig config) {
        Properties props = base(config, "oracle", "io.debezium.connector.oracle.OracleConnector", "1521");
        props.setProperty("database.dbname", config.required(
                "validation.database.dbname", "RPS_VALIDATION_DATABASE_DBNAME"));
        props.setProperty("schema.include.list", config.required(
                "validation.schema.include.list", "RPS_VALIDATION_SCHEMA_INCLUDE_LIST"));
        props.setProperty("table.include.list", config.required(
                "validation.table.include.list", "RPS_VALIDATION_TABLE_INCLUDE_LIST"));
        props.setProperty("interval.handling.mode", "string");
        props.setProperty("lob.enabled", Boolean.toString(config.bool(
                "validation.oracle.lob.enabled", "RPS_VALIDATION_ORACLE_LOB_ENABLED", true)));
        props.setProperty("log.mining.strategy", config.optional(
                "validation.oracle.log.mining.strategy",
                "RPS_VALIDATION_ORACLE_LOG_MINING_STRATEGY",
                "online_catalog"));
        return props;
    }

    private static Properties base(
                            ValidationConfig config,
                            String connectorName,
                            String connectorClass,
                            String defaultPort) {
        Properties props = new Properties();
        props.setProperty("name", "rps-validation-" + connectorName);
        props.setProperty("connector.class", connectorClass);
        props.setProperty("snapshot.mode", config.optional(
                "validation.snapshot.mode", "RPS_VALIDATION_SNAPSHOT_MODE", "no_data"));
        props.setProperty("database.hostname", config.required(
                "validation.database.hostname", "RPS_VALIDATION_DATABASE_HOSTNAME"));
        props.setProperty("database.port", config.optional(
                "validation.database.port", "RPS_VALIDATION_DATABASE_PORT", defaultPort));
        props.setProperty("database.user", config.required(
                "validation.database.user", "RPS_VALIDATION_DATABASE_USER"));
        props.setProperty("database.password", config.required(
                "validation.database.password", "RPS_VALIDATION_DATABASE_PASSWORD"));
        props.setProperty("topic.prefix", config.optional(
                "validation.topic.prefix", "RPS_VALIDATION_TOPIC_PREFIX", "rps_validation_" + connectorName));
        props.setProperty("include.schema.changes", "true");
        props.setProperty("snapshot.max.threads", config.optional(
                "validation.snapshot.max.threads", "RPS_VALIDATION_SNAPSHOT_MAX_THREADS", "1"));
        props.setProperty("errors.max.retries", config.optional(
                "validation.errors.max.retries", "RPS_VALIDATION_ERRORS_MAX_RETRIES", "2"));
        props.setProperty("offset.flush.interval.ms", config.optional(
                "validation.offset.flush.interval.ms", "RPS_VALIDATION_OFFSET_FLUSH_INTERVAL_MS", "1000"));
        props.setProperty("offset.storage", FileOffsetBackingStore.class.getName());
        props.setProperty("offset.storage.file.filename", config.stateFile(connectorName, "offsets.dat").toString());
        props.setProperty("schema.history.internal", FILE_SCHEMA_HISTORY);
        props.setProperty("schema.history.internal.file.filename",
                config.stateFile(connectorName, "schema-history.dat").toString());
        props.setProperty("schema.history.internal.store.only.captured.tables.ddl", "true");
        return props;
    }
}
