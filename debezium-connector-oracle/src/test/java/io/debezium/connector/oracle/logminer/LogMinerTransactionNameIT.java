/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.oracle.logminer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.debezium.config.Configuration;
import io.debezium.connector.oracle.OracleConnection;
import io.debezium.connector.oracle.OracleConnectorConfig;
import io.debezium.connector.oracle.RedoThreadState;
import io.debezium.connector.oracle.Scn;
import io.debezium.connector.oracle.logminer.buffered.BufferedLogMinerQueryBuilder;
import io.debezium.connector.oracle.logminer.events.EventType;
import io.debezium.connector.oracle.logminer.events.LogMinerEventRow;
import io.debezium.connector.oracle.util.TestHelper;
import io.debezium.jdbc.JdbcConfiguration;

public class LogMinerTransactionNameIT {

    private static final String TABLE_NAME = "DBZ_RPS_TXNAME_A09D99";
    private static final String TRANSACTION_NAME = "RPS_ORIGIN_A09D99";
    private static final String SCHEMA_NAME = System.getProperty("database.user", "DATAKNOWN").toUpperCase(Locale.ROOT);

    private OracleConnection connection;
    private OracleConnection writerConnection;
    private OracleConnectorConfig connectorConfig;

    @BeforeEach
    void beforeEach() throws SQLException {
        final String password = System.getenv("RPS_ORACLE_PASSWORD");
        assertThat(password).as("RPS_ORACLE_PASSWORD must be provided for this external acceptance test").isNotBlank();
        final Configuration config = TestHelper.defaultConfig()
                .with("database.password", password)
                .with("schema.include.list", SCHEMA_NAME)
                .with("log.mining.query.filter.mode", "in")
                .with(OracleConnectorConfig.TABLE_INCLUDE_LIST, SCHEMA_NAME + "\\." + TABLE_NAME)
                .build();
        connectorConfig = new OracleConnectorConfig(config);
        final JdbcConfiguration jdbcConfig = JdbcConfiguration.adapt(config.subset("database.", true));
        final String writerUrl = System.getProperty("rps.oracle.writer.url");
        final JdbcConfiguration writerJdbcConfig = writerUrl == null
                ? jdbcConfig
                : JdbcConfiguration.adapt(config.edit().with(OracleConnectorConfig.URL, writerUrl).build().subset("database.", true));
        connection = new OracleConnection(jdbcConfig, true);
        writerConnection = new OracleConnection(writerJdbcConfig, true);
        connection.setAutoCommit(false);
        writerConnection.setAutoCommit(true);
        TestHelper.dropTable(connection, TABLE_NAME);
        connection.execute("CREATE TABLE " + TABLE_NAME + " (ID NUMBER(9) PRIMARY KEY, DATA CLOB, TEXT_DATA VARCHAR2(100))");
        connection.execute("ALTER TABLE " + TABLE_NAME + " ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS");
    }

    @AfterEach
    void afterEach() throws SQLException {
        try {
            if (writerConnection != null) {
                try {
                    writerConnection.rollback();
                }
                finally {
                    writerConnection.close();
                }
            }
        }
        finally {
            if (connection != null) {
                try {
                    TestHelper.dropTable(connection, TABLE_NAME);
                }
                finally {
                    connection.close();
                }
            }
        }
    }

    @Test
    void shouldReadLastRedoStateColumnsFromSupportedOracleVersion() throws Exception {
        final RedoThreadState state = connection.getRedoThreadState();

        assertThat(state.getThreads()).isNotEmpty().anySatisfy(thread -> {
            assertThat(thread.getLastRedoSequenceNumber()).isNotNull();
            assertThat(thread.getLastRedoBlock()).isNotNull();
            assertThat(thread.getLastRedoScn()).isNotNull();
            assertThat(thread.getLastRedoTime()).isNotNull();
        });
    }

    @Test
    void shouldReadBothRacRedoThreadsWithoutDuplicateCurrentLogs() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("rps.oracle.rac"));

        final RedoThreadState state = connection.getRedoThreadState();
        assertThat(state.getThreads()).extracting(RedoThreadState.RedoThread::getThreadId)
                .containsExactlyInAnyOrder(1, 2);

        final List<LogFile> logs = new LogFileCollector(connectorConfig, connection)
                .getLogs(currentScn()).logFiles();
        assertThat(logs).extracting(LogFile::getThread).contains(1, 2);
        assertThat(logs.stream()
                .map(log -> log.getThread() + ":" + log.getSequence())
                .collect(Collectors.toList())).doesNotHaveDuplicates();
    }

    @Test
    void shouldReadTransactionNameFromSupportedOracleLogMiner() throws Exception {
        final Scn startScn = currentScn();
        writeNamedTransaction(TRANSACTION_NAME, 1, "事务名验证");
        final Scn endScn = currentScn();

        final List<LogMinerEventRow> rows = mine(startScn, endScn);
        final LogMinerEventRow matchingRow = findTableInsert(rows);
        assertThat(matchingRow.getTransactionName()).isEqualTo(TRANSACTION_NAME);
        assertThat(matchingRow.getRedoSql()).contains("事务名验证");
        final String expectedWriterThread = System.getProperty("rps.oracle.expected.writer.thread");
        if (expectedWriterThread != null) {
            assertThat(matchingRow.getThread()).isEqualTo(Integer.parseInt(expectedWriterThread));
        }

        final LogMinerEventRow commitRow = findCommit(rows, matchingRow.getTransactionId());
        assertThat(commitRow.getTransactionName()).isEqualTo(TRANSACTION_NAME);
    }

    @Test
    void shouldDocumentTransactionNameAcrossRestartedMiningWindows() throws Exception {
        final String splitTransactionName = TRANSACTION_NAME + "_SPLIT";
        final Scn startScn = currentScn();

        writerConnection.setAutoCommit(false);
        writerConnection.executeWithoutCommitting("SET TRANSACTION NAME '" + splitTransactionName + "'");
        writerConnection.executeWithoutCommitting("INSERT INTO " + TABLE_NAME + " (ID, DATA) VALUES (2, 'cross-window-check')");
        final Scn splitScn = currentScn();
        writerConnection.commit();

        final List<LogMinerEventRow> firstWindow = mine(startScn, splitScn);
        final LogMinerEventRow insertRow = findTableInsert(firstWindow);
        assertThat(insertRow.getTransactionName()).isEqualTo(splitTransactionName);

        final LogMinerEventRow commitRow = findCommitEventually(splitScn, insertRow.getTransactionId());
        if (connection.getOracleVersion().getMajor() >= 11) {
            assertThat(commitRow.getTransactionName()).isNull();
        }
        else {
            assertThat(commitRow.getTransactionName()).isEqualTo(splitTransactionName);
        }
    }

    @Test
    void shouldRetainTransactionNameForLobSavepointRollback() throws Exception {
        final String lobTransactionName = TRANSACTION_NAME + "_LOB";
        final Scn startScn = currentScn();

        writerConnection.setAutoCommit(false);
        writerConnection.executeWithoutCommitting("SET TRANSACTION NAME '" + lobTransactionName + "'");
        writerConnection.executeWithoutCommitting(
                "INSERT INTO " + TABLE_NAME + " (ID, DATA) VALUES (3, TO_CLOB('before-savepoint'))");
        writerConnection.executeWithoutCommitting("SAVEPOINT rps_lob_savepoint");
        writerConnection.executeWithoutCommitting("UPDATE " + TABLE_NAME + " SET DATA = " +
                "TO_CLOB(RPAD('X', 4000, 'X')) || RPAD('Y', 1000, 'Y') WHERE ID = 3");
        writerConnection.executeWithoutCommitting("ROLLBACK TO SAVEPOINT rps_lob_savepoint");
        writerConnection.commit();
        final Scn endScn = currentScn();

        final String committedValue = writerConnection.queryAndMap(
                "SELECT DATA FROM " + TABLE_NAME + " WHERE ID = 3",
                resultSet -> {
                    assertThat(resultSet.next()).isTrue();
                    return resultSet.getString(1);
                });
        assertThat(committedValue).isEqualTo("before-savepoint");

        final List<LogMinerEventRow> rows = mine(startScn, endScn);
        final LogMinerEventRow insertRow = findTableInsert(rows);
        assertThat(insertRow.getTransactionName()).isEqualTo(lobTransactionName);
        assertThat(rows).anyMatch(row -> insertRow.getTransactionId().equals(row.getTransactionId()) && row.isRollbackFlag());

        final LogMinerEventRow commitRow = findCommit(rows, insertRow.getTransactionId());
        assertThat(commitRow.getTransactionName()).isEqualTo(lobTransactionName);
    }

    private List<LogMinerEventRow> mine(Scn startScn, Scn endScn) throws Exception {
        final LogMinerSessionContext session = new LogMinerSessionContext(
                connection, true, OracleConnectorConfig.LogMiningStrategy.ONLINE_CATALOG, null);
        try {
            session.startSession(startScn, endScn, false);
            final boolean clientIdAvailable = connection.getOracleVersion().getMajor() >= 11;
            final String query = new BufferedLogMinerQueryBuilder(connectorConfig, clientIdAvailable).getQuery();
            try (PreparedStatement statement = connection.connection().prepareStatement(query)) {
                statement.setString(1, startScn.toString());
                statement.setString(2, endScn.toString());

                final List<LogMinerEventRow> rows = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    final LogMinerColumnIndexes indexes = LogMinerColumnIndexes.fromConfig(connectorConfig, clientIdAvailable);
                    while (resultSet.next()) {
                        rows.add(LogMinerEventRow.fromResultSet(resultSet, null, indexes));
                    }
                }
                return rows;
            }
        }
        finally {
            session.endMiningSession();
        }
    }

    private LogMinerEventRow findTableInsert(List<LogMinerEventRow> rows) {
        final LogMinerEventRow row = rows.stream()
                .filter(event -> TABLE_NAME.equals(event.getTableName()))
                .filter(event -> EventType.INSERT.equals(event.getEventType()))
                .findFirst()
                .orElse(null);
        assertThat(row).isNotNull();
        return row;
    }

    private LogMinerEventRow findCommit(List<LogMinerEventRow> rows, String transactionId) {
        final LogMinerEventRow row = rows.stream()
                .filter(event -> EventType.COMMIT.equals(event.getEventType()))
                .filter(event -> transactionId.equals(event.getTransactionId()))
                .findFirst()
                .orElse(null);
        assertThat(row).isNotNull();
        return row;
    }

    private LogMinerEventRow findCommitEventually(Scn startScn, String transactionId) {
        final AtomicReference<LogMinerEventRow> commitRow = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            commitRow.set(findCommit(mine(startScn, currentScn()), transactionId));
        });
        return commitRow.get();
    }

    private void writeNamedTransaction(String transactionName, int id, String data) throws SQLException {
        writerConnection.setAutoCommit(false);
        writerConnection.executeWithoutCommitting("SET TRANSACTION NAME '" + transactionName + "'");
        writerConnection.executeWithoutCommitting(
                "INSERT INTO " + TABLE_NAME + " (ID, DATA, TEXT_DATA) VALUES (" + id + ", TO_CLOB('" + data + "'), '" + data + "')");
        writerConnection.commit();
    }

    private Scn currentScn() throws SQLException {
        return connection.queryAndMap("SELECT CURRENT_SCN FROM V$DATABASE", resultSet -> {
            assertThat(resultSet.next()).isTrue();
            return Scn.valueOf(resultSet.getString(1));
        });
    }
}
