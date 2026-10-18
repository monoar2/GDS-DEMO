package com.gds.booking;

/** HELD: inventory is held until the ticketing deadline. TICKETED: paid. CANCELLED and EXPIRED: inventory released. */
public enum PnrStatus {
    HELD, TICKETED, CANCELLED, EXPIRED
}
