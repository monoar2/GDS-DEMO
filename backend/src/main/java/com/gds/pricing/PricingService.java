package com.gds.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Turns supplier base rates into what the traveller pays. Taxes are mocked but follow the usual shape:
 * a per-segment airport charge plus a percentage, a hotel occupancy tax and a car rental tax.
 */
@Service
public class PricingService {

    private static final BigDecimal SEGMENT_CHARGE = new BigDecimal("18.00");
    private static final BigDecimal AIR_TAX_RATE = new BigDecimal("0.075");
    private static final BigDecimal HOTEL_TAX_RATE = new BigDecimal("0.14");
    private static final BigDecimal CAR_TAX_RATE = new BigDecimal("0.16");

    public record SegmentFare(String bookingClass, BigDecimal baseFare) {
    }

    public record PaxPrice(PassengerType type, int count, BigDecimal baseFare, BigDecimal taxes, BigDecimal totalPerPax) {
    }

    public record AirPrice(List<PaxPrice> passengers, BigDecimal baseTotal, BigDecimal taxTotal, BigDecimal grandTotal,
                           String currency) {
    }

    public record StayPrice(int units, int nights, BigDecimal rate, BigDecimal base, BigDecimal taxes, BigDecimal total,
                            String currency) {
    }

    public AirPrice priceAir(List<SegmentFare> segments, Map<PassengerType, Integer> passengers, String currency) {
        BigDecimal adultBase = segments.stream().map(SegmentFare::baseFare).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaxPrice> lines = new ArrayList<>();
        BigDecimal baseTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (PassengerType type : PassengerType.values()) {
            int count = passengers.getOrDefault(type, 0);
            if (count == 0) {
                continue;
            }
            BigDecimal base = money(adultBase.multiply(type.fareShare()));
            BigDecimal taxes = money(base.multiply(AIR_TAX_RATE)
                    .add(type.occupiesSeat() ? SEGMENT_CHARGE.multiply(BigDecimal.valueOf(segments.size())) : BigDecimal.ZERO));
            lines.add(new PaxPrice(type, count, base, taxes, base.add(taxes)));
            baseTotal = baseTotal.add(base.multiply(BigDecimal.valueOf(count)));
            taxTotal = taxTotal.add(taxes.multiply(BigDecimal.valueOf(count)));
        }
        return new AirPrice(lines, baseTotal, taxTotal, baseTotal.add(taxTotal), currency);
    }

    public StayPrice priceHotel(BigDecimal nightlyRate, int nights, int rooms, String currency) {
        return stay(nightlyRate, nights, rooms, HOTEL_TAX_RATE, currency);
    }

    public StayPrice priceCar(BigDecimal dailyRate, int days, String currency) {
        return stay(dailyRate, days, 1, CAR_TAX_RATE, currency);
    }

    private static StayPrice stay(BigDecimal rate, int nights, int units, BigDecimal taxRate, String currency) {
        BigDecimal base = money(rate.multiply(BigDecimal.valueOf((long) nights * units)));
        BigDecimal taxes = money(base.multiply(taxRate));
        return new StayPrice(units, nights, rate, base, taxes, base.add(taxes), currency);
    }

    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
