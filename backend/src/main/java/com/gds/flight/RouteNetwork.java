package com.gds.flight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Which airports have scheduled service to which, the way a GDS keeps a schedule index next to its availability
 * cache. Shopping uses it to look up only the connections that exist: from a spoke airport that is one or two
 * hubs, not all fifteen.
 *
 * <p>Schedules are generated once at startup and never change while the app runs, so the index is loaded on first
 * use and kept. (A lock rather than {@code synchronized}, so a virtual thread waiting for the load doesn't pin its
 * carrier thread.)
 */
@Component
public class RouteNetwork {

    private final JdbcTemplate jdbc;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Map<String, Set<String>> served;

    public RouteNetwork(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True if any flight is scheduled from {@code origin} to {@code destination}. */
    public boolean hasService(String origin, String destination) {
        return routes().getOrDefault(origin, Set.of()).contains(destination);
    }

    private Map<String, Set<String>> routes() {
        Map<String, Set<String>> current = served;
        if (current != null) {
            return current;
        }
        lock.lock();
        try {
            if (served == null) {
                Map<String, Set<String>> loaded = new HashMap<>();
                jdbc.query("select distinct origin, destination from flight",
                        rs -> { loaded.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2)); });
                if (loaded.isEmpty()) {
                    return loaded; // still seeding: try again next time
                }
                served = loaded;
            }
            return served;
        } finally {
            lock.unlock();
        }
    }
}
