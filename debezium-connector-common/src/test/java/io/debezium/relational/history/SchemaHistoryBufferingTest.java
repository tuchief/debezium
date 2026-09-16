/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

public class SchemaHistoryBufferingTest {

    @Test
    public void shouldPreservePrimaryFailureAndSuppressDrainFailure() {
        IllegalStateException primary = new IllegalStateException("snapshot failed");
        SchemaHistoryException drain = new SchemaHistoryException("drain failed");
        TestSchemaHistory history = new TestSchemaHistory(drain);

        Throwable thrown = catchThrowable(() -> {
            try (SchemaHistory.BufferingScope ignored = history.buffering()) {
                throw primary;
            }
        });

        assertThat(thrown).isSameAs(primary);
        assertThat(thrown.getSuppressed()).containsExactly(drain);
        assertThat(history.starts).isEqualTo(1);
        assertThat(history.stops).isEqualTo(1);
    }

    @Test
    public void shouldPropagateDrainFailureWhenBodySucceeds() {
        SchemaHistoryException drain = new SchemaHistoryException("drain failed");
        TestSchemaHistory history = new TestSchemaHistory(drain);

        Throwable thrown = catchThrowable(() -> {
            try (SchemaHistory.BufferingScope ignored = history.buffering()) {
                // no-op
            }
        });

        assertThat(thrown).isSameAs(drain);
    }

    private static class TestSchemaHistory extends AbstractSchemaHistory {
        private final SchemaHistoryException stopFailure;
        private int starts;
        private int stops;

        private TestSchemaHistory(SchemaHistoryException stopFailure) {
            this.stopFailure = stopFailure;
        }

        @Override
        public void startBuffering() {
            starts++;
        }

        @Override
        public void stopBuffering() {
            stops++;
            throw stopFailure;
        }

        @Override
        protected void storeRecord(HistoryRecord record) {
        }

        @Override
        protected void recoverRecords(Consumer<HistoryRecord> records) {
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean storageExists() {
            return true;
        }
    }
}
