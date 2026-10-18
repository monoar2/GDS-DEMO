package com.gds.shopping;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gds.common.BadRequestException;
import com.gds.flight.Cabin;
import com.gds.flight.FlightInventoryService;
import com.gds.flight.FlightInventoryService.BookingClassView;
import com.gds.flight.FlightInventoryService.FlightInventoryView;
import com.gds.flight.RouteNetwork;
import com.gds.pricing.FareRules;
import com.gds.pricing.PassengerType;
import com.gds.pricing.PricingService;
import com.gds.pricing.PricingService.AirPrice;
import com.gds.pricing.PricingService.SegmentFare;
import com.gds.reference.ReferenceService;
import com.gds.reference.ReferenceService.AirlineView;
import com.gds.reference.ReferenceService.AirportView;
import com.gds.supplier.SupplierGateway;

/**
 * Builds priced itineraries for one direction of travel: nonstop flights plus one-stop connections over
 * the airline hubs that actually serve both ends. Each route lookup is an independent, cache-backed read,
 * so they all run at once on virtual threads; a search touching a dozen routes costs roughly one round trip
 * instead of twelve.
 */
@Service
public class AirShoppingService {

    static final Duration MIN_CONNECTION = Duration.ofMinutes(60);
    static final Duration MAX_CONNECTION = Duration.ofHours(10);
    private static final int MAX_OFFERS = 50;

    private final FlightInventoryService inventory;
    private final RouteNetwork routes;
    private final ReferenceService reference;
    private final PricingService pricing;
    private final SupplierGateway supplier;

    public AirShoppingService(FlightInventoryService inventory, RouteNetwork routes, ReferenceService reference,
                              PricingService pricing, SupplierGateway supplier) {
        this.inventory = inventory;
        this.routes = routes;
        this.reference = reference;
        this.pricing = pricing;
        this.supplier = supplier;
    }

    public record SegmentOffer(Long flightId, String flight, String origin, String destination, LocalDateTime departure,
                               LocalDateTime arrival, int durationMinutes, String aircraft, String bookingClass,
                               Cabin cabin, String fareBasis, int seatsAvailable, BigDecimal baseFare, FareRules rules) {
    }

    public record AirOffer(List<SegmentOffer> segments, int stops, int totalDurationMinutes, AirPrice price) {
    }

    public List<AirOffer> search(String origin, String destination, LocalDate date, Map<PassengerType, Integer> passengers,
                                 Cabin cabin) {
        Map<String, AirportView> airports = reference.airports().stream()
                .collect(Collectors.toMap(AirportView::code, Function.identity()));
        if (!airports.containsKey(origin) || !airports.containsKey(destination) || origin.equals(destination)) {
            throw new BadRequestException("Unknown or identical airports " + origin + "-" + destination);
        }
        int seats = passengers.entrySet().stream()
                .filter(e -> e.getKey().occupiesSeat())
                .mapToInt(Map.Entry::getValue)
                .sum();
        if (seats < 1 || seats > 9) {
            throw new BadRequestException("A booking needs 1 to 9 passengers with seats");
        }

        Set<String> hubs = new LinkedHashSet<>(reference.airlines().stream().map(AirlineView::hub).toList());
        hubs.removeIf(hub -> hub.equals(origin) || hub.equals(destination)
                || !routes.hasService(origin, hub) || !routes.hasService(hub, destination));

        List<AirOffer> offers = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<FlightInventoryView>> nonstop = routes.hasService(origin, destination)
                    ? executor.submit(() -> inventory.inventory(origin, destination, date))
                    : CompletableFuture.completedFuture(List.of());
            List<HubLegs> viaHubs = new ArrayList<>();
            for (String hub : hubs) {
                viaHubs.add(new HubLegs(
                        executor.submit(() -> inventory.inventory(origin, hub, date)),
                        executor.submit(() -> inventory.inventory(hub, destination, date)),
                        executor.submit(() -> inventory.inventory(hub, destination, date.plusDays(1)))));
            }

            for (FlightInventoryView flight : nonstop.get()) {
                select(flight, cabin, seats).ifPresent(s -> offers.add(offer(List.of(s), passengers, airports)));
            }
            for (HubLegs legs : viaHubs) {
                List<FlightInventoryView> firstLegs = legs.first().get();
                if (firstLegs.isEmpty()) {
                    continue;
                }
                List<FlightInventoryView> secondLegs = new ArrayList<>(legs.secondSameDay().get());
                secondLegs.addAll(legs.secondNextDay().get());
                for (FlightInventoryView first : firstLegs) {
                    for (FlightInventoryView second : secondLegs) {
                        Duration connection = Duration.between(first.arrival(), second.departure());
                        if (connection.compareTo(MIN_CONNECTION) < 0 || connection.compareTo(MAX_CONNECTION) > 0) {
                            continue;
                        }
                        var a = select(first, cabin, seats);
                        var b = select(second, cabin, seats);
                        if (a.isPresent() && b.isPresent()) {
                            offers.add(offer(List.of(a.get(), b.get()), passengers, airports));
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Search interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Inventory lookup failed", e.getCause());
        }

        // live seat check with the carriers before the offers go back to the agent
        supplier.confirm("air");
        return offers.stream()
                .sorted(Comparator.comparing((AirOffer o) -> o.price().grandTotal())
                        .thenComparingInt(AirOffer::totalDurationMinutes))
                .limit(MAX_OFFERS)
                .toList();
    }

    private record HubLegs(Future<List<FlightInventoryView>> first, Future<List<FlightInventoryView>> secondSameDay,
                           Future<List<FlightInventoryView>> secondNextDay) {
    }

    /** The cheapest booking class in the cabin that still has enough seats for the party. */
    private static Optional<SegmentOffer> select(FlightInventoryView flight, Cabin cabin, int seats) {
        return flight.classes().stream()
                .filter(c -> c.cabin() == cabin && c.seatsAvailable() >= seats)
                .min(Comparator.comparing(BookingClassView::baseFare))
                .map(c -> {
                    FareRules rules = FareRules.forClass(c.code());
                    return new SegmentOffer(flight.flightId(), flight.flight(), flight.origin(), flight.destination(),
                            flight.departure(), flight.arrival(), flight.durationMinutes(), flight.aircraft(), c.code(),
                            c.cabin(), rules.fareBasis(c.code()), c.seatsAvailable(), c.baseFare(), rules);
                });
    }

    private AirOffer offer(List<SegmentOffer> segments, Map<PassengerType, Integer> passengers,
                           Map<String, AirportView> airports) {
        SegmentOffer first = segments.getFirst();
        SegmentOffer last = segments.getLast();
        long minutes = Duration.between(
                first.departure().atZone(ZoneId.of(airports.get(first.origin()).timezone())),
                last.arrival().atZone(ZoneId.of(airports.get(last.destination()).timezone()))).toMinutes();
        AirPrice price = pricing.priceAir(
                segments.stream().map(s -> new SegmentFare(s.bookingClass(), s.baseFare())).toList(), passengers, "USD");
        return new AirOffer(segments, segments.size() - 1, (int) minutes, price);
    }
}
