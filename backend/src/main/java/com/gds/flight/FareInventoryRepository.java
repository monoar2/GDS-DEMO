package com.gds.flight;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FareInventoryRepository extends JpaRepository<FareInventory, Long> {

    List<FareInventory> findByFlightIdInOrderByFlightIdAscBaseFareDesc(Collection<Long> flightIds);

    Optional<FareInventory> findByFlightIdAndBookingClass(Long flightId, String bookingClass);
}
