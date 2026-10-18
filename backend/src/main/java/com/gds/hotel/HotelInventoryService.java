package com.gds.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gds.config.CacheConfig;

@Service
public class HotelInventoryService {

    private final HotelRepository hotels;

    public HotelInventoryService(HotelRepository hotels) {
        this.hotels = hotels;
    }

    /** Hotels in a city with the rooms left of each room type for one night. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.HOTEL_INVENTORY, sync = true, key = "#cityCode + ':' + #date")
    public List<HotelInventoryView> inventory(String cityCode, LocalDate date) {
        List<Hotel> inCity = hotels.findByCityCodeOrderByStarsDescNameAsc(cityCode);
        if (inCity.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RoomRow>> rooms = hotels.roomsFor(inCity.stream().map(Hotel::getId).toList(), date)
                .stream()
                .collect(Collectors.groupingBy(RoomRow::hotelId));

        return inCity.stream()
                .map(h -> new HotelInventoryView(h.getId(), h.getPropertyCode(), h.getChainCode(), h.getName(),
                        h.getCityCode(), h.getStars(), h.getAddress(),
                        rooms.getOrDefault(h.getId(), List.of()).stream()
                                .map(r -> new RoomView(r.roomTypeId(), r.code(), r.description(), r.maxOccupancy(), r.nightlyRate(),
                                        r.currency(), r.roomsAvailable(), r.roomsTotal()))
                                .toList()))
                .toList();
    }

    public record HotelInventoryView(Long hotelId, String propertyCode, String chain, String name, String city,
                                     int stars, String address, List<RoomView> rooms) {
    }

    public record RoomView(Long roomTypeId, String code, String description, int maxOccupancy, BigDecimal nightlyRate,
                           String currency, int roomsAvailable, int roomsTotal) {
    }
}
