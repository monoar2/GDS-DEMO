package com.gds.agency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo agencies and logins, created on first start. Passwords are for local use only:
 * admin/admin123, supervisor/super123, agent1/agent123, agent2/agent123, mexagent/agent123.
 * Load-test agents loadtest1..loadtest20 (password "loadtest") book through the same API.
 */
@Component
@Order(0)
public class AgencySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgencySeeder.class);

    private final AgencyRepository agencies;
    private final AgentRepository agents;
    private final PasswordEncoder encoder;

    public AgencySeeder(AgencyRepository agencies, AgentRepository agents, PasswordEncoder encoder) {
        this.agencies = agencies;
        this.agents = agents;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (agencies.count() > 0) {
            return;
        }
        Agency hq = agencies.save(new Agency("GDS1HQ", "GDS Operations", "US"));
        Agency nyc = agencies.save(new Agency("NYC1A2", "Hudson Travel", "US"));
        Agency mex = agencies.save(new Agency("MEX1B3", "Viajes Pirámide", "MX"));
        Agency load = agencies.save(new Agency("LOAD01", "Load Test Agency", "US"));

        agents.save(new Agent("admin", encoder.encode("admin123"), "GDS Administrator", Role.ADMIN, hq.getId()));
        agents.save(new Agent("supervisor", encoder.encode("super123"), "Dana Supervisor", Role.SUPERVISOR, nyc.getId()));
        agents.save(new Agent("agent1", encoder.encode("agent123"), "Alex Agent", Role.AGENT, nyc.getId()));
        agents.save(new Agent("agent2", encoder.encode("agent123"), "Sam Agent", Role.AGENT, nyc.getId()));
        agents.save(new Agent("mexagent", encoder.encode("agent123"), "Lucía Agente", Role.AGENT, mex.getId()));
        String loadPassword = encoder.encode("loadtest");
        for (int i = 1; i <= 20; i++) {
            agents.save(new Agent("loadtest" + i, loadPassword, "Load Tester " + i, Role.AGENT, load.getId()));
        }
        log.info("Seeded {} agencies and {} agents", agencies.count(), agents.count());
    }
}
