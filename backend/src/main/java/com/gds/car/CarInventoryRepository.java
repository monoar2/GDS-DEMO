package com.gds.car;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarInventoryRepository extends JpaRepository<CarInventory, Long> {

    List<CarInventory> findByLocationCodeAndPickupDateOrderByDailyRate(String locationCode, LocalDate pickupDate);

    Optional<CarInventory> findByVendorCodeAndLocationCodeAndSippCodeAndPickupDate(String vendorCode, String locationCode,
                                                                                  String sippCode, LocalDate pickupDate);
}
