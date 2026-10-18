package com.gds.shopping;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gds.common.BadRequestException;
import com.gds.flight.Cabin;
import com.gds.pricing.PassengerType;
import com.gds.shopping.AirShoppingService.AirOffer;
import com.gds.shopping.StayShoppingService.CarOffer;
import com.gds.shopping.StayShoppingService.HotelOffer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Availability and shopping: priced, bookable options. Prices are re-checked when the PNR is created. */
@RestController
@RequestMapping("/api/shopping")
@Validated
public class ShoppingController {

    private static final String IATA = "[A-Za-z]{3}";

    private final AirShoppingService air;
    private final StayShoppingService stays;

    public ShoppingController(AirShoppingService air, StayShoppingService stays) {
        this.air = air;
        this.stays = stays;
    }

    public record AirSearchResult(List<AirOffer> outbound, List<AirOffer> inbound) {
    }

    @GetMapping("/air")
    public AirSearchResult air(@RequestParam @Pattern(regexp = IATA) String origin,
                               @RequestParam @Pattern(regexp = IATA) String destination,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
                               @RequestParam(defaultValue = "1") @Min(0) @Max(9) int adults,
                               @RequestParam(defaultValue = "0") @Min(0) @Max(8) int children,
                               @RequestParam(defaultValue = "0") @Min(0) @Max(4) int infants,
                               @RequestParam(defaultValue = "ECONOMY") Cabin cabin) {
        if (infants > adults) {
            throw new BadRequestException("Each infant must travel with an adult");
        }
        if (returnDate != null && returnDate.isBefore(date)) {
            throw new BadRequestException("Return date is before departure date");
        }
        Map<PassengerType, Integer> passengers = new EnumMap<>(PassengerType.class);
        passengers.put(PassengerType.ADT, adults);
        passengers.put(PassengerType.CHD, children);
        passengers.put(PassengerType.INF, infants);
        String from = origin.toUpperCase();
        String to = destination.toUpperCase();
        return new AirSearchResult(
                air.search(from, to, date, passengers, cabin),
                returnDate == null ? List.of() : air.search(to, from, returnDate, passengers, cabin));
    }

    @GetMapping("/hotels")
    public List<HotelOffer> hotels(@RequestParam @Pattern(regexp = IATA) String city,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                                   @RequestParam(defaultValue = "1") int rooms,
                                   @RequestParam(defaultValue = "1") int guests) {
        return stays.hotels(city.toUpperCase(), checkIn, checkOut, rooms, guests);
    }

    @GetMapping("/cars")
    public List<CarOffer> cars(@RequestParam @Pattern(regexp = IATA) String location,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickup,
                               @RequestParam(defaultValue = "1") int days) {
        return stays.cars(location.toUpperCase(), pickup, days);
    }
}
