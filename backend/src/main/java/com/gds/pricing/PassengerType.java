package com.gds.pricing;

import java.math.BigDecimal;

/** Adult, child (2-11) and infant on lap (under 2, no seat). */
public enum PassengerType {
    ADT(new BigDecimal("1.00"), true),
    CHD(new BigDecimal("0.75"), true),
    INF(new BigDecimal("0.10"), false);

    private final BigDecimal fareShare;
    private final boolean occupiesSeat;

    PassengerType(BigDecimal fareShare, boolean occupiesSeat) {
        this.fareShare = fareShare;
        this.occupiesSeat = occupiesSeat;
    }

    public BigDecimal fareShare() { return fareShare; }
    public boolean occupiesSeat() { return occupiesSeat; }
}
