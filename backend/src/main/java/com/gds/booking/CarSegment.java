package com.gds.booking;

import java.math.BigDecimal;
import java.time.LocalDate;

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
@Table(name = "pnr_car_segment")
public class CarSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "car_segment_seq")
    @SequenceGenerator(name = "car_segment_seq", sequenceName = "car_segment_seq", allocationSize = 50)
    private Long id;

    @Column(length = 2)
    private String vendorCode;

    @Column(length = 3)
    private String locationCode;

    @Column(length = 4)
    private String sippCode;

    private String sampleModel;
    private LocalDate pickupDate;
    private LocalDate returnDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal dailyRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Column(length = 12)
    private String confirmation;

    @Enumerated(EnumType.STRING)
    @Column(length = 2, nullable = false)
    private SegmentStatus status = SegmentStatus.HK;

    protected CarSegment() {
    }

    public CarSegment(String vendorCode, String locationCode, String sippCode, String sampleModel, LocalDate pickupDate,
                      LocalDate returnDate, BigDecimal dailyRate, BigDecimal total, String confirmation) {
        this.vendorCode = vendorCode;
        this.locationCode = locationCode;
        this.sippCode = sippCode;
        this.sampleModel = sampleModel;
        this.pickupDate = pickupDate;
        this.returnDate = returnDate;
        this.dailyRate = dailyRate;
        this.total = total;
        this.confirmation = confirmation;
    }

    public Long getId() { return id; }
    public String getVendorCode() { return vendorCode; }
    public String getLocationCode() { return locationCode; }
    public String getSippCode() { return sippCode; }
    public String getSampleModel() { return sampleModel; }
    public LocalDate getPickupDate() { return pickupDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public BigDecimal getTotal() { return total; }
    public String getConfirmation() { return confirmation; }
    public SegmentStatus getStatus() { return status; }
    public boolean isActive() { return status == SegmentStatus.HK; }

    void cancel() { status = SegmentStatus.XX; }
}
