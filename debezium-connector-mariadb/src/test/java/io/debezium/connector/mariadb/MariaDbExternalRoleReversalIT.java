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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.Configuration;
import io.debezium.connector.binlog.BinlogConnectorConfig;
import io.debezium.connector.binlog.BinlogConnectorConfig.SnapshotMode;
import io.debezium.connector.binlog.BinlogOffsetContext;
import io.debezium.connector.mariadb.gtid.MariaDbGtidSetFactory;
import io.debezium.data.Envelope;
import io.debezium.embedded.async.AbstractAsyncEngineConnectorTest;
import io.debezium.storage.file.history.FileSchemaHistory;
import io.debezium.util.Testing.Files;

/**
 * External acceptance for a connector that remains attached to one MariaDB node while that node changes between replica and primary roles.
 */
public class MariaDbExternalRoleReversalIT extends AbstractAsyncEngineConnectorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MariaDbExternalRoleReversalIT.class);

    private static final String ORIGINAL_PRIMARY_HOST = System.getProperty("rps.mariadb.original.primary.host");
    private static final String FIXED_CONNECTOR_HOST = System.getProperty("rps.mariadb.fixed.connector.host");
    private static final boolean ROLE_REVERSAL_ENABLED = Boolean.getBoolean("rps.mariadb.role.reversal.enabled");
    private static final String DATABASE_NAME = "dbz_rps_role_reversal_a09d99";
    private static final String TABLE_NAME = "events";
    private static final String TOPIC_PREFIX = "rps_role_reversal_a09d99";
    private static final String TOPIC_NAME = TOPIC_PREFIX + "." + DATABASE_NAME + "." + TABLE_NAME;
    private static final Path SCHEMA_HISTORY_PATH = Files.createTestingPath("rps-role-reversal-schema-history.dat").toAbsolutePath();

    private final MariaDbGtidSetFactory gtidSetFactory = new MariaDbGtidSetFactory();

    private String password;
    private boolean topologyTouched;
    private boolean topologyRestored;

    @BeforeEach
    void beforeEach() throws Exception {
        password = System.getenv("RPS_MARIADB_PASSWORD");
        assertThat(ROLE_REVERSAL_ENABLED)
                .as("the destructive external test requires -Drps.mariadb.role.reversal.enabled=true")
                .isTrue();
        assertThat(ORIGINAL_PRIMARY_HOST).isEqualTo("192.168.0.136");
        assertThat(FIXED_CONNECTOR_HOST).isEqualTo("192.168.0.137");
        assertThat(password).isNotBlank();

        stopConnector();
        initializeConnectorTestFramework();
        Files.delete(SCHEMA_HISTORY_PATH);
    }

    @AfterEach
    void afterEach() throws Exception {
        try {
            stopConnector();
        }
        finally {
            try {
                if (topologyTouched && !topologyRestored) {
                    restoreOriginalTopology();
                }
            }
            finally {
                Files.delete(SCHEMA_HISTORY_PATH);
            }
        }
    }

    @Test
    void shouldContinueIncrementalCaptureOnFixedHostAcrossRoleReversalAndFailback() throws Exception {
        assertInitialTopology();
        topologyTouched = true;

        try {
            setReplicaDelay(FIXED_CONNECTOR_HOST, 0);
            waitForGtid(FIXED_CONNECTOR_HOST, gtidBinlogPosition(ORIGINAL_PRIMARY_HOST));
            assertHealthyReplica(FIXED_CONNECTOR_HOST, ORIGINAL_PRIMARY_HOST, 0, "Slave_Pos");

            execute(ORIGINAL_PRIMARY_HOST,
                    "CREATE DATABASE `" + DATABASE_NAME + "`",
                    "CREATE TABLE `" + DATABASE_NAME + "`.`" + TABLE_NAME
                            + "` (id INT PRIMARY KEY, marker VARCHAR(64) NOT NULL)");
            awaitSchema(FIXED_CONNECTOR_HOST, true);

            final Configuration config = connectorConfig();
            start(MariaDbConnector.class, config);
            waitForStreamingRunning(Module.name(), TOPIC_PREFIX);

            execute(ORIGINAL_PRIMARY_HOST,
                    "INSERT INTO `" + DATABASE_NAME + "`.`" + TABLE_NAME + "` VALUES (1, 'replica-before-reversal')");
            awaitRowCount(FIXED_CONNECTOR_HOST, 1);
            final Map<String, ?> phaseOneOffset = assertRecord(consumeNextRecord(), 1, "replica-before-reversal");

            reverseRoles(ORIGINAL_PRIMARY_HOST, FIXED_CONNECTOR_HOST);
            execute(FIXED_CONNECTOR_HOST,
                    "INSERT INTO `" + DATABASE_NAME + "`.`" + TABLE_NAME + "` VALUES (2, 'fixed-host-as-primary')");
            awaitRowCount(ORIGINAL_PRIMARY_HOST, 2);
            final Map<String, ?> phaseTwoOffset = assertRecord(consumeNextRecord(), 2, "fixed-host-as-primary");
            assertOffsetProgression(phaseOneOffset, phaseTwoOffset);

            reverseRoles(FIXED_CONNECTOR_HOST, ORIGINAL_PRIMARY_HOST);
            execute(ORIGINAL_PRIMARY_HOST,
                    "INSERT INTO `" + DATABASE_NAME + "`.`" + TABLE_NAME + "` VALUES (3, 'fixed-host-restored-as-replica')");
            awaitRowCount(FIXED_CONNECTOR_HOST, 3);
            final Map<String, ?> phaseThreeOffset = assertRecord(consumeNextRecord(), 3, "fixed-host-restored-as-replica");
            assertOffsetProgression(phaseTwoOffset, phaseThreeOffset);

            LOGGER.info("Replica-only role reversal acceptance offsets: phase1={}, phase2={}, phase3={}",
                    phaseOneOffset, phaseTwoOffset, phaseThreeOffset);
        }
        finally {
            stopConnector();
            restoreOriginalTopology();
            topologyRestored = true;
        }
    }

    private void assertInitialTopology() throws SQLException {
        assertThat(readOnly(ORIGINAL_PRIMARY_HOST)).isFalse();
        assertThat(readOnly(FIXED_CONNECTOR_HOST)).isTrue();
        assertThat(schemaExists(ORIGINAL_PRIMARY_HOST)).isFalse();
        assertThat(schemaExists(FIXED_CONNECTOR_HOST)).isFalse();
        assertRequiredBinlogSettings(ORIGINAL_PRIMARY_HOST, 136);
        assertRequiredBinlogSettings(FIXED_CONNECTOR_HOST, 137);
        assertHealthyReplica(FIXED_CONNECTOR_HOST, ORIGINAL_PRIMARY_HOST, 300, "Slave_Pos");
        assertThat(nonReplicationClientCount(ORIGINAL_PRIMARY_HOST)).isZero();
        assertThat(nonReplicationClientCount(FIXED_CONNECTOR_HOST)).isZero();
    }

    private void assertRequiredBinlogSettings(String host, int serverId) throws SQLException {
        assertThat(queryInt(host, "SELECT @@server_id")).isEqualTo(serverId);
        assertThat(queryInt(host, "SELECT @@global.log_bin")).isEqualTo(1);
        assertThat(queryInt(host, "SELECT @@global.log_slave_updates")).isEqualTo(1);
        assertThat(queryString(host, "SELECT @@global.binlog_format")).isEqualTo("ROW");
        assertThat(queryString(host, "SELECT @@global.binlog_row_image")).isEqualTo("FULL");
        assertThat(queryInt(host, "SELECT @@global.log_bin_compress")).isEqualTo(1);
        assertThat(queryInt(host, "SELECT @@global.gtid_strict_mode")).isEqualTo(1);
    }

    private void reverseRoles(String oldPrimary, String newPrimary) throws SQLException {
        execute(oldPrimary, "SET GLOBAL read_only=ON");
        assertThat(readOnly(oldPrimary)).isTrue();

        final String finalGtid = gtidBinlogPosition(oldPrimary);
        waitForGtid(newPrimary, finalGtid);
        final String expectedReplicaMode = newPrimary.equals(FIXED_CONNECTOR_HOST) ? "Slave_Pos" : "Current_Pos";
        assertHealthyReplica(newPrimary, oldPrimary, 0, expectedReplicaMode);

        removeReplicaConfiguration(newPrimary);
        execute(newPrimary, "SET GLOBAL read_only=OFF");
        assertThat(readOnly(newPrimary)).isFalse();
        configureReplica(oldPrimary, newPrimary, 0);
        awaitGtidContainment(oldPrimary, gtidBinlogPosition(newPrimary));
        assertHealthyReplica(oldPrimary, newPrimary, 0, "Current_Pos");
    }

    private void restoreOriginalTopology() throws SQLException {
        execute(ORIGINAL_PRIMARY_HOST, "SET GLOBAL read_only=ON");
        execute(FIXED_CONNECTOR_HOST, "SET GLOBAL read_only=ON");

        final String fixedHostGtid = gtidBinlogPosition(FIXED_CONNECTOR_HOST);
        if (!isContainedWithin(fixedHostGtid, gtidBinlogPosition(ORIGINAL_PRIMARY_HOST))) {
            configureReplica(ORIGINAL_PRIMARY_HOST, FIXED_CONNECTOR_HOST, 0);
            awaitGtidContainment(ORIGINAL_PRIMARY_HOST, fixedHostGtid);
        }
        assertThat(isContainedWithin(fixedHostGtid, gtidBinlogPosition(ORIGINAL_PRIMARY_HOST))).isTrue();

        removeReplicaConfiguration(ORIGINAL_PRIMARY_HOST);
        execute(ORIGINAL_PRIMARY_HOST, "SET GLOBAL read_only=OFF");
        configureReplica(FIXED_CONNECTOR_HOST, ORIGINAL_PRIMARY_HOST, 0);
        awaitGtidContainment(FIXED_CONNECTOR_HOST, gtidBinlogPosition(ORIGINAL_PRIMARY_HOST));

        execute(ORIGINAL_PRIMARY_HOST, "DROP DATABASE IF EXISTS `" + DATABASE_NAME + "`");
        awaitSchema(FIXED_CONNECTOR_HOST, false);
        restoreReplicaModeAndDelay(FIXED_CONNECTOR_HOST);

        assertThat(readOnly(ORIGINAL_PRIMARY_HOST)).isFalse();
        assertThat(readOnly(FIXED_CONNECTOR_HOST)).isTrue();
        assertHealthyReplica(FIXED_CONNECTOR_HOST, ORIGINAL_PRIMARY_HOST, 300, "Slave_Pos");
        assertThat(schemaExists(ORIGINAL_PRIMARY_HOST)).isFalse();
        assertThat(schemaExists(FIXED_CONNECTOR_HOST)).isFalse();
        assertThat(isContainedWithin(gtidBinlogPosition(ORIGINAL_PRIMARY_HOST), gtidBinlogPosition(FIXED_CONNECTOR_HOST))).isTrue();
    }

    private void configureReplica(String replicaHost, String primaryHost, int delay) throws SQLException {
        removeReplicaConfiguration(replicaHost);
        execute(replicaHost,
                "CHANGE MASTER TO "
                        + "MASTER_HOST='" + sqlLiteral(primaryHost) + "', "
                        + "MASTER_PORT=3306, "
                        + "MASTER_USER='root', "
                        + "MASTER_PASSWORD='" + sqlLiteral(password) + "', "
                        + "MASTER_USE_GTID=current_pos, "
                        + "MASTER_CONNECT_RETRY=5, "
                        + "MASTER_SSL=1, "
                        + "MASTER_SSL_VERIFY_SERVER_CERT=1, "
                        + "MASTER_DELAY=" + delay,
                "START SLAVE");
        awaitReplicaHealthy(replicaHost, primaryHost, delay, "Current_Pos");
    }

    private void removeReplicaConfiguration(String host) throws SQLException {
        if (slaveStatus(host) != null) {
            execute(host, "STOP SLAVE", "RESET SLAVE ALL");
        }
    }

    private void setReplicaDelay(String host, int delay) throws SQLException {
        execute(host, "STOP SLAVE", "CHANGE MASTER TO MASTER_DELAY=" + delay, "START SLAVE");
        awaitReplicaHealthy(host, null, delay, "Slave_Pos");
    }

    private void restoreReplicaModeAndDelay(String host) throws SQLException {
        execute(host, "STOP SLAVE", "CHANGE MASTER TO MASTER_USE_GTID=slave_pos, MASTER_DELAY=300", "START SLAVE");
        awaitReplicaHealthy(host, ORIGINAL_PRIMARY_HOST, 300, "Slave_Pos");
    }

    private void waitForGtid(String host, String gtid) throws SQLException {
        assertThat(queryInt(host, "SELECT MASTER_GTID_WAIT('" + sqlLiteral(gtid) + "',60)"))
                .as("%s must apply GTID %s", host, gtid)
                .isZero();
    }

    private void awaitGtidContainment(String host, String requiredGtid) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            try {
                assertThat(isContainedWithin(requiredGtid, gtidBinlogPosition(host)))
                        .as("%s binlog GTID must contain %s", host, requiredGtid)
                        .isTrue();
            }
            catch (SQLException e) {
                throw new AssertionError(e);
            }
        });
    }

    private void awaitReplicaHealthy(String replicaHost, String primaryHost, int delay, String usingGtid) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            try {
                assertHealthyReplica(replicaHost, primaryHost, delay, usingGtid);
            }
            catch (SQLException e) {
                throw new AssertionError(e);
            }
        });
    }

    private void assertHealthyReplica(String host, String expectedPrimary, int expectedDelay, String expectedUsingGtid) throws SQLException {
        final SlaveStatus status = slaveStatus(host);
        assertThat(status).isNotNull();
        assertThat(status.ioRunning()).isEqualTo("Yes");
        assertThat(status.sqlRunning()).isEqualTo("Yes");
        assertThat(status.lastIoErrno()).isZero();
        assertThat(status.lastSqlErrno()).isZero();
        assertThat(status.usingGtid()).isEqualTo(expectedUsingGtid);
        assertThat(status.delay()).isEqualTo(expectedDelay);
        if (expectedPrimary != null) {
            assertThat(status.masterHost()).isEqualTo(expectedPrimary);
        }
    }

    private SlaveStatus slaveStatus(String host) throws SQLException {
        try (Connection connection = connect(host);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW SLAVE STATUS")) {
            if (!resultSet.next()) {
                return null;
            }
            return new SlaveStatus(
                    resultSet.getString("Master_Host"),
                    resultSet.getString("Slave_IO_Running"),
                    resultSet.getString("Slave_SQL_Running"),
                    resultSet.getInt("Last_IO_Errno"),
                    resultSet.getInt("Last_SQL_Errno"),
                    resultSet.getString("Using_Gtid"),
                    resultSet.getInt("SQL_Delay"));
        }
    }

    private SourceRecord consumeNextRecord() throws InterruptedException {
        return consumeRecordsByTopic(1).recordsForTopic(TOPIC_NAME).get(0);
    }

    private Map<String, ?> assertRecord(SourceRecord record, int id, String marker) {
        final Struct value = (Struct) record.value();
        assertThat(value.getString(Envelope.FieldName.OPERATION)).isEqualTo(Envelope.Operation.CREATE.code());
        final Struct after = value.getStruct(Envelope.FieldName.AFTER);
        assertThat(after.getInt32("id")).isEqualTo(id);
        assertThat(after.getString("marker")).isEqualTo(marker);
        assertThat(record.sourceOffset()).containsKeys(
                BinlogOffsetContext.GTID_SET_KEY,
                BinlogOffsetContext.LAST_BINLOG_EVENT_TIMESTAMP_KEY);
        return record.sourceOffset();
    }

    private void assertOffsetProgression(Map<String, ?> earlier, Map<String, ?> later) {
        assertThat(isContainedWithin(
                (String) earlier.get(BinlogOffsetContext.GTID_SET_KEY),
                (String) later.get(BinlogOffsetContext.GTID_SET_KEY))).isTrue();
        assertThat(((Number) later.get(BinlogOffsetContext.LAST_BINLOG_EVENT_TIMESTAMP_KEY)).longValue())
                .isGreaterThanOrEqualTo(((Number) earlier.get(BinlogOffsetContext.LAST_BINLOG_EVENT_TIMESTAMP_KEY)).longValue());
    }

    private boolean isContainedWithin(String required, String available) {
        return gtidSetFactory.createGtidSet(required).isContainedWithin(gtidSetFactory.createGtidSet(available));
    }

    private Configuration connectorConfig() {
        return Configuration.create()
                .with(BinlogConnectorConfig.HOSTNAME, FIXED_CONNECTOR_HOST)
                .with(BinlogConnectorConfig.PORT, 3306)
                .with(BinlogConnectorConfig.USER, "root")
                .with(BinlogConnectorConfig.PASSWORD, password)
                .with(BinlogConnectorConfig.SERVER_ID, 54002)
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

    private boolean readOnly(String host) throws SQLException {
        return queryInt(host, "SELECT @@global.read_only") == 1;
    }

    private String gtidBinlogPosition(String host) throws SQLException {
        return queryString(host, "SELECT @@global.gtid_binlog_pos");
    }

    private int nonReplicationClientCount(String host) throws SQLException {
        return queryInt(host, "SELECT COUNT(*) FROM information_schema.processlist "
                + "WHERE ID<>CONNECTION_ID() AND COMMAND NOT IN ('Binlog Dump','Daemon')");
    }

    private boolean schemaExists(String host) throws SQLException {
        return queryInt(host, "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + DATABASE_NAME + "'") == 1;
    }

    private void awaitSchema(String host, boolean expected) {
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            try {
                assertThat(schemaExists(host)).isEqualTo(expected);
            }
            catch (SQLException e) {
                throw new AssertionError(e);
            }
        });
    }

    private void awaitRowCount(String host, int expected) {
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            try {
                assertThat(rowCount(host)).isEqualTo(expected);
            }
            catch (SQLException e) {
                throw new AssertionError(e);
            }
        });
    }

    private int rowCount(String host) throws SQLException {
        return queryInt(host, "SELECT COUNT(*) FROM `" + DATABASE_NAME + "`.`" + TABLE_NAME + "`");
    }

    private int queryInt(String host, String sql) throws SQLException {
        try (Connection connection = connect(host);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private String queryString(String host, String sql) throws SQLException {
        try (Connection connection = connect(host);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
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

    private String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private record SlaveStatus(String masterHost, String ioRunning, String sqlRunning, int lastIoErrno,
            int lastSqlErrno, String usingGtid, int delay) {
    }
}
