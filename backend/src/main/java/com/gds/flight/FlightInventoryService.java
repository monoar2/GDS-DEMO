package com.gds.flight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gds.config.CacheConfig;

@Service
public class FlightInventoryService {

    private final FlightRepository flights;
    private final FareInventoryRepository fares;

    public FlightInventoryService(FlightRepository flights, FareInventoryRepository fares) {
        this.flights = flights;
        this.fares = fares;
    }

    /** Raw inventory for a route and day: every departure with the seats left in each booking class. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.FLIGHT_INVENTORY, sync = true, key = "#origin + ':' + #destination + ':' + #date")
    public List<FlightInventoryView> inventory(String origin, String destination, LocalDate date) {
        List<Flight> departures = flights.findByOriginAndDestinationAndDepartureDateOrderByDeparture(origin, destination, date);
        if (departures.isEmpty()) {
            return List.of();
        }
        Map<Long, List<FareInventory>> byFlight = fares
                .findByFlightIdInOrderByFlightIdAscBaseFareDesc(departures.stream().map(Flight::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(FareInventory::getFlightId));

        return departures.stream()
                .map(f -> new FlightInventoryView(
                        f.getId(),
                        f.getAirlineCode() + f.getFlightNumber(),
                        f.getOrigin(),
                        f.getDestination(),
                        f.getDeparture(),
                        f.getArrival(),
                        f.getDurationMinutes(),
                        f.getAircraft(),
                        byFlight.getOrDefault(f.getId(), List.of()).stream()
                                .map(c -> new BookingClassView(c.getBookingClass(), c.getCabin(), c.getBaseFare(),
                                        c.getCurrency(), c.getSeatsAvailable(), c.getSeatsTotal()))
                                .toList()))
                .toList();
    }

    public record FlightInventoryView(Long flightId, String flight, String origin, String destination,
                                      LocalDateTime departure, LocalDateTime arrival, int durationMinutes,
                                      String aircraft, List<BookingClassView> classes) {
    }

    public record BookingClassView(String code, Cabin cabin, BigDecimal baseFare, String currency,
                                   int seatsAvailable, int seatsTotal) {
    }
}
