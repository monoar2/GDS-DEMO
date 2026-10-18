package com.gds.hotel;

import java.math.BigDecimal;

/** Flat projection of a room type joined with one night of its inventory. */
public record RoomRow(Long hotelId, Long roomTypeId, String code, String description, int maxOccupancy,
                      BigDecimal nightlyRate, String currency, int roomsAvailable, int roomsTotal) {
}
