/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.oracle.logminer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.debezium.connector.oracle.OracleConnection;
import io.debezium.connector.oracle.OracleConnectorConfig.LogMiningStrategy;
import io.debezium.connector.oracle.Scn;

class LogMinerSessionContextTest {

    private final OracleConnection connection = mock(OracleConnection.class);

    @Test
    void shouldFullyQualifyLogMinerPackageWhenAddingLogFile() throws Exception {
        final LogMinerSessionContext context = context(LogMiningStrategy.ONLINE_CATALOG, false);
        final LogFile logFile = LogFile.forRedo("/redo01.log", Scn.valueOf(1), Scn.valueOf(2), BigInteger.ONE, true, 1, 1024);

        context.addLogFiles(List.of(logFile));

        verify(connection).executeWithoutCommitting(
                "BEGIN sys.dbms_logmnr.add_logfile(LOGFILENAME => '/redo01.log', OPTIONS => SYS.DBMS_LOGMNR.ADDFILE); END;");
    }

    @Test
    void shouldFullyQualifyEveryLogMinerOptionWhenStartingSession() throws Exception {
        final LogMinerSessionContext context = context(LogMiningStrategy.ONLINE_CATALOG, true);

        context.startSession(Scn.valueOf(10), Scn.valueOf(20), true);

        verify(connection).executeWithoutCommitting("BEGIN sys.dbms_logmnr.start_logmnr(startScn => '10', endScn => '20', " +
                "options => SYS.DBMS_LOGMNR.DICT_FROM_ONLINE_CATALOG + SYS.DBMS_LOGMNR.CONTINUOUS_MINE + " +
                "SYS.DBMS_LOGMNR.COMMITTED_DATA_ONLY + SYS.DBMS_LOGMNR.NO_ROWID_IN_STMT); END;");
    }

    @Test
    void shouldFullyQualifyLogMinerDictionaryPackage() throws Exception {
        final LogMinerSessionContext context = context(LogMiningStrategy.CATALOG_IN_REDO, false);

        context.writeDataDictionaryToRedoLogs();

        verify(connection).executeWithoutCommitting(
                "BEGIN SYS.DBMS_LOGMNR_D.BUILD (options => SYS.DBMS_LOGMNR_D.STORE_IN_REDO_LOGS); END;");
    }

    private LogMinerSessionContext context(LogMiningStrategy strategy, boolean continuousMining) {
        return new LogMinerSessionContext(connection, continuousMining, strategy, null);
    }
}
