package com.gds.supplier;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.gds.config.GdsProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Stands in for the remote systems a real GDS talks to: the airline's host for a live seat check, the hotel's
 * property system for a rate confirmation, the car vendor's reservation system. The mock inventory is local,
 * so this simply blocks for {@code gds.supplier.latency}, the way a network call would.
 *
 * <p>That blocking is what the step 4 load test is about. On platform threads every request waiting here
 * occupies one of Tomcat's 200 worker threads; on virtual threads the wait parks the virtual thread and frees
 * the carrier thread for other work.
 */
@Component
public class SupplierGateway {

    private final Duration latency;
    private final MeterRegistry registry;

    public SupplierGateway(GdsProperties properties, MeterRegistry registry) {
        this.latency = properties.supplier() == null || properties.supplier().latency() == null
                ? Duration.ZERO
                : properties.supplier().latency();
        this.registry = registry;
    }

    /** One round trip to a supplier system. {@code type} is air, hotel or car, and tags the timer. */
    public void confirm(String type) {
        if (latency.isZero()) {
            return;
        }
        Timer.builder("gds.supplier.calls")
                .description("Simulated round trips to airline, hotel and car supplier systems")
                .tag("type", type)
                .register(registry)
                .record(() -> {
                    try {
                        Thread.sleep(latency);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
    }

    public Duration latency() {
        return latency;
    }
}
