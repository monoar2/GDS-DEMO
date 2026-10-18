package com.gds.car;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Cars of one ACRISS (SIPP) category a vendor has left at one location for one pick-up day. */
@Entity
@Table(name = "car_inventory", indexes = {
        @Index(name = "ux_car_inventory", columnList = "location_code,pickup_date,vendor_code,sipp_code", unique = true)
})
public class CarInventory {

    @Id
    private Long id;

    @Column(name = "vendor_code", length = 2, nullable = false)
    private String vendorCode;

    @Column(name = "location_code", length = 3, nullable = false)
    private String locationCode;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    /** ACRISS code, e.g. ECMR = Economy, 2/4 door, Manual, Air. */
    @Column(name = "sipp_code", length = 4, nullable = false)
    private String sippCode;

    private String sampleModel;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal dailyRate;

    @Column(length = 3, nullable = false)
    private String currency;

    private int unitsTotal;
    private int unitsAvailable;

    @Version
    private long version;

    protected CarInventory() {
    }

    public Long getId() { return id; }
    public String getVendorCode() { return vendorCode; }
    public String getLocationCode() { return locationCode; }
    public LocalDate getPickupDate() { return pickupDate; }
    public String getSippCode() { return sippCode; }
    public String getSampleModel() { return sampleModel; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public String getCurrency() { return currency; }
    public int getUnitsTotal() { return unitsTotal; }
    public int getUnitsAvailable() { return unitsAvailable; }
    public long getVersion() { return version; }
}
