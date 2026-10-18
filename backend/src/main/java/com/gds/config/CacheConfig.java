package com.gds.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gds.car.CarInventoryService.CarInventoryView;
import com.gds.flight.FlightInventoryService.FlightInventoryView;
import com.gds.hotel.HotelInventoryService.HotelInventoryView;
import com.gds.reference.ReferenceService.AirlineView;
import com.gds.reference.ReferenceService.AirportView;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Two cache levels: a local Caffeine cache in each instance in front of Redis (see {@link TwoLevelCache}).
 * Reference data (airports, airlines) lives for hours; inventory snapshots live for seconds so availability
 * stays close to the database while absorbing read bursts. Redis stores plain JSON for each value type, so
 * entries are readable with redis-cli. If Redis is down, cache errors are logged and requests fall through to
 * the local level and H2 instead of failing.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    public static final String AIRPORTS = "airports";
    public static final String AIRLINES = "airlines";
    public static final String FLIGHT_INVENTORY = "flight-inventory";
    public static final String HOTEL_INVENTORY = "hotel-inventory";
    public static final String CAR_INVENTORY = "car-inventory";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private static final Set<String> REFERENCE = Set.of(AIRPORTS, AIRLINES);
    private static final int LOCAL_MAX_ENTRIES = 20_000;

    /** Only with {@code spring.cache.type=redis}; tests use Spring's simple in-memory cache instead. */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    CacheManager cacheManager(RedisConnectionFactory redis, ObjectMapper mapper, GdsProperties props,
                              MeterRegistry registry) {
        var base = RedisCacheConfiguration.defaultCacheConfig().prefixCacheNameWith("gds:");
        var reference = base.entryTtl(props.cache().referenceTtl());
        var inventory = base.entryTtl(props.cache().inventoryTtl());
        RedisCacheManager shared = RedisCacheManager.builder(redis)
                .cacheDefaults(base)
                .withCacheConfiguration(AIRPORTS, listOf(reference, mapper, AirportView.class))
                .withCacheConfiguration(AIRLINES, listOf(reference, mapper, AirlineView.class))
                .withCacheConfiguration(FLIGHT_INVENTORY, listOf(inventory, mapper, FlightInventoryView.class))
                .withCacheConfiguration(HOTEL_INVENTORY, listOf(inventory, mapper, HotelInventoryView.class))
                .withCacheConfiguration(CAR_INVENTORY, listOf(inventory, mapper, CarInventoryView.class))
                .build();
        shared.afterPropertiesSet();

        return new AbstractCacheManager() {
            @Override
            protected List<Cache> loadCaches() {
                return shared.getCacheNames().stream().map(this::twoLevel).toList();
            }

            @Override
            protected Cache getMissingCache(String name) {
                return twoLevel(name);
            }

            private Cache twoLevel(String name) {
                Duration ttl = REFERENCE.contains(name) ? props.cache().referenceTtl() : props.cache().localTtl();
                var local = new CaffeineCache(name, Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(LOCAL_MAX_ENTRIES)
                        .build(), false);
                return new TwoLevelCache(local, shared.getCache(name), registry);
            }
        };
    }

    private static RedisCacheConfiguration listOf(RedisCacheConfiguration config, ObjectMapper mapper, Class<?> element) {
        var type = mapper.getTypeFactory().constructCollectionType(List.class, element);
        return config.serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed on {} ({}), reading from database", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed on {} ({})", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed on {} ({})", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed on {} ({})", cache.getName(), e.getMessage());
            }
        };
    }
}
