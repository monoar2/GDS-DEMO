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
@Table(name = "pnr_hotel_segment")
public class HotelSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hotel_segment_seq")
    @SequenceGenerator(name = "hotel_segment_seq", sequenceName = "hotel_segment_seq", allocationSize = 50)
    private Long id;

    private Long hotelId;
    private Long roomTypeId;

    @Column(length = 8)
    private String propertyCode;

    private String hotelName;

    @Column(length = 3)
    private String cityCode;

    @Column(length = 3)
    private String roomCode;

    private LocalDate checkIn;
    private LocalDate checkOut;
    private int rooms;

    @Column(precision = 10, scale = 2)
    private BigDecimal nightlyRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Column(length = 12)
    private String confirmation;

    @Enumerated(EnumType.STRING)
    @Column(length = 2, nullable = false)
    private SegmentStatus status = SegmentStatus.HK;

    protected HotelSegment() {
    }

    public HotelSegment(Long hotelId, Long roomTypeId, String propertyCode, String hotelName, String cityCode,
                        String roomCode, LocalDate checkIn, LocalDate checkOut, int rooms, BigDecimal nightlyRate,
                        BigDecimal total, String confirmation) {
        this.hotelId = hotelId;
        this.roomTypeId = roomTypeId;
        this.propertyCode = propertyCode;
        this.hotelName = hotelName;
        this.cityCode = cityCode;
        this.roomCode = roomCode;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.rooms = rooms;
        this.nightlyRate = nightlyRate;
        this.total = total;
        this.confirmation = confirmation;
    }

    public Long getId() { return id; }
    public Long getHotelId() { return hotelId; }
    public Long getRoomTypeId() { return roomTypeId; }
    public String getPropertyCode() { return propertyCode; }
    public String getHotelName() { return hotelName; }
    public String getCityCode() { return cityCode; }
    public String getRoomCode() { return roomCode; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public int getRooms() { return rooms; }
    public BigDecimal getNightlyRate() { return nightlyRate; }
    public BigDecimal getTotal() { return total; }
    public String getConfirmation() { return confirmation; }
    public SegmentStatus getStatus() { return status; }
    public boolean isActive() { return status == SegmentStatus.HK; }

    void cancel() { status = SegmentStatus.XX; }
}
