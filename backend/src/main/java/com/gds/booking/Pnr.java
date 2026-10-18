package com.gds.booking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Passenger Name Record: the booking. Groups passengers, air/hotel/car segments, tickets and history. */
@Entity
@Table(name = "pnr", indexes = {
        @Index(name = "ux_pnr_locator", columnList = "record_locator", unique = true),
        @Index(name = "ix_pnr_status_deadline", columnList = "status,ticketing_deadline"),
        @Index(name = "ix_pnr_agency", columnList = "agency_id,created_at")
})
public class Pnr {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pnr_seq")
    @SequenceGenerator(name = "pnr_seq", sequenceName = "pnr_seq", allocationSize = 50)
    private Long id;

    @Column(name = "record_locator", length = 6, nullable = false)
    private String recordLocator;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Column(length = 9, nullable = false)
    private String pcc;

    @Column(length = 64, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ticketing_deadline")
    private Instant ticketingDeadline;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private PnrStatus status = PnrStatus.HELD;

    private String contactEmail;
    private String contactPhone;

    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pnr_id", nullable = false)
    @OrderBy("id")
    private List<Passenger> passengers = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pnr_id", nullable = false)
    @OrderBy("departure")
    private List<AirSegment> airSegments = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pnr_id", nullable = false)
    @OrderBy("checkIn")
    private List<HotelSegment> hotelSegments = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pnr_id", nullable = false)
    @OrderBy("pickupDate")
    private List<CarSegment> carSegments = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pnr_id", nullable = false)
    @OrderBy("id")
    private List<Ticket> tickets = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pnr_id", nullable = false)
    @OrderBy("id")
    private List<PnrEvent> history = new ArrayList<>();

    protected Pnr() {
    }

    Pnr(String recordLocator, Long agencyId, String pcc, String createdBy, Instant createdAt, Instant ticketingDeadline,
        String contactEmail, String contactPhone) {
        this.recordLocator = recordLocator;
        this.agencyId = agencyId;
        this.pcc = pcc;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.ticketingDeadline = ticketingDeadline;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
    }

    void log(String actor, String action, String detail) {
        history.add(new PnrEvent(Instant.now(), actor, action, detail));
    }

    void setStatus(PnrStatus status) { this.status = status; }
    void setTicketingDeadline(Instant deadline) { this.ticketingDeadline = deadline; }
    void setContact(String email, String phone) {
        this.contactEmail = email;
        this.contactPhone = phone;
    }

    public Long getId() { return id; }
    public String getRecordLocator() { return recordLocator; }
    public Long getAgencyId() { return agencyId; }
    public String getPcc() { return pcc; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getTicketingDeadline() { return ticketingDeadline; }
    public PnrStatus getStatus() { return status; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public long getVersion() { return version; }
    public List<Passenger> getPassengers() { return passengers; }
    public List<AirSegment> getAirSegments() { return airSegments; }
    public List<HotelSegment> getHotelSegments() { return hotelSegments; }
    public List<CarSegment> getCarSegments() { return carSegments; }
    public List<Ticket> getTickets() { return tickets; }
    public List<PnrEvent> getHistory() { return history; }

    public List<AirSegment> activeAirSegments() {
        return airSegments.stream().filter(AirSegment::isActive).toList();
    }

    public boolean hasActiveSegments() {
        return airSegments.stream().anyMatch(AirSegment::isActive)
                || hotelSegments.stream().anyMatch(HotelSegment::isActive)
                || carSegments.stream().anyMatch(CarSegment::isActive);
    }
}
