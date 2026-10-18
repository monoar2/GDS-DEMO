package com.gds.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.gds.flight.Cabin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "pnr_air_segment")
public class AirSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_segment_seq")
    @SequenceGenerator(name = "air_segment_seq", sequenceName = "air_segment_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private Long fareInventoryId;

    @Column(nullable = false)
    private Long flightId;

    @Column(length = 2, nullable = false)
    private String airlineCode;

    @Column(length = 8, nullable = false)
    private String flight;

    @Column(length = 3, nullable = false)
    private String origin;

    @Column(length = 3, nullable = false)
    private String destination;

    private LocalDateTime departure;
    private LocalDateTime arrival;

    @Column(length = 1, nullable = false)
    private String bookingClass;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Cabin cabin;

    @Column(length = 16)
    private String fareBasis;

    /** Seats held: adults and children, infants on lap don't take one. */
    private int seats;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFare;

    @Enumerated(EnumType.STRING)
    @Column(length = 2, nullable = false)
    private SegmentStatus status = SegmentStatus.HK;

    protected AirSegment() {
    }

    public AirSegment(Long fareInventoryId, Long flightId, String airlineCode, String flight, String origin,
                      String destination, LocalDateTime departure, LocalDateTime arrival, String bookingClass, Cabin cabin,
                      String fareBasis, int seats, BigDecimal baseFare) {
        this.fareInventoryId = fareInventoryId;
        this.flightId = flightId;
        this.airlineCode = airlineCode;
        this.flight = flight;
        this.origin = origin;
        this.destination = destination;
        this.departure = departure;
        this.arrival = arrival;
        this.bookingClass = bookingClass;
        this.cabin = cabin;
        this.fareBasis = fareBasis;
        this.seats = seats;
        this.baseFare = baseFare;
    }

    public Long getId() { return id; }
    public Long getFareInventoryId() { return fareInventoryId; }
    public Long getFlightId() { return flightId; }
    public String getAirlineCode() { return airlineCode; }
    public String getFlight() { return flight; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public LocalDateTime getDeparture() { return departure; }
    public LocalDateTime getArrival() { return arrival; }
    public String getBookingClass() { return bookingClass; }
    public Cabin getCabin() { return cabin; }
    public String getFareBasis() { return fareBasis; }
    public int getSeats() { return seats; }
    public BigDecimal getBaseFare() { return baseFare; }
    public SegmentStatus getStatus() { return status; }
    public boolean isActive() { return status == SegmentStatus.HK; }

    void cancel() { status = SegmentStatus.XX; }
}
