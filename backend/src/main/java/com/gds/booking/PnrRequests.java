package com.gds.booking;

import java.time.LocalDate;
import java.util.List;

import com.gds.pricing.PassengerType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies for the PNR API. */
public final class PnrRequests {

    private PnrRequests() {
    }

    public record PassengerInput(@NotNull PassengerType type,
                                 @NotBlank @Size(max = 40) @Pattern(regexp = "[\\p{L} '-]+") String firstName,
                                 @NotBlank @Size(max = 40) @Pattern(regexp = "[\\p{L} '-]+") String lastName,
                                 LocalDate dateOfBirth) {
    }

    public record AirSegmentInput(@NotNull Long flightId, @NotBlank @Pattern(regexp = "[A-Z]") String bookingClass) {
    }

    public record HotelSegmentInput(@NotNull Long roomTypeId, @NotNull LocalDate checkIn, @NotNull LocalDate checkOut,
                                    @Min(1) @Max(9) int rooms) {
    }

    public record CarSegmentInput(@NotBlank String vendor, @NotBlank String location, @NotBlank String sipp,
                                  @NotNull LocalDate pickupDate, @Min(1) @Max(30) int days) {
    }

    public record Segments(@Valid List<AirSegmentInput> air, @Valid List<HotelSegmentInput> hotels,
                           @Valid List<CarSegmentInput> cars) {

        public List<AirSegmentInput> airOrEmpty() { return air == null ? List.of() : air; }
        public List<HotelSegmentInput> hotelsOrEmpty() { return hotels == null ? List.of() : hotels; }
        public List<CarSegmentInput> carsOrEmpty() { return cars == null ? List.of() : cars; }

        boolean isEmpty() {
            return airOrEmpty().isEmpty() && hotelsOrEmpty().isEmpty() && carsOrEmpty().isEmpty();
        }
    }

    public record CreatePnr(@NotEmpty @Size(max = 9) @Valid List<PassengerInput> passengers,
                            @Email String contactEmail, @Size(max = 30) String contactPhone,
                            @NotNull @Valid Segments segments) {
    }

    public enum FormOfPayment { CASH, CARD, INVOICE }

    public record TicketRequest(@NotNull FormOfPayment formOfPayment, @Pattern(regexp = "\\d{4}") String cardLast4) {
    }

    public record ContactUpdate(@Email String contactEmail, @Size(max = 30) String contactPhone) {
    }

    public enum SegmentType { AIR, HOTEL, CAR }
}
