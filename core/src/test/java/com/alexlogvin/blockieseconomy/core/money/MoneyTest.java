package com.alexlogvin.blockieseconomy.core.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("micro-units round up so nothing purchasable is free")
    void roundsUpFromMicros() {
        assertEquals(1L, Money.fromMicros(1L));
        assertEquals(1L, Money.fromMicros(Money.MICRO_SCALE));
        assertEquals(2L, Money.fromMicros(Money.MICRO_SCALE + 1L));
        assertEquals(0L, Money.fromMicros(0L));
        assertEquals(0L, Money.fromMicros(-5L));
    }

    @Test
    @DisplayName("payouts round down, so selling never mints a Blockie from nothing")
    void roundsDownForPayouts() {
        assertEquals(0L, Money.fromMicrosFloor(Money.MICRO_SCALE - 1L));
        assertEquals(1L, Money.fromMicrosFloor(Money.MICRO_SCALE));
        assertEquals(1L, Money.fromMicrosFloor(Money.MICRO_SCALE * 3L / 2L));
    }

    @Test
    @DisplayName("rounding is applied once at the edge, not compounded per step")
    void roundingDoesNotCompound() {
        // A log worth 40 split into 4 planks, each split into 4 sticks. Rounding at every
        // step would give 40 -> 10 -> 3 (ceil of 2.5), inflating the stick by 20%.
        long log = Money.toMicros(40L);
        long plank = log / 4L;
        long stick = plank / 4L;
        assertEquals(10L, Money.fromMicros(plank));
        assertEquals(3L, Money.fromMicros(stick)); // 2.5 rounded up, once

        // The exact micro value is preserved until conversion.
        assertEquals(2_500_000L, stick);
    }

    @Test
    @DisplayName("overflow throws rather than silently wrapping the ledger")
    void detectsOverflow() {
        assertThrows(ArithmeticException.class, () -> Money.add(Long.MAX_VALUE, 1L));
        assertThrows(ArithmeticException.class, () -> Money.subtract(Long.MIN_VALUE, 1L));
        assertThrows(ArithmeticException.class, () -> Money.multiply(Long.MAX_VALUE, 2L));
        assertThrows(ArithmeticException.class, () -> Money.toMicros(Long.MAX_VALUE));

        assertEquals(3L, Money.add(1L, 2L));
        assertEquals(-1L, Money.subtract(1L, 2L));
        assertEquals(6L, Money.multiply(2L, 3L));
    }

    @Test
    @DisplayName("scaling by a multiplier rejects nonsense factors")
    void scaleRejectsBadFactors() {
        assertEquals(130L, Money.scale(100L, 1.3d));
        assertEquals(75L, Money.scale(100L, 0.75d));
        assertThrows(IllegalArgumentException.class, () -> Money.scale(100L, -1.0d));
        assertThrows(IllegalArgumentException.class, () -> Money.scale(100L, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Money.scale(100L, Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("clamp keeps balances inside the supported range")
    void clampsBalances() {
        assertEquals(0L, Money.clamp(-1L));
        assertEquals(50L, Money.clamp(50L));
        assertEquals(Money.MAX_BALANCE, Money.clamp(Money.MAX_BALANCE + 1L));
    }
}
