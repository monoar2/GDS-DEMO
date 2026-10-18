package com.gds.booking;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** An electronic ticket: one per passenger, covering every air segment in the PNR. */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_seq")
    @SequenceGenerator(name = "ticket_seq", sequenceName = "ticket_seq", allocationSize = 50)
    private Long id;

    /** 13 digits: 3-digit airline accounting code + 10-digit serial. */
    @Column(length = 13, nullable = false, unique = true)
    private String number;

    private Long passengerId;
    private String passengerName;

    @Column(precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxes;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    @Column(length = 24)
    private String formOfPayment;

    private Instant issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TicketStatus status = TicketStatus.ISSUED;

    @Column(precision = 10, scale = 2)
    private BigDecimal refundAmount;

    protected Ticket() {
    }

    public Ticket(String number, Long passengerId, String passengerName, BigDecimal fare, BigDecimal taxes,
                  String formOfPayment, Instant issuedAt) {
        this.number = number;
        this.passengerId = passengerId;
        this.passengerName = passengerName;
        this.fare = fare;
        this.taxes = taxes;
        this.total = fare.add(taxes);
        this.formOfPayment = formOfPayment;
        this.issuedAt = issuedAt;
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public Long getPassengerId() { return passengerId; }
    public String getPassengerName() { return passengerName; }
    public BigDecimal getFare() { return fare; }
    public BigDecimal getTaxes() { return taxes; }
    public BigDecimal getTotal() { return total; }
    public String getFormOfPayment() { return formOfPayment; }
    public Instant getIssuedAt() { return issuedAt; }
    public TicketStatus getStatus() { return status; }
    public BigDecimal getRefundAmount() { return refundAmount; }

    void voidTicket() {
        status = TicketStatus.VOIDED;
        refundAmount = total;
    }

    void refund(BigDecimal amount) {
        status = TicketStatus.REFUNDED;
        refundAmount = amount;
    }
}
