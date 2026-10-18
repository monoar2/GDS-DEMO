package com.gds.system;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inventory volumes and runtime facts, for the dashboard and for checking virtual threads are on. */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final JdbcTemplate jdbc;
    private final RedisConnectionFactory redis;

    public SystemController(JdbcTemplate jdbc, RedisConnectionFactory redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @GetMapping("/stats")
    public SystemStats stats() {
        Thread current = Thread.currentThread();
        var counts = new LinkedHashMap<String, Long>();
        for (String table : new String[] {"airport", "airline", "flight", "fare_inventory", "hotel", "room_type",
                "room_inventory", "car_inventory"}) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        LocalDate first = jdbc.queryForObject("select min(departure_date) from flight", LocalDate.class);
        LocalDate last = jdbc.queryForObject("select max(departure_date) from flight", LocalDate.class);
        return new SystemStats(
                counts,
                first,
                last,
                new RuntimeInfo(Runtime.version().toString(), current.isVirtual(), current.toString(),
                        Runtime.getRuntime().availableProcessors(), redisUp()));
    }

    private boolean redisUp() {
        try (var connection = redis.getConnection()) {
            return "PONG".equals(connection.ping());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record SystemStats(Map<String, Long> rows, LocalDate inventoryFrom, LocalDate inventoryTo, RuntimeInfo runtime) {
    }

    public record RuntimeInfo(String javaVersion, boolean virtualThread, String thread, int cpus, boolean redisUp) {
    }
}
