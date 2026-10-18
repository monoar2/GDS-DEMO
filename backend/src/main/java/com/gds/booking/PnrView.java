package com.gds.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.gds.flight.Cabin;
import com.gds.pricing.FareRules;
import com.gds.pricing.PassengerType;
import com.gds.pricing.PricingService.AirPrice;

/** What agents see when they retrieve a PNR. */
public record PnrView(String recordLocator, PnrStatus status, String pcc, String createdBy, Instant createdAt,
                      Instant ticketingDeadline, String contactEmail, String contactPhone,
                      List<PassengerLine> passengers, List<AirLine> air, List<HotelLine> hotels, List<CarLine> cars,
                      AirPrice airPrice, BigDecimal hotelTotal, BigDecimal carTotal, BigDecimal grandTotal, String currency,
                      List<TicketLine> tickets, List<HistoryLine> history) {

    public record PassengerLine(Long id, PassengerType type, String name, LocalDate dateOfBirth) {
    }

    public record AirLine(Long id, String flight, String origin, String destination, LocalDateTime departure,
                          LocalDateTime arrival, String bookingClass, Cabin cabin, String fareBasis, int seats,
                          BigDecimal baseFare, FareRules rules, SegmentStatus status) {
    }

    public record HotelLine(Long id, String propertyCode, String hotelName, String city, String roomCode,
                            LocalDate checkIn, LocalDate checkOut, int rooms, BigDecimal nightlyRate, BigDecimal total,
                            String confirmation, SegmentStatus status) {
    }

    public record CarLine(Long id, String vendor, String location, String sipp, String model, LocalDate pickupDate,
                          LocalDate returnDate, BigDecimal dailyRate, BigDecimal total, String confirmation,
                          SegmentStatus status) {
    }

    public record TicketLine(String number, String passenger, BigDecimal fare, BigDecimal taxes, BigDecimal total,
                             String formOfPayment, Instant issuedAt, TicketStatus status, BigDecimal refundAmount) {
    }

    public record HistoryLine(Instant at, String actor, String action, String detail) {
    }

    /** One line per PNR for lists. */
    public record Summary(String recordLocator, PnrStatus status, String leadPassenger, int passengers,
                          String itinerary, LocalDateTime firstDeparture, Instant createdAt, Instant ticketingDeadline,
                          String createdBy) {
    }
}
