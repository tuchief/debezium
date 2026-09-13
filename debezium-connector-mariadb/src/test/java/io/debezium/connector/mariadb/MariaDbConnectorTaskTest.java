/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.apache.kafka.connect.source.SourceRecord;
import org.junit.Test;

import io.debezium.pipeline.DataChangeEvent;

public class MariaDbConnectorTaskTest {

    @Test
    public void shouldConvertPolledEventsInOrderToMutableList() {
        SourceRecord first = record("first");
        SourceRecord second = record("second");

        List<SourceRecord> converted = MariaDbConnectorTask.toSourceRecords(
                List.of(new DataChangeEvent(first), new DataChangeEvent(second)));

        assertThat(converted).containsExactly(first, second);
        assertThat(converted.get(0)).isSameAs(first);
        converted.add(record("third"));
        assertThat(converted).hasSize(3);
        assertThat(MariaDbConnectorTask.toSourceRecords(Collections.emptyList())).isEmpty();
    }

    private static SourceRecord record(String value) {
        return new SourceRecord(Collections.emptyMap(), Collections.emptyMap(), "topic", null, value);
    }
}
