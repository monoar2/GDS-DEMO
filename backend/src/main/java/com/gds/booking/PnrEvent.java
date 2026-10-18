package com.gds.booking;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** PNR history line: who did what and when, like the history section of a real PNR. */
@Entity
@Table(name = "pnr_event")
public class PnrEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pnr_event_seq")
    @SequenceGenerator(name = "pnr_event_seq", sequenceName = "pnr_event_seq", allocationSize = 50)
    private Long id;

    private Instant at;

    @Column(length = 64)
    private String actor;

    @Column(length = 24)
    private String action;

    @Column(length = 500)
    private String detail;

    protected PnrEvent() {
    }

    PnrEvent(Instant at, String actor, String action, String detail) {
        this.at = at;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
    }

    public Instant getAt() { return at; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
}
