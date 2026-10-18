package com.gds.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gds")
public record GdsProperties(Security security, Booking booking, Seed seed, Cache cache, Supplier supplier,
                           Http http) {

    public record Security(String jwtSecret, Duration tokenTtl) {
    }

    public record Booking(Duration holdTime, Duration expiryCheck) {
    }

    public record Seed(boolean enabled, long randomSeed, int days, int hotelsPerCity) {
    }

    /** {@code localTtl}: how long each instance keeps inventory in memory before asking Redis again. */
    public record Cache(Duration referenceTtl, Duration inventoryTtl, Duration localTtl) {
    }

    /** {@code latency}: simulated round trip to airline, hotel and car systems on every search. */
    public record Supplier(Duration latency) {
    }

    /** Concurrency limit for API requests; see ConcurrencyLimitFilter. */
    public record Http(int maxConcurrentRequests, Duration queueTimeout) {
    }
}
