package com.gds.system;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gds.car.CarInventoryService;
import com.gds.car.CarInventoryService.CarInventoryView;
import com.gds.flight.FlightInventoryService;
import com.gds.flight.FlightInventoryService.FlightInventoryView;
import com.gds.hotel.HotelInventoryService;
import com.gds.hotel.HotelInventoryService.HotelInventoryView;

import jakarta.validation.constraints.Pattern;

/** Read-only view of raw supplier inventory. Shopping, pricing and booking come in later steps. */
@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private static final String IATA = "[A-Za-z]{3}";

    private final FlightInventoryService flights;
    private final HotelInventoryService hotels;
    private final CarInventoryService cars;

    public InventoryController(FlightInventoryService flights, HotelInventoryService hotels, CarInventoryService cars) {
        this.flights = flights;
        this.hotels = hotels;
        this.cars = cars;
    }

    @GetMapping("/flights")
    public List<FlightInventoryView> flights(@RequestParam @Pattern(regexp = IATA) String origin,
                                             @RequestParam @Pattern(regexp = IATA) String destination,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return flights.inventory(origin.toUpperCase(), destination.toUpperCase(), date);
    }

    @GetMapping("/hotels")
    public List<HotelInventoryView> hotels(@RequestParam @Pattern(regexp = IATA) String city,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return hotels.inventory(city.toUpperCase(), date);
    }

    @GetMapping("/cars")
    public List<CarInventoryView> cars(@RequestParam @Pattern(regexp = IATA) String location,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return cars.inventory(location.toUpperCase(), date);
    }
}
