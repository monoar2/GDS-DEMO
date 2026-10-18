package com.gds.system;

import java.lang.management.ManagementFactory;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gds.booking.PnrStatus;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * A small JSON snapshot of live counters for the console dashboard. The page polls it every couple of seconds
 * and turns the running totals into rates. Prometheus scrapers should use /actuator/prometheus instead.
 */
@RestController
@RequestMapping("/api/system")
public class LiveMetricsController {

    private final MeterRegistry registry;
    private final JdbcTemplate jdbc;

    public LiveMetricsController(MeterRegistry registry, JdbcTemplate jdbc) {
        this.registry = registry;
        this.jdbc = jdbc;
    }

    public record LiveMetrics(long timestamp, long uptimeSeconds, long requests, double requestSeconds, long errors,
                              double cacheHits, double cacheMisses, double dbActive, double dbPending, long heapUsedMb,
                              long heapMaxMb, int platformThreads, Map<PnrStatus, Long> pnrs) {
    }

    @GetMapping("/metrics")
    public LiveMetrics metrics() {
        long requests = 0;
        double seconds = 0;
        long errors = 0;
        for (Timer timer : registry.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            if (uri == null || uri.startsWith("/actuator") || uri.startsWith("/api/system")) {
                continue; // don't count the dashboard watching itself
            }
            requests += timer.count();
            seconds += timer.totalTime(TimeUnit.SECONDS);
            String outcome = timer.getId().getTag("outcome");
            if ("SERVER_ERROR".equals(outcome)) {
                errors += timer.count();
            }
        }
        Map<PnrStatus, Long> pnrs = new EnumMap<>(PnrStatus.class);
        for (PnrStatus status : PnrStatus.values()) {
            pnrs.put(status, 0L);
        }
        jdbc.query("select status, count(*) from pnr group by status",
                rs -> { pnrs.put(PnrStatus.valueOf(rs.getString(1)), rs.getLong(2)); });

        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return new LiveMetrics(
                System.currentTimeMillis(),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                requests, seconds, errors,
                sum("cache.gets", "hit"), sum("cache.gets", "miss"),
                gauge("hikaricp.connections.active"), gauge("hikaricp.connections.pending"),
                memory.getUsed() / (1024 * 1024), memory.getMax() / (1024 * 1024),
                ManagementFactory.getThreadMXBean().getThreadCount(),
                pnrs);
    }

    private double sum(String name, String result) {
        return registry.find(name).tag("result", result).functionCounters().stream()
                .mapToDouble(FunctionCounter::count).sum()
                + registry.find(name).tag("result", result).counters().stream().mapToDouble(c -> c.count()).sum();
    }

    private double gauge(String name) {
        return registry.find(name).gauges().stream().mapToDouble(Gauge::value).sum();
    }
}
