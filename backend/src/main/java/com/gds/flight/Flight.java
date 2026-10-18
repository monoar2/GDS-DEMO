package com.gds.flight;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One dated departure of a scheduled flight, e.g. AA100 JFK-LAX on 2026-11-02. */
@Entity
@Table(name = "flight", indexes = {
        @Index(name = "ix_flight_route_date", columnList = "origin,destination,departure_date"),
        @Index(name = "ix_flight_number_date", columnList = "airline_code,flight_number,departure_date", unique = true)
})
public class Flight {

    @Id
    private Long id;

    @Column(name = "airline_code", length = 2, nullable = false)
    private String airlineCode;

    @Column(name = "flight_number", nullable = false)
    private int flightNumber;

    @Column(length = 3, nullable = false)
    private String origin;

    @Column(length = 3, nullable = false)
    private String destination;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(nullable = false)
    private LocalDateTime departure;

    @Column(nullable = false)
    private LocalDateTime arrival;

    private int durationMinutes;

    @Column(length = 4)
    private String aircraft;

    protected Flight() {
    }

    public Long getId() { return id; }
    public String getAirlineCode() { return airlineCode; }
    public int getFlightNumber() { return flightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public LocalDate getDepartureDate() { return departureDate; }
    public LocalDateTime getDeparture() { return departure; }
    public LocalDateTime getArrival() { return arrival; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getAircraft() { return aircraft; }
}
