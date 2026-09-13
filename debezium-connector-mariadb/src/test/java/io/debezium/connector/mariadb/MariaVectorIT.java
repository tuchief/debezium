/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static io.debezium.junit.EqualityCheck.LESS_THAN;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.apache.kafka.connect.data.Struct;
import org.junit.Test;

import io.debezium.connector.binlog.BinlogConnectorConfig;
import io.debezium.connector.binlog.BinlogVectorIT;
import io.debezium.connector.binlog.util.BinlogTestConnection;
import io.debezium.data.vector.FloatVector;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.junit.SkipWhenDatabaseVersion;

@SkipWhenDatabaseVersion(check = LESS_THAN, major = 11, minor = 7, reason = "VECTOR datatype not added until MariaDB 11.7")
public class MariaVectorIT extends BinlogVectorIT<MariaDbConnector> implements MariaDbCommon {

    public MariaVectorIT() {
        super("maria_vector_test");
    }

    @Test
    public void shouldConsumeAllEventsFromDatabaseUsingStreaming() throws SQLException, InterruptedException {
        config = DATABASE.defaultConfig()
                .with(BinlogConnectorConfig.SNAPSHOT_MODE, BinlogConnectorConfig.SnapshotMode.NO_DATA)
                .build();

        start(getConnectorClass(), config);

        int numTables = 1;
        int numDdlRecords = numTables * 2 + 3;
        int numSetVariables = 1;
        var records = consumeRecordsByTopic(numDdlRecords + numSetVariables);

        try (BinlogTestConnection db = getTestDatabaseConnection(DATABASE.getDatabaseName());
                JdbcConnection connection = db.connect()) {
            connection.execute(
                    "INSERT INTO dbz_8157 VALUES (default, Vec_FromText('[10.1,10.2]'),Vec_FromText('[20.1,20.2]'),Vec_FromText('[30.1,30.2]'));");
        }
        records = consumeRecordsByTopic(1);

        assertThat(records).isNotNull();
        final var dataRecords = records.recordsForTopic(DATABASE.topicForTable("dbz_8157"));
        assertThat(dataRecords).hasSize(1);
        var record = dataRecords.get(0);
        var after = ((Struct) record.value()).getStruct("after");
        assertThat(after.schema().field("f_vector_null").schema().name()).isEqualTo(FloatVector.LOGICAL_NAME);
        assertThat(after.getArray("f_vector_null")).containsExactly(10.1f, 10.2f);
        assertThat(after.getArray("f_vector_default")).containsExactly(20.1f, 20.2f);
        assertThat(after.getArray("f_vector_cons")).containsExactly(30.1f, 30.2f);

        stopConnector();
    }
}
