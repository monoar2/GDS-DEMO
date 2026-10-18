package com.gds.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TwoLevelCacheTests {

    private final ConcurrentMapCache local = new ConcurrentMapCache("flight-inventory");
    private final ConcurrentMapCache shared = new ConcurrentMapCache("flight-inventory");
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TwoLevelCache cache = new TwoLevelCache(local, shared, registry);

    private double gets(String result, String level) {
        return registry.get("cache.gets").tag("result", result).tag("level", level).counter().count();
    }

    @Test
    void sharedHitIsCopiedToTheLocalLevel() {
        shared.put("JFK:LHR:2026-10-18", "flights");

        assertThat(cache.get("JFK:LHR:2026-10-18").get()).isEqualTo("flights");
        assertThat(local.get("JFK:LHR:2026-10-18").get()).isEqualTo("flights");
        assertThat(cache.get("JFK:LHR:2026-10-18").get()).isEqualTo("flights");

        assertThat(gets("hit", "redis")).isEqualTo(1);
        assertThat(gets("hit", "local")).isEqualTo(1);
    }

    @Test
    void missLoadsOnceAndWritesBothLevels() {
        assertThat(cache.get("k")).isNull();
        assertThat(cache.get("k", () -> "loaded")).isEqualTo("loaded");

        assertThat(local.get("k").get()).isEqualTo("loaded");
        assertThat(shared.get("k").get()).isEqualTo("loaded");
        assertThat(gets("miss", "none")).isEqualTo(2);
    }

    @Test
    void evictClearsBothLevels() {
        cache.put("k", "v");
        cache.evict("k");

        assertThat(local.get("k")).isNull();
        assertThat(shared.get("k")).isNull();
    }

    @Test
    void concurrentMissesLoadOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return cache.get("hot", () -> {
                        loads.incrementAndGet();
                        Thread.sleep(100);
                        return "loaded";
                    });
                }));
            }
            start.countDown();
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("loaded");
            }
        }
        assertThat(loads).hasValue(1);
    }
}
