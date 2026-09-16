/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.QueryEventDataDeserializer;
import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;

class BinlogQueryEventStatusVariablesTest {

    @Test
    void shouldDeserializeStatusVariablesWithoutChangingQueryFields() throws Exception {
        byte[] statusVariables = { 0, 1, 2, 3, 4, 7, 52, 18 };
        byte[] payload = queryEventPayload(42, 3, 0, statusVariables, "inventory", "ALTER TABLE products ADD COLUMN c INT");

        QueryEventData event = new QueryEventDataDeserializer().deserialize(new ByteArrayInputStream(payload));

        assertThat(event.getThreadId()).isEqualTo(42);
        assertThat(event.getExecutionTime()).isEqualTo(3);
        assertThat(event.getErrorCode()).isZero();
        assertThat(event.getDatabase()).isEqualTo("inventory");
        assertThat(event.getSql()).isEqualTo("ALTER TABLE products ADD COLUMN c INT");
        assertThat(event.getStatusVariables()).containsExactly(statusVariables);
        assertThat(BinlogQueryEventStatusVariables.getLcTimeNames(event.getStatusVariables())).hasValue(0x1234);
    }

    @Test
    void shouldReadLcTimeNamesAfterLegacyCatalogWithTerminator() {
        assertThat(BinlogQueryEventStatusVariables.getLcTimeNames(
                new byte[]{ 2, 3, 's', 't', 'd', 0, 7, 5, 0 })).hasValue(5);
    }

    @Test
    void shouldReadLcTimeNamesAfterLaterStatusVariableTypes() {
        assertThat(BinlogQueryEventStatusVariables.getLcTimeNames(new byte[]{
                8, 1, 0,
                9, 0, 0, 0, 0, 0, 0, 0, 0,
                10, 0, 0, 0, 0,
                11, 1, 'u', 1, 'h',
                12, 2, 'a', 0, 'b', 0,
                13, 0, 0, 0,
                7, 42, 0
        })).hasValue(42);
    }

    @Test
    void shouldReturnEmptyLcTimeNamesForMissingMalformedOrUnknownStatusVariables() {
        assertThat(BinlogQueryEventStatusVariables.getLcTimeNames(null)).isEmpty();
        assertThat(BinlogQueryEventStatusVariables.getLcTimeNames(new byte[]{ 7, 1 })).isEmpty();
        assertThat(BinlogQueryEventStatusVariables.getLcTimeNames(new byte[]{ 99, 7, 1, 0 })).isEmpty();
    }

    private static byte[] queryEventPayload(long threadId, long executionTime, int errorCode, byte[] statusVariables,
                                            String database, String sql) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeLittleEndian(output, threadId, 4);
        writeLittleEndian(output, executionTime, 4);
        output.write(database.length());
        writeLittleEndian(output, errorCode, 2);
        writeLittleEndian(output, statusVariables.length, 2);
        output.writeBytes(statusVariables);
        output.writeBytes(database.getBytes(StandardCharsets.UTF_8));
        output.write(0);
        output.writeBytes(sql.getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private static void writeLittleEndian(ByteArrayOutputStream output, long value, int byteCount) {
        for (int i = 0; i < byteCount; i++) {
            output.write((int) (value >>> (i * 8)) & 0xff);
        }
    }
}
