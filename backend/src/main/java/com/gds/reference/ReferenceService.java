package com.gds.reference;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.gds.config.CacheConfig;

@Service
public class ReferenceService {

    private final AirportRepository airports;
    private final AirlineRepository airlines;

    public ReferenceService(AirportRepository airports, AirlineRepository airlines) {
        this.airports = airports;
        this.airlines = airlines;
    }

    @Cacheable(cacheNames = CacheConfig.AIRPORTS, sync = true, key = "'all'")
    public List<AirportView> airports() {
        return airports.findAll(Sort.by("code")).stream()
                .map(a -> new AirportView(a.getCode(), a.getName(), a.getCity(), a.getCountry(), a.getTimezone()))
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.AIRLINES, sync = true, key = "'all'")
    public List<AirlineView> airlines() {
        return airlines.findAll(Sort.by("code")).stream()
                .map(a -> new AirlineView(a.getCode(), a.getName(), a.getCountry(), a.getHub()))
                .toList();
    }

    public record AirportView(String code, String name, String city, String country, String timezone) {
    }

    public record AirlineView(String code, String name, String country, String hub) {
    }
}
