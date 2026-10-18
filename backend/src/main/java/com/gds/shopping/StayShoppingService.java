package com.gds.shopping;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import org.springframework.stereotype.Service;

import com.gds.car.CarInventoryService;
import com.gds.car.CarInventoryService.CarInventoryView;
import com.gds.common.BadRequestException;
import com.gds.hotel.HotelInventoryService;
import com.gds.hotel.HotelInventoryService.HotelInventoryView;
import com.gds.hotel.HotelInventoryService.RoomView;
import com.gds.pricing.PricingService;
import com.gds.pricing.PricingService.StayPrice;
import com.gds.supplier.SupplierGateway;

/**
 * Hotel and car shopping. Both need inventory on every night or day of the stay, so each day is looked up
 * in parallel on virtual threads and only options available on all of them are offered.
 */
@Service
public class StayShoppingService {

    private static final int MAX_NIGHTS = 30;

    private final HotelInventoryService hotels;
    private final CarInventoryService cars;
    private final PricingService pricing;
    private final SupplierGateway supplier;

    public StayShoppingService(HotelInventoryService hotels, CarInventoryService cars, PricingService pricing,
                               SupplierGateway supplier) {
        this.hotels = hotels;
        this.cars = cars;
        this.pricing = pricing;
        this.supplier = supplier;
    }

    public record RoomOffer(Long roomTypeId, String code, String description, int maxOccupancy, int roomsLeft,
                            StayPrice price) {
    }

    public record HotelOffer(Long hotelId, String propertyCode, String chain, String name, String city, int stars,
                             String address, List<RoomOffer> rooms) {
    }

    public record CarOffer(String vendor, String location, LocalDate pickupDate, LocalDate returnDate, String sipp,
                           String sampleModel, int unitsLeft, StayPrice price) {
    }

    public List<HotelOffer> hotels(String city, LocalDate checkIn, LocalDate checkOut, int rooms, int guests) {
        int nights = nights(checkIn, checkOut);
        if (rooms < 1 || guests < 1 || guests > rooms * 4) {
            throw new BadRequestException("Need at least one room and guest, and at most 4 guests per room");
        }
        List<List<HotelInventoryView>> perNight = parallel(nights, n -> hotels.inventory(city, checkIn.plusDays(n)));

        // rooms left per room type = the minimum across all nights of the stay
        Map<Long, Integer> roomsLeft = new HashMap<>();
        perNight.getFirst().forEach(h -> h.rooms().forEach(r -> roomsLeft.put(r.roomTypeId(), r.roomsAvailable())));
        for (List<HotelInventoryView> night : perNight.subList(1, perNight.size())) {
            Map<Long, Integer> tonight = new HashMap<>();
            night.forEach(h -> h.rooms().forEach(r -> tonight.put(r.roomTypeId(), r.roomsAvailable())));
            roomsLeft.replaceAll((id, left) -> Math.min(left, tonight.getOrDefault(id, 0)));
        }

        List<HotelOffer> offers = new ArrayList<>();
        for (HotelInventoryView hotel : perNight.getFirst()) {
            List<RoomOffer> available = new ArrayList<>();
            for (RoomView room : hotel.rooms()) {
                int left = roomsLeft.getOrDefault(room.roomTypeId(), 0);
                if (left >= rooms && room.maxOccupancy() * rooms >= guests) {
                    available.add(new RoomOffer(room.roomTypeId(), room.code(), room.description(), room.maxOccupancy(),
                            left, pricing.priceHotel(room.nightlyRate(), nights, rooms, room.currency())));
                }
            }
            if (!available.isEmpty()) {
                offers.add(new HotelOffer(hotel.hotelId(), hotel.propertyCode(), hotel.chain(), hotel.name(), hotel.city(),
                        hotel.stars(), hotel.address(), available));
            }
        }
        offers.sort(Comparator.comparing((HotelOffer h) -> h.rooms().getFirst().price().total()));
        supplier.confirm("hotel");  // rate confirmation with the properties
        return offers;
    }

    public List<CarOffer> cars(String location, LocalDate pickup, int days) {
        nights(pickup, pickup.plusDays(days));
        List<List<CarInventoryView>> perDay = parallel(days, d -> cars.inventory(location, pickup.plusDays(d)));

        supplier.confirm("car");  // availability check with the vendors
        Map<String, Integer> unitsLeft = new HashMap<>();
        perDay.getFirst().forEach(c -> unitsLeft.put(c.vendor() + c.sipp(), c.unitsAvailable()));
        for (List<CarInventoryView> day : perDay.subList(1, perDay.size())) {
            Map<String, Integer> today = new HashMap<>();
            day.forEach(c -> today.put(c.vendor() + c.sipp(), c.unitsAvailable()));
            unitsLeft.replaceAll((key, left) -> Math.min(left, today.getOrDefault(key, 0)));
        }

        return perDay.getFirst().stream()
                .filter(c -> unitsLeft.getOrDefault(c.vendor() + c.sipp(), 0) > 0)
                .map(c -> new CarOffer(c.vendor(), c.location(), pickup, pickup.plusDays(days), c.sipp(), c.sampleModel(),
                        unitsLeft.get(c.vendor() + c.sipp()), pricing.priceCar(c.dailyRate(), days, c.currency())))
                .sorted(Comparator.comparing((CarOffer o) -> o.price().total()))
                .toList();
    }

    static int nights(LocalDate from, LocalDate to) {
        long nights = ChronoUnit.DAYS.between(from, to);
        if (nights < 1 || nights > MAX_NIGHTS) {
            throw new BadRequestException("Stay must be 1 to " + MAX_NIGHTS + " nights or days");
        }
        return (int) nights;
    }

    private static <T> List<T> parallel(int count, IntFunction<T> lookup) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> lookup.apply(index)));
            }
            List<T> results = new ArrayList<>(count);
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Search interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Inventory lookup failed", e.getCause());
        }
    }
}
