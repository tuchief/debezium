/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.debezium.storage.kafka.history.KafkaSchemaHistory;

@SuppressWarnings("unchecked")
public class KafkaSchemaHistoryBufferingTest {

    @AfterEach
    public void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    public void shouldDrainAtBatchThresholdAndWhenBufferingStops() throws Exception {
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        Future<RecordMetadata> first = completedFuture();
        Future<RecordMetadata> second = completedFuture();
        Future<RecordMetadata> third = completedFuture();
        when(producer.send(any())).thenReturn(first).thenReturn(second).thenReturn(third);
        TestKafkaSchemaHistory history = historyWith(producer, 2);

        history.startBuffering();
        history.store(record(1));
        verify(producer, times(0)).flush();
        history.store(record(2));
        verify(producer, times(1)).flush();
        verify(first).get();
        verify(second).get();

        history.store(record(3));
        verify(producer, times(1)).flush();
        history.stopBuffering();
        verify(producer, times(2)).flush();
        verify(third).get();
    }

    @Test
    public void shouldKeepNonBufferedWritesSynchronous() throws Exception {
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        Future<RecordMetadata> future = completedFuture();
        when(producer.send(any())).thenReturn(future);
        TestKafkaSchemaHistory history = historyWith(producer, 10);

        history.store(record(1));

        verify(producer).flush();
        verify(future).get();
    }

    @Test
    public void shouldPropagateBufferedWriteFailureAndDisableBuffering() throws Exception {
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        Future<RecordMetadata> failed = mock(Future.class);
        Future<RecordMetadata> succeeded = completedFuture();
        when(failed.get()).thenThrow(new ExecutionException(new IllegalStateException("write failed")));
        when(producer.send(any())).thenReturn(failed).thenReturn(succeeded);
        TestKafkaSchemaHistory history = historyWith(producer, 10);

        history.startBuffering();
        history.store(record(1));
        assertThatThrownBy(history::stopBuffering).isInstanceOf(SchemaHistoryException.class);

        history.store(record(2));
        verify(succeeded).get();
    }

    @Test
    public void shouldRestoreInterruptStatusWhenDrainIsInterrupted() throws Exception {
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        Future<RecordMetadata> interrupted = mock(Future.class);
        when(interrupted.get()).thenThrow(new InterruptedException("interrupted"));
        when(producer.send(any())).thenReturn(interrupted);
        TestKafkaSchemaHistory history = historyWith(producer, 10);

        history.startBuffering();
        history.store(record(1));
        assertThatThrownBy(history::stopBuffering).isInstanceOf(SchemaHistoryException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static TestKafkaSchemaHistory historyWith(KafkaProducer<String, String> producer, int batchSize) throws Exception {
        TestKafkaSchemaHistory history = new TestKafkaSchemaHistory();
        setField(history, "producer", producer);
        setField(history, "topicName", "history-topic");
        setField(history, "bufferBatchSize", batchSize);
        return history;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = KafkaSchemaHistory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static Future<RecordMetadata> completedFuture() throws Exception {
        Future<RecordMetadata> future = mock(Future.class);
        when(future.get()).thenReturn(null);
        return future;
    }

    private static HistoryRecord record(int position) {
        return new HistoryRecord(Collections.singletonMap("server", "test"),
                Collections.singletonMap("position", position), "db", null,
                "CREATE TABLE t" + position + " (id INT)", null, Instant.EPOCH);
    }

    private static class TestKafkaSchemaHistory extends KafkaSchemaHistory {
        void store(HistoryRecord record) {
            storeRecord(record);
        }
    }
}
