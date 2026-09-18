package com.alexlogvin.blockieseconomy.core.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexlogvin.blockieseconomy.core.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TradeCalculatorTest {

    private static PriceEntry priced(long blockies) {
        return new PriceEntry("minecraft:test", Money.toMicros(blockies),
                PriceSource.MANUAL, null);
    }

    @Test
    @DisplayName("totals are the unit price times the count, so the shown price never drifts")
    void totalsAreUnitTimesCount() {
        PriceEntry entry = priced(78L);

        assertEquals(78L, TradeCalculator.buyTotal(entry, 1));
        assertEquals(4_992L, TradeCalculator.buyTotal(entry, 64));

        // Sell rounds down per unit: 78 * 0.75 = 58.5 -> 58.
        assertEquals(58L, TradeCalculator.sellTotal(entry, 1, 0.75d));
        assertEquals(3_712L, TradeCalculator.sellTotal(entry, 64, 0.75d));
    }

    @Test
    @DisplayName("buying a stack then selling it back always loses money")
    void roundTripLosesMoney() {
        for (long price = 1L; price <= 3_000L; price += 37L) {
            PriceEntry entry = priced(price);
            long bought = TradeCalculator.buyTotal(entry, 64);
            long sold = TradeCalculator.sellTotal(entry, 64, 0.75d);
            assertTrue(sold < bought,
                    "at " + price + " a stack cost " + bought + " but sold for " + sold);
        }
    }

    @Test
    @DisplayName("a damaged tool sells pro-rata to its remaining durability")
    void damagedItemsSellForLess() {
        PriceEntry pickaxe = priced(400L);

        // Undamaged: full sell price.
        assertEquals(300L, TradeCalculator.sellTotalDamaged(pickaxe, 1, 0.75d, 250, 250));
        // Half worn.
        assertEquals(150L, TradeCalculator.sellTotalDamaged(pickaxe, 1, 0.75d, 125, 250));
        // Nearly broken pays almost nothing, which is the point.
        assertEquals(3L, TradeCalculator.sellTotalDamaged(pickaxe, 1, 0.75d, 3, 250));
        assertEquals(0L, TradeCalculator.sellTotalDamaged(pickaxe, 1, 0.75d, 0, 250));
    }

    @Test
    @DisplayName("buying a tool, wearing it out and selling it back cannot turn a profit")
    void wornToolCannotBeFlipped() {
        PriceEntry pickaxe = priced(400L);
        long cost = TradeCalculator.buyTotal(pickaxe, 1);

        for (int remaining = 0; remaining <= 250; remaining += 25) {
            long payout = TradeCalculator.sellTotalDamaged(pickaxe, 1, 0.75d, remaining, 250);
            assertTrue(payout < cost,
                    "at " + remaining + "/250 durability the payout " + payout
                            + " must stay below the " + cost + " purchase price");
        }
    }

    @Test
    @DisplayName("an item with no durability is unaffected by the pro-rating path")
    void nonDamageableItemsIgnoreDurability() {
        PriceEntry dirt = priced(5L);
        assertEquals(TradeCalculator.sellTotal(dirt, 10, 0.75d),
                TradeCalculator.sellTotalDamaged(dirt, 10, 0.75d, 0, 0));
    }

    @Test
    @DisplayName("max buy reports what the balance actually covers, capped by stack size")
    void maxAffordable() {
        PriceEntry entry = priced(78L);

        assertEquals(0, TradeCalculator.maxAffordable(77L, entry, 64));
        assertEquals(1, TradeCalculator.maxAffordable(78L, entry, 64));
        assertEquals(12, TradeCalculator.maxAffordable(1_000L, entry, 64));
        // The cap wins when the player could afford more than a stack.
        assertEquals(64, TradeCalculator.maxAffordable(1_000_000L, entry, 64));
        assertEquals(0, TradeCalculator.maxAffordable(0L, entry, 64));
    }

    @Test
    @DisplayName("counts must be positive")
    void rejectsNonPositiveCounts() {
        PriceEntry entry = priced(10L);
        assertThrows(IllegalArgumentException.class, () -> TradeCalculator.buyTotal(entry, 0));
        assertThrows(IllegalArgumentException.class, () -> TradeCalculator.buyTotal(entry, -1));
        assertThrows(IllegalArgumentException.class,
                () -> TradeCalculator.sellTotal(entry, 0, 0.75d));
    }
}
