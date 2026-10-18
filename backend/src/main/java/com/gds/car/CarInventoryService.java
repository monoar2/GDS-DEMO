package com.gds.car;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gds.config.CacheConfig;

@Service
public class CarInventoryService {

    private final CarInventoryRepository cars;

    public CarInventoryService(CarInventoryRepository cars) {
        this.cars = cars;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CAR_INVENTORY, sync = true, key = "#locationCode + ':' + #date")
    public List<CarInventoryView> inventory(String locationCode, LocalDate date) {
        return cars.findByLocationCodeAndPickupDateOrderByDailyRate(locationCode, date).stream()
                .map(c -> new CarInventoryView(c.getVendorCode(), c.getLocationCode(), c.getPickupDate(),
                        c.getSippCode(), c.getSampleModel(), c.getDailyRate(), c.getCurrency(),
                        c.getUnitsAvailable(), c.getUnitsTotal()))
                .toList();
    }

    public record CarInventoryView(String vendor, String location, LocalDate pickupDate, String sipp,
                                   String sampleModel, BigDecimal dailyRate, String currency,
                                   int unitsAvailable, int unitsTotal) {
    }
}
