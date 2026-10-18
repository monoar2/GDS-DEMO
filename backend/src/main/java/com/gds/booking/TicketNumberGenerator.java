package com.gds.booking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/** Ticket numbers: 3-digit accounting code of the validating airline + 10-digit serial from a database sequence. */
@Component
public class TicketNumberGenerator {

    private final JdbcTemplate jdbc;

    public TicketNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void createSequence() {
        jdbc.execute("create sequence if not exists ticket_number_seq start with 1000000000");
    }

    public String next(String validatingAirline) {
        Long serial = jdbc.queryForObject("select next value for ticket_number_seq", Long.class);
        return "%03d%010d".formatted(accountingCode(validatingAirline), serial);
    }

    static int accountingCode(String airline) {
        return 100 + Math.floorMod(airline.hashCode() * 31 + 7, 900);
    }
}
