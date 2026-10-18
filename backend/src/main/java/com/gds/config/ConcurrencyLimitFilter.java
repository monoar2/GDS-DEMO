package com.gds.config;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caps how many API requests are in progress at once.
 *
 * <p>With platform threads, Tomcat's 200 worker threads are the cap: request 201 waits for a free thread. Virtual
 * threads remove that cap, which is the point, but it also means nothing stops the server from accepting more work
 * than it can finish. Under overload every request then gets slower, memory fills with half-done work, and the
 * JVM can run out of heap. This filter puts the limit back as an explicit setting: past
 * {@code gds.http.max-concurrent-requests}, a request waits up to {@code gds.http.queue-timeout} for a slot and
 * otherwise gets a quick 503 with Retry-After, which a client can back off from.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // before security: turning a request away should be the cheapest thing we do
public class ConcurrencyLimitFilter extends OncePerRequestFilter {

    private final Semaphore slots;
    private final long queueTimeoutMillis;
    private final Counter rejected;

    public ConcurrencyLimitFilter(GdsProperties properties, MeterRegistry registry) {
        int limit = properties.http().maxConcurrentRequests();
        this.slots = new Semaphore(limit);
        this.queueTimeoutMillis = properties.http().queueTimeout().toMillis();
        this.rejected = Counter.builder("gds.http.rejected")
                .description("API requests turned away with 503 because the server was at its concurrency limit")
                .register(registry);
        Gauge.builder("gds.http.in.flight", slots, s -> limit - s.availablePermits())
                .description("API requests in progress")
                .register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/system/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(queueTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            rejected.increment();
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setHeader("Retry-After", "1");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Service Unavailable\",\"status\":503,"
                    + "\"detail\":\"The GDS is at capacity, retry shortly\"}");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            slots.release();
        }
    }
}
