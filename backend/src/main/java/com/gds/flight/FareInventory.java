package com.gds.flight;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Seats left in one booking class (RBD) on one flight, e.g. class "Y" on AA100 has 42 seats at 389.00.
 * Booking classes nest: cheaper classes close first as the flight fills up.
 */
@Entity
@Table(name = "fare_inventory", indexes = {
        @Index(name = "ux_fare_flight_class", columnList = "flight_id,booking_class", unique = true)
})
public class FareInventory {

    @Id
    private Long id;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "booking_class", length = 1, nullable = false)
    private String bookingClass;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Cabin cabin;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFare;

    @Column(length = 3, nullable = false)
    private String currency;

    private int seatsTotal;
    private int seatsAvailable;

    @Version
    private long version;

    protected FareInventory() {
    }

    public Long getId() { return id; }
    public Long getFlightId() { return flightId; }
    public String getBookingClass() { return bookingClass; }
    public Cabin getCabin() { return cabin; }
    public BigDecimal getBaseFare() { return baseFare; }
    public String getCurrency() { return currency; }
    public int getSeatsTotal() { return seatsTotal; }
    public int getSeatsAvailable() { return seatsAvailable; }
    public long getVersion() { return version; }
}
