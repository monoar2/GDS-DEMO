package com.gds.hotel;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Rooms left of one room type for one night. */
@Entity
@Table(name = "room_inventory", indexes = {
        @Index(name = "ux_room_inventory", columnList = "room_type_id,stay_date", unique = true)
})
public class RoomInventory {

    @Id
    private Long id;

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    private int roomsTotal;
    private int roomsAvailable;

    @Version
    private long version;

    protected RoomInventory() {
    }

    public Long getId() { return id; }
    public Long getRoomTypeId() { return roomTypeId; }
    public LocalDate getStayDate() { return stayDate; }
    public int getRoomsTotal() { return roomsTotal; }
    public int getRoomsAvailable() { return roomsAvailable; }
    public long getVersion() { return version; }
}
