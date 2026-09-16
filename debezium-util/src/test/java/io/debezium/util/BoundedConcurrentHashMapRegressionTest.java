/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.debezium.util.BoundedConcurrentHashMap.Eviction;

class BoundedConcurrentHashMapRegressionTest {

    @Test
    void shouldDrainAccessQueueOnRepeatedPutHits() {
        for (Eviction eviction : new Eviction[]{ Eviction.LRU, Eviction.LIRS }) {
            BoundedConcurrentHashMap<Integer, Integer> map = new BoundedConcurrentHashMap<>(64, 1, eviction);
            map.put(1, 0);

            for (int i = 1; i <= 1_000; i++) {
                assertThat(map.put(1, i)).isEqualTo(i - 1);
            }

            assertThat(map).containsOnlyKeys(1);
            assertThat(map.segments[0].eviction.thresholdExpired())
                    .as("%s access queue should be drained during repeated put hits", eviction)
                    .isFalse();
            assertThat(map.get(1)).isEqualTo(1_000);
        }
    }

    @Test
    void shouldKeepAccessQueueCounterConsistentOnRemoveAndClear() throws Exception {
        for (Eviction eviction : new Eviction[]{ Eviction.LRU, Eviction.LIRS }) {
            BoundedConcurrentHashMap<Integer, Integer> map = new BoundedConcurrentHashMap<>(64, 1, eviction);
            map.put(1, 1);
            for (int i = 0; i < 20; i++) {
                map.put(1, i);
            }

            map.remove(1);
            assertAccessQueueState(map.segments[0].eviction, 0);

            map.put(2, 2);
            for (int i = 0; i < 20; i++) {
                map.put(2, i);
            }
            map.clear();

            assertThat(map).isEmpty();
            assertAccessQueueState(map.segments[0].eviction, 0);
        }
    }

    private static void assertAccessQueueState(Object evictionPolicy, int expectedSize) throws Exception {
        Field queueField = evictionPolicy.getClass().getDeclaredField("accessQueue");
        queueField.setAccessible(true);
        ConcurrentLinkedQueue<?> queue = (ConcurrentLinkedQueue<?>) queueField.get(evictionPolicy);

        Field counterField = evictionPolicy.getClass().getDeclaredField("accessQueueSize");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(evictionPolicy);

        assertThat(queue).hasSize(expectedSize);
        assertThat(counter.get()).isEqualTo(expectedSize);
    }
}
