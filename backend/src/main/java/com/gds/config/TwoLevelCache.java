package com.gds.config;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * A small in-process cache (Caffeine) in front of the shared Redis cache.
 *
 * <p>One air search reads up to 46 route-days of inventory. Fetching each from Redis means a network hop plus
 * JSON decoding; at a few hundred searches a second that decoding alone saturates the CPU. The local level hands
 * back the already-decoded object instead, and Redis remains the shared level that other instances (and restarts)
 * see. Local entries live only a few seconds, so another instance's booking is visible here within that window;
 * this instance's own bookings evict both levels immediately.
 *
 * <p>With {@code @Cacheable(sync = true)}, concurrent misses on the same key wait for one load instead of all
 * hitting the database (a cache stampede). The waiting uses a {@link CompletableFuture} rather than a lock held
 * during the load, because on Java 21 a virtual thread blocked inside {@code synchronized} pins its carrier
 * thread.
 *
 * <p>Redis errors are logged and treated as a miss, so the local level and the database keep serving.
 * Counts {@code cache.gets} by result (hit, miss) and level (local, redis) for the dashboard and Prometheus.
 */
class TwoLevelCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(TwoLevelCache.class);

    private final Cache local;
    private final Cache shared;
    private final Counter localHits;
    private final Counter sharedHits;
    private final Counter misses;
    private final ConcurrentMap<Object, CompletableFuture<Object>> loading = new ConcurrentHashMap<>();

    TwoLevelCache(Cache local, Cache shared, MeterRegistry registry) {
        this.local = local;
        this.shared = shared;
        this.localHits = counter(registry, "hit", "local");
        this.sharedHits = counter(registry, "hit", "redis");
        this.misses = counter(registry, "miss", "none");
    }

    private Counter counter(MeterRegistry registry, String result, String level) {
        return Counter.builder("cache.gets")
                .description("Cache lookups by result and the level that answered")
                .tag("cache", getName())
                .tag("result", result)
                .tag("level", level)
                .register(registry);
    }

    @Override
    public String getName() {
        return shared.getName();
    }

    @Override
    public Object getNativeCache() {
        return shared.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper value = local.get(key);
        if (value != null) {
            localHits.increment();
            return value;
        }
        value = sharedGet(key);
        if (value != null) {
            sharedHits.increment();
            local.put(key, value.get());
            return value;
        }
        misses.increment();
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper value = get(key);
        return value == null ? null : (T) value.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> loader) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            return (T) cached.get();
        }
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> inFlight = loading.putIfAbsent(key, mine);
        if (inFlight != null) {
            try {
                return (T) inFlight.join();
            } catch (CompletionException e) {
                throw new ValueRetrievalException(key, loader, e.getCause());
            }
        }
        try {
            T value = loader.call();
            put(key, value);
            mine.complete(value);
            return value;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw new ValueRetrievalException(key, loader, e);
        } finally {
            loading.remove(key, mine);
        }
    }

    @Override
    public void put(Object key, Object value) {
        local.put(key, value);
        try {
            shared.put(key, value);
        } catch (RuntimeException e) {
            log.warn("Redis put failed on {} ({})", getName(), e.getMessage());
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        local.evict(key);
        try {
            shared.evict(key);
        } catch (RuntimeException e) {
            log.warn("Redis evict failed on {} ({}); the entry expires on its own", getName(), e.getMessage());
        }
    }

    @Override
    public void clear() {
        local.clear();
        shared.clear();
    }

    private ValueWrapper sharedGet(Object key) {
        try {
            return shared.get(key);
        } catch (RuntimeException e) {
            log.warn("Redis get failed on {} ({}), reading from the database", getName(), e.getMessage());
            return null;
        }
    }
}
