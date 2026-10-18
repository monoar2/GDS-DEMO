package com.gds.booking;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gds.common.ConflictException;
import com.gds.config.CacheConfig;

/**
 * Takes and gives back supplier inventory with single conditional UPDATEs ("decrement only if enough is left").
 * The database does the check and the write in one step, so thousands of concurrent bookings can never oversell
 * a flight, and nobody has to retry on optimistic-lock failures. Must run inside the booking transaction:
 * if any part of a booking fails, the whole transaction rolls back and every decrement with it.
 * Cached availability for the touched routes and days is evicted once the transaction commits.
 */
@Component
public class InventoryAllocator {

    private final JdbcTemplate jdbc;
    private final CacheManager caches;

    public InventoryAllocator(JdbcTemplate jdbc, CacheManager caches) {
        this.jdbc = jdbc;
        this.caches = caches;
    }

    public void holdSeats(AirSegment segment) {
        int updated = jdbc.update("""
                update fare_inventory set seats_available = seats_available - ?, version = version + 1
                where id = ? and seats_available >= ?""",
                segment.getSeats(), segment.getFareInventoryId(), segment.getSeats());
        if (updated == 0) {
            throw new ConflictException("Class %s on %s %s is sold out for %d seat(s)".formatted(
                    segment.getBookingClass(), segment.getFlight(), segment.getDeparture().toLocalDate(), segment.getSeats()));
        }
        evictAfterCommit(CacheConfig.FLIGHT_INVENTORY, flightKey(segment));
    }

    public void releaseSeats(AirSegment segment) {
        jdbc.update("""
                update fare_inventory set seats_available = least(seats_total, seats_available + ?), version = version + 1
                where id = ?""", segment.getSeats(), segment.getFareInventoryId());
        evictAfterCommit(CacheConfig.FLIGHT_INVENTORY, flightKey(segment));
    }

    public void holdRooms(HotelSegment segment) {
        int nights = (int) segment.getCheckIn().datesUntil(segment.getCheckOut()).count();
        int updated = jdbc.update("""
                update room_inventory set rooms_available = rooms_available - ?, version = version + 1
                where room_type_id = ? and stay_date >= ? and stay_date < ? and rooms_available >= ?""",
                segment.getRooms(), segment.getRoomTypeId(), Date.valueOf(segment.getCheckIn()),
                Date.valueOf(segment.getCheckOut()), segment.getRooms());
        if (updated != nights) {
            throw new ConflictException("%s %s has no %d room(s) left on every night from %s to %s".formatted(
                    segment.getHotelName(), segment.getRoomCode(), segment.getRooms(), segment.getCheckIn(), segment.getCheckOut()));
        }
        evictDays(CacheConfig.HOTEL_INVENTORY, segment.getCityCode(), segment.getCheckIn(), segment.getCheckOut());
    }

    public void releaseRooms(HotelSegment segment) {
        jdbc.update("""
                update room_inventory set rooms_available = least(rooms_total, rooms_available + ?), version = version + 1
                where room_type_id = ? and stay_date >= ? and stay_date < ?""",
                segment.getRooms(), segment.getRoomTypeId(), Date.valueOf(segment.getCheckIn()), Date.valueOf(segment.getCheckOut()));
        evictDays(CacheConfig.HOTEL_INVENTORY, segment.getCityCode(), segment.getCheckIn(), segment.getCheckOut());
    }

    public void holdCar(CarSegment segment) {
        int days = (int) segment.getPickupDate().datesUntil(segment.getReturnDate()).count();
        int updated = jdbc.update("""
                update car_inventory set units_available = units_available - 1, version = version + 1
                where vendor_code = ? and location_code = ? and sipp_code = ?
                  and pickup_date >= ? and pickup_date < ? and units_available >= 1""",
                segment.getVendorCode(), segment.getLocationCode(), segment.getSippCode(),
                Date.valueOf(segment.getPickupDate()), Date.valueOf(segment.getReturnDate()));
        if (updated != days) {
            throw new ConflictException("%s has no %s left at %s for every day from %s to %s".formatted(
                    segment.getVendorCode(), segment.getSippCode(), segment.getLocationCode(),
                    segment.getPickupDate(), segment.getReturnDate()));
        }
        evictDays(CacheConfig.CAR_INVENTORY, segment.getLocationCode(), segment.getPickupDate(), segment.getReturnDate());
    }

    public void releaseCar(CarSegment segment) {
        jdbc.update("""
                update car_inventory set units_available = least(units_total, units_available + 1), version = version + 1
                where vendor_code = ? and location_code = ? and sipp_code = ? and pickup_date >= ? and pickup_date < ?""",
                segment.getVendorCode(), segment.getLocationCode(), segment.getSippCode(),
                Date.valueOf(segment.getPickupDate()), Date.valueOf(segment.getReturnDate()));
        evictDays(CacheConfig.CAR_INVENTORY, segment.getLocationCode(), segment.getPickupDate(), segment.getReturnDate());
    }

    private static String flightKey(AirSegment segment) {
        return segment.getOrigin() + ":" + segment.getDestination() + ":" + segment.getDeparture().toLocalDate();
    }

    private void evictDays(String cache, String place, LocalDate from, LocalDate until) {
        Set<String> keys = new LinkedHashSet<>();
        from.datesUntil(until).forEach(day -> keys.add(place + ":" + day));
        keys.forEach(key -> evictAfterCommit(cache, key));
    }

    private void evictAfterCommit(String cacheName, String key) {
        Runnable evict = () -> {
            var cache = caches.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
