/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.oracle;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import java.time.Duration;

import org.apache.kafka.connect.errors.RetriableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.debezium.jdbc.JdbcConfiguration;
import io.debezium.jdbc.JdbcConnection;

public class OracleConnectionTest {

    private Statement statement;
    private JdbcConfiguration jdbcConfiguration;
    private Connection connection;
    private JdbcConnection.ConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() throws Exception {

        jdbcConfiguration = mock(JdbcConfiguration.class);
        when(jdbcConfiguration.getQueryTimeout()).thenReturn(Duration.ZERO);
        connectionFactory = mock(JdbcConnection.ConnectionFactory.class);
        connection = mock(Connection.class);
        doNothing().when(connection).setAutoCommit(anyBoolean());
        statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.getConnection()).thenReturn(connection);
        when(connectionFactory.connect(jdbcConfiguration)).thenReturn(connection);

    }

    @Test
    void whenOracleConnectionGetSQLRecoverableExceptionThenARetriableExceptionWillBeThrown() throws SQLException {

        when(statement.executeQuery(any()))
                .thenThrow(new SQLRecoverableException("IO Error: The Network Adapter could not establish the connection (CONNECTION_ID=u/VErjYySfO0HgLtwdCuTQ==)"));

        when(connection.getMetaData())
                .thenThrow(new SQLRecoverableException("IO Error: The Network Adapter could not establish the connection (CONNECTION_ID=u/VErjYySfO0HgLtwdCuTQ==)"));

        assertThrows(RetriableException.class, () -> {
            try (OracleConnection connection = new OracleConnection(jdbcConfiguration, connectionFactory, true)) {
                // Force a connection call to the database.
                connection.getOracleVersion();
            }
        });
    }

    @Test
    void shouldReadLastRedoStateColumnsOnOracle10g() throws Exception {
        final ResultSet resultSet = mock(ResultSet.class);
        final OracleDatabaseVersion oracle10g = mock(OracleDatabaseVersion.class);
        when(oracle10g.getMajor()).thenReturn(10);
        when(statement.executeQuery("SELECT * FROM V$THREAD")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getInt("THREAD#")).thenReturn(1);
        when(resultSet.getString("STATUS")).thenReturn("OPEN");
        when(resultSet.getString("ENABLED")).thenReturn("PUBLIC");
        when(resultSet.getString("INSTANCE")).thenReturn("ORCL");

        try (OracleConnection oracleConnection = new OracleConnection(jdbcConfiguration, connectionFactory, true) {
            @Override
            public OracleDatabaseVersion getOracleVersion() {
                return oracle10g;
            }
        }) {
            oracleConnection.getRedoThreadState();
        }

        verify(resultSet).getLong("LAST_REDO_SEQUENCE#");
        verify(resultSet).getLong("LAST_REDO_BLOCK");
        verify(resultSet).getString("LAST_REDO_CHANGE#");
    }
}
