/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.oracle.logminer.buffered.ehcache.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.debezium.connector.oracle.Scn;
import io.debezium.connector.oracle.logminer.buffered.ehcache.EhcacheTransaction;

class EhcacheTransactionSerializerTest {

    private final EhcacheTransactionSerializer serializer = new EhcacheTransactionSerializer(getClass().getClassLoader());

    @Test
    void shouldRoundTripTransactionName() throws Exception {
        final EhcacheTransaction transaction = new EhcacheTransaction(
                "010203", Scn.valueOf(100), Instant.parse("2026-09-15T00:00:00Z"), "DATAKNOWN", 1, 3, "client", "RPS_ORIGIN");

        final EhcacheTransaction restored = serializer.read(serializer.serialize(transaction));

        assertThat(restored.getTransactionName()).isEqualTo("RPS_ORIGIN");
        assertThat(restored.getNumberOfEvents()).isEqualTo(3);
    }

    @Test
    void shouldReadLegacyTransactionWithoutTransactionName() throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (SerializerOutputStream stream = new SerializerOutputStream(output)) {
            stream.writeString("010203");
            stream.writeScn(Scn.valueOf(100));
            stream.writeInstant(Instant.parse("2026-09-15T00:00:00Z"));
            stream.writeString("DATAKNOWN");
            stream.writeInt(1);
            stream.writeInt(3);
            stream.writeString("client");
        }

        final EhcacheTransaction restored = serializer.read(ByteBuffer.wrap(output.toByteArray()));

        assertThat(restored.getTransactionName()).isNull();
        assertThat(restored.getNumberOfEvents()).isEqualTo(3);
    }
}
