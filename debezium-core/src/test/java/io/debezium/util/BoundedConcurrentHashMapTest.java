/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.debezium.util.BoundedConcurrentHashMap.Eviction;

public class BoundedConcurrentHashMapTest {

    @Test
    public void shouldDrainAccessQueueOnPutHits() {
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
    public void shouldKeepAccessQueueCounterConsistentOnRemoveAndClear() throws Exception {
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

    @Test(timeout = 10000)
    public void shouldKeepAccessQueueCounterConsistentDuringConcurrentHitsAndClears() throws Exception {
        for (Eviction eviction : new Eviction[]{ Eviction.LRU, Eviction.LIRS }) {
            for (int round = 0; round < 10; round++) {
                BoundedConcurrentHashMap<Integer, Integer> map = new BoundedConcurrentHashMap<>(64, 1, eviction);
                map.put(1, 1);
                CountDownLatch start = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();

                Thread hits = new Thread(() -> runSafely(failure, () -> {
                    start.await();
                    for (int i = 0; i < 20_000; i++) {
                        map.get(1);
                        map.put(1, i);
                    }
                }));
                Thread clears = new Thread(() -> runSafely(failure, () -> {
                    start.await();
                    for (int i = 0; i < 2_000; i++) {
                        map.clear();
                        map.put(1, i);
                    }
                }));

                hits.start();
                clears.start();
                start.countDown();
                hits.join();
                clears.join();

                assertThat(failure.get()).isNull();
                assertAccessQueueCounterMatchesQueue(map.segments[0].eviction);
            }
        }
    }

    @Test(timeout = 10000)
    public void shouldBoundDrainWhileHitsContinue() throws Exception {
        for (Eviction eviction : new Eviction[]{ Eviction.LRU, Eviction.LIRS }) {
            BoundedConcurrentHashMap<Integer, Integer> map = new BoundedConcurrentHashMap<>(64, 1, eviction);
            map.put(1, 1);
            BoundedConcurrentHashMap.Segment<Integer, Integer> segment = map.segments[0];
            Object entry = firstEntry(segment);
            Method onEntryHit = findMethod(segment.eviction.getClass(), "onEntryHit");
            AtomicBoolean running = new AtomicBoolean(true);
            Thread[] producers = new Thread[4];
            for (int i = 0; i < producers.length; i++) {
                producers[i] = new Thread(() -> {
                    while (running.get()) {
                        try {
                            onEntryHit.invoke(segment.eviction, entry);
                        }
                        catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                producers[i].start();
            }

            while (!segment.eviction.thresholdExpired()) {
                Thread.yield();
            }

            Thread drain = new Thread(segment.eviction::execute);
            drain.start();
            drain.join(1_000);
            assertThat(drain.isAlive())
                    .as("%s drain must finish while producers are still active", eviction)
                    .isFalse();

            running.set(false);
            for (Thread producer : producers) {
                producer.join();
            }
            segment.eviction.execute();
            assertAccessQueueState(segment.eviction, 0);
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

    private static void assertAccessQueueCounterMatchesQueue(Object evictionPolicy) throws Exception {
        Field queueField = evictionPolicy.getClass().getDeclaredField("accessQueue");
        queueField.setAccessible(true);
        ConcurrentLinkedQueue<?> queue = (ConcurrentLinkedQueue<?>) queueField.get(evictionPolicy);

        Field counterField = evictionPolicy.getClass().getDeclaredField("accessQueueSize");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(evictionPolicy);

        assertThat(counter.get()).isEqualTo(queue.size());
    }

    private static void runSafely(AtomicReference<Throwable> failure, ThrowingRunnable action) {
        try {
            action.run();
        }
        catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    private static Object firstEntry(Object segment) throws Exception {
        Field tableField = segment.getClass().getDeclaredField("table");
        tableField.setAccessible(true);
        Object[] table = (Object[]) tableField.get(segment);
        for (Object entry : table) {
            if (entry != null) {
                return entry;
            }
        }
        throw new AssertionError("Expected a map entry");
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new AssertionError("Method not found: " + name);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
