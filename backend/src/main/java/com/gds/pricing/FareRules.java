package com.gds.pricing;

import java.math.BigDecimal;

/**
 * Simplified fare conditions per booking class. Flexible fares refund in full, mid fares refund minus a
 * penalty, and the cheapest fares only refund taxes. Tickets voided on the day of issue always refund in full.
 */
public record FareRules(String fareFamily, boolean refundable, BigDecimal cancelPenalty, BigDecimal changeFee) {

    public static FareRules forClass(String bookingClass) {
        return switch (bookingClass) {
            case "F", "J", "Y" -> new FareRules("FLEX", true, BigDecimal.ZERO, BigDecimal.ZERO);
            case "C", "W", "B" -> new FareRules("STANDARD", true, new BigDecimal("100.00"), new BigDecimal("75.00"));
            case "M", "H" -> new FareRules("SAVER", true, new BigDecimal("200.00"), new BigDecimal("150.00"));
            default -> new FareRules("BASIC", false, BigDecimal.ZERO, new BigDecimal("250.00"));
        };
    }

    /** Fare basis code in the usual style: booking class + season + family, e.g. "MLSAVER". */
    public String fareBasis(String bookingClass) {
        return bookingClass + "L" + fareFamily;
    }
}
