package com.gds.booking;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/** Six-character record locators without look-alike characters (no I, O, 0 or 1). */
@Component
public class RecordLocatorGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final PnrRepository pnrs;

    public RecordLocatorGenerator(PnrRepository pnrs) {
        this.pnrs = pnrs;
    }

    public String next() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = random(6);
            if (!pnrs.existsByRecordLocator(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free record locator");
    }

    /** Hotel and car confirmation numbers from the mock suppliers. */
    public static String confirmation(String prefix) {
        return prefix + random(8);
    }

    private static String random(int length) {
        var random = ThreadLocalRandom.current();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }
}
