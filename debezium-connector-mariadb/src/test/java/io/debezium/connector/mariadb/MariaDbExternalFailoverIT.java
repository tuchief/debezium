/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.Configuration;
import io.debezium.connector.binlog.BinlogConnectorConfig;
import io.debezium.connector.binlog.BinlogConnectorConfig.SnapshotMode;
import io.debezium.connector.binlog.BinlogOffsetContext;
import io.debezium.connector.mariadb.gtid.MariaDbGtidSetFactory;
import io.debezium.embedded.async.AbstractAsyncEngineConnectorTest;
import io.debezium.storage.file.history.FileSchemaHistory;
import io.debezium.util.Testing.Files;

/** External acceptance for switching a connector from a MariaDB primary to its GTID replica. */
public class MariaDbExternalFailoverIT extends AbstractAsyncEngineConnectorTest {

    private static final String PRIMARY_HOST = System.getProperty("rps.mariadb.primary.host");
    private static final String REPLICA_HOST = System.getProperty("rps.mariadb.replica.host");
    private static final String DATABASE_NAME = "dbz_rps_failover_a09d99";
    private static final String TABLE_NAME = "events";
    private static final String TOPIC_PREFIX = "rps_failover_a09d99";
    private static final String TOPIC_NAME = TOPIC_PREFIX + "." + DATABASE_NAME + "." + TABLE_NAME;
    private static final Path SCHEMA_HISTORY_PATH = Files.createTestingPath("rps-failover-schema-history.dat").toAbsolutePath();

    private String password;

    @BeforeEach
    void beforeEach() throws Exception {
        password = System.getenv("RPS_MARIADB_PASSWORD");
        assertThat(PRIMARY_HOST).isNotBlank();
        assertThat(REPLICA_HOST).isNotBlank();
        assertThat(password).isNotBlank();

        stopConnector();
        initializeConnectorTestFramework();
        Files.delete(SCHEMA_HISTORY_PATH);
        execute(PRIMARY_HOST, "DROP DATABASE IF EXISTS `" + DATABASE_NAME + "`", "CREATE DATABASE `" + DATABASE_NAME + "`",
                "CREATE TABLE `" + DATABASE_NAME + "`.`" + TABLE_NAME + "` (id INT PRIMARY KEY, marker VARCHAR(64) NOT NULL)");
        awaitReplicaRowCount(0);
    }

    @AfterEach
    void afterEach() throws Exception {
        try {
            stopConnector();
        }
        finally {
            try {
                if (PRIMARY_HOST != null && password != null) {
                    execute(PRIMARY_HOST, "DROP DATABASE IF EXISTS `" + DATABASE_NAME + "`");
                }
            }
            finally {
                Files.delete(SCHEMA_HISTORY_PATH);
            }
        }
    }

    @Test
    void shouldContinueFromPrimaryOffsetOnReplicaWithoutDuplicateOrRegression() throws Exception {
        final Configuration primaryConfig = connectorConfig(PRIMARY_HOST);
        start(MariaDbConnector.class, primaryConfig);
        waitForStreamingRunning(Module.name(), TOPIC_PREFIX);

        execute(PRIMARY_HOST, "INSERT INTO `" + DATABASE_NAME + "`.`" + TABLE_NAME + "` VALUES (1, 'before-switch')");
        assertRecord(consumeRecordsByTopic(1).recordsForTopic(TOPIC_NAME).get(0), 1, "before-switch");
        stopConnector();

        final Map<String, ?> primaryOffset = committedOffset(primaryConfig);
        final String primaryGtid = (String) primaryOffset.get(BinlogOffsetContext.GTID_SET_KEY);
        final long primaryTimestamp = ((Number) primaryOffset.get(BinlogOffsetContext.LAST_BINLOG_EVENT_TIMESTAMP_KEY)).longValue();
        assertThat(primaryGtid).isNotBlank();
        awaitReplicaRowCount(1);

        final Configuration replicaConfig = connectorConfig(REPLICA_HOST);
        start(MariaDbConnector.class, replicaConfig);
        waitForStreamingRunning(Module.name(), TOPIC_PREFIX);

        execute(PRIMARY_HOST, "INSERT INTO `" + DATABASE_NAME + "`.`" + TABLE_NAME + "` VALUES (2, 'after-switch')");
        awaitReplicaRowCount(2);
        assertRecord(consumeRecordsByTopic(1).recordsForTopic(TOPIC_NAME).get(0), 2, "after-switch");
        stopConnector();

        final Map<String, ?> replicaOffset = committedOffset(replicaConfig);
        final MariaDbGtidSetFactory gtidSetFactory = new MariaDbGtidSetFactory();
        assertThat(gtidSetFactory.createGtidSet(primaryGtid)
                .isContainedWithin(gtidSetFactory.createGtidSet((String) replicaOffset.get(BinlogOffsetContext.GTID_SET_KEY))))
                .isTrue();
        assertThat(((Number) replicaOffset.get(BinlogOffsetContext.LAST_BINLOG_EVENT_TIMESTAMP_KEY)).longValue())
                .isGreaterThanOrEqualTo(primaryTimestamp);
    }

    private Configuration connectorConfig(String host) {
        return Configuration.create()
                .with(BinlogConnectorConfig.HOSTNAME, host)
                .with(BinlogConnectorConfig.PORT, 3306)
                .with(BinlogConnectorConfig.USER, "root")
                .with(BinlogConnectorConfig.PASSWORD, password)
                .with(BinlogConnectorConfig.SERVER_ID, 54001)
                .with(CommonConnectorConfig.TOPIC_PREFIX, TOPIC_PREFIX)
                .with(BinlogConnectorConfig.DATABASE_INCLUDE_LIST, DATABASE_NAME)
                .with(BinlogConnectorConfig.TABLE_INCLUDE_LIST, DATABASE_NAME + "\\." + TABLE_NAME)
                .with(BinlogConnectorConfig.SNAPSHOT_MODE, SnapshotMode.NO_DATA)
                .with(BinlogConnectorConfig.INCLUDE_SCHEMA_CHANGES, false)
                .with(BinlogConnectorConfig.SCHEMA_HISTORY, FileSchemaHistory.class)
                .with(FileSchemaHistory.FILE_PATH, SCHEMA_HISTORY_PATH)
                .with(MariaDbConnectorConfig.SSL_MODE, MariaDbConnectorConfig.MariaDbSecureConnectionMode.DISABLE)
                .build();
    }

    private Map<String, ?> committedOffset(Configuration config) {
        return readLastCommittedOffset(config, new MariaDbPartition(TOPIC_PREFIX, DATABASE_NAME).getSourcePartition());
    }

    private void assertRecord(SourceRecord record, int id, String marker) {
        final Struct after = ((Struct) record.value()).getStruct("after");
        assertThat(after.getInt32("id")).isEqualTo(id);
        assertThat(after.getString("marker")).isEqualTo(marker);
    }

    private void awaitReplicaRowCount(int expected) {
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(rowCount(REPLICA_HOST)).isEqualTo(expected));
    }

    private int rowCount(String host) throws SQLException {
        try (Connection connection = connect(host);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM `" + DATABASE_NAME + "`.`" + TABLE_NAME + "`")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private void execute(String host, String... statements) throws SQLException {
        try (Connection connection = connect(host); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private Connection connect(String host) throws SQLException {
        return DriverManager.getConnection("jdbc:mariadb://" + host + ":3306/mysql?sslMode=disable", "root", password);
    }
}
