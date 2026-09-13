/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.debezium.config.Configuration;
import io.debezium.connector.binlog.AbstractBinlogConnectorIT;
import io.debezium.connector.binlog.BinlogConnectorConfig;
import io.debezium.connector.binlog.util.BinlogTestConnection;
import io.debezium.connector.binlog.util.TestHelper;
import io.debezium.connector.binlog.util.UniqueDatabase;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.schema.SchemaFactory;

public class SystemVersionedTableIT extends AbstractBinlogConnectorIT<MariaDbConnector> implements MariaDbCommon {

    private static final Path SCHEMA_HISTORY_PATH = Files.createTestingPath("file-schema-history-system-versioned.txt").toAbsolutePath();
    private final UniqueDatabase DATABASE = TestHelper.getUniqueDatabase("systemversionedit", "system_versioned_table_test")
            .withDbHistoryPath(SCHEMA_HISTORY_PATH);

    @Before
    public void beforeEach() {
        stopConnector();
        DATABASE.createAndInitialize();
        initializeConnectorTestFramework();
        Files.delete(SCHEMA_HISTORY_PATH);
    }

    @After
    public void afterEach() {
        try {
            stopConnector();
        }
        finally {
            Files.delete(SCHEMA_HISTORY_PATH);
        }
    }

    @Test
    public void shouldCaptureExplicitSystemVersioningColumnsInSnapshotAndStreaming() throws Exception {
        Configuration config = DATABASE.defaultConfig()
                .with(BinlogConnectorConfig.TABLE_INCLUDE_LIST, DATABASE.qualifiedTableName("test_system_versioned"))
                .build();

        start(getConnectorClass(), config);

        SourceRecord snapshot = consumeSkippingSchemaChanges(1).get(0);
        Struct snapshotAfter = ((Struct) snapshot.value()).getStruct("after");
        assertThat(snapshotAfter.getInt32("id")).isEqualTo(1);
        assertThat(snapshotAfter.getString("value")).isEqualTo("before");
        assertThat(snapshotAfter.schema().field("row_start")).isNotNull();
        assertThat(snapshotAfter.schema().field("row_end")).isNotNull();

        try (BinlogTestConnection db = getTestDatabaseConnection(DATABASE.getDatabaseName());
                JdbcConnection connection = db.connect()) {
            connection.execute("UPDATE test_system_versioned SET value = 'after' WHERE id = 1");
        }

        SourceRecord update = consumeSkippingSchemaChanges(1).get(0);
        Struct updateAfter = ((Struct) update.value()).getStruct("after");
        assertThat(updateAfter.getString("value")).isEqualTo("after");
    }

    private List<SourceRecord> consumeSkippingSchemaChanges(int numberOfRecords) throws InterruptedException {
        List<SourceRecord> recordList = new ArrayList<>();
        consumeRecordsUntil((i, r) -> recordList.size() == numberOfRecords, (i, r) -> "", 3,
                r -> {
                    if (!((Struct) r.value()).schema().name().endsWith(SchemaFactory.SCHEMA_CHANGE_VALUE)) {
                        recordList.add(r);
                    }
                }, true);
        assertThat(recordList).hasSize(numberOfRecords);
        return recordList;
    }
}
