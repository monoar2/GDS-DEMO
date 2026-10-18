package com.gds.hotel;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    List<Hotel> findByCityCodeOrderByStarsDescNameAsc(String cityCode);

    @Query("""
            select new com.gds.hotel.RoomRow(
                rt.hotelId, rt.id, rt.code, rt.description, rt.maxOccupancy, rt.nightlyRate, rt.currency,
                ri.roomsAvailable, ri.roomsTotal)
            from RoomType rt, RoomInventory ri
            where ri.roomTypeId = rt.id and rt.hotelId in :hotelIds and ri.stayDate = :date
            order by rt.hotelId, rt.nightlyRate
            """)
    List<RoomRow> roomsFor(@Param("hotelIds") List<Long> hotelIds, @Param("date") LocalDate date);
}
