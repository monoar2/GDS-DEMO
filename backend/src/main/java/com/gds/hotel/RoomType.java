package com.gds.hotel;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_type", indexes = @Index(name = "ix_room_type_hotel", columnList = "hotel_id"))
public class RoomType {

    @Id
    private Long id;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    /** e.g. STD, DLX, STE, EXE */
    @Column(length = 3, nullable = false)
    private String code;

    private String description;
    private int maxOccupancy;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal nightlyRate;

    @Column(length = 3, nullable = false)
    private String currency;

    protected RoomType() {
    }

    public Long getId() { return id; }
    public Long getHotelId() { return hotelId; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getMaxOccupancy() { return maxOccupancy; }
    public BigDecimal getNightlyRate() { return nightlyRate; }
    public String getCurrency() { return currency; }
}
