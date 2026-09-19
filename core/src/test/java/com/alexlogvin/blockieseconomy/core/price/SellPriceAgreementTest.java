package com.alexlogvin.blockieseconomy.core.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexlogvin.blockieseconomy.core.money.Money;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshot;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshotCodec;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shop must pay what it quotes.
 *
 * <p>These exist because it once did not. The server took the sell spread off the solver's
 * exact micro value and the client took it off the rounded buy price, so an item worth 3.4
 * was bought for 4, shown as selling for 3, and paid out 2 — on 92 of 946 vanilla items.
 * Two copies of one formula is the bug; these tests hold them to being one.
 */
class SellPriceAgreementTest {

    private static final double SELL = 0.75;

    private static ShopSnapshot snapshotOf(String itemId, long buyPrice) {
        TreeMap<String, Long> prices = new TreeMap<String, Long>();
        prices.put(itemId, Long.valueOf(buyPrice));
        return new ShopSnapshot(prices, SELL, ShopSnapshotCodec.hash(prices, SELL));
    }

    @Test
    @DisplayName("what the client shows is what the server pays, for every fractional value")
    void clientAndServerAgree() {
        // Steps of 1000 micros sweep every fractional part that rounding can produce, which
        // is exactly where the two formulas used to part company.
        for (long micros = 1_000L; micros <= 40_000_000L; micros += 1_000L) {
            PriceEntry entry = new PriceEntry("test:item", micros, PriceSource.DERIVED, null);

            long shown = snapshotOf("test:item", entry.buyPrice()).sellPrice("test:item");
            long paid = entry.sellPrice(SELL);

            assertEquals(shown, paid,
                    "client and server disagree at " + micros + " micros (buy "
                            + entry.buyPrice() + ")");
        }
    }

    @Test
    @DisplayName("the total for a stack is the unit price times the count, exactly")
    void totalsAreTheUnitPriceTimesCount() {
        // The shop shows "+300" above the Sell button by multiplying the unit price it
        // displays. If the payout rounded the total instead, the error would scale with the
        // quantity: the report that found this described 300 shown and 200 paid.
        for (long micros = 1_000L; micros <= 10_000_000L; micros += 7_000L) {
            PriceEntry entry = new PriceEntry("test:item", micros, PriceSource.DERIVED, null);
            long unit = entry.sellPrice(SELL);

            for (int count : new int[] {1, 7, 64, 100, 1_000}) {
                assertEquals(unit * count, TradeCalculator.sellTotal(entry, count, SELL),
                        "stack of " + count + " at " + micros + " micros");
            }
        }
    }

    @Test
    @DisplayName("the exact case from the bug report: a pane bought for 4 pays 3, not 2")
    void theReportedCase() {
        // minecraft:brown_stained_glass_pane, whose solved value is fractional.
        PriceEntry pane = new PriceEntry("minecraft:brown_stained_glass_pane",
                3_400_000L, PriceSource.DERIVED, null);

        assertEquals(4L, pane.buyPrice(), "rounded up, so nothing purchasable is free");
        assertEquals(3L, pane.sellPrice(SELL), "the shop pays what it displayed");
        assertEquals(300L, TradeCalculator.sellTotal(pane, 100, SELL));
    }

    @Test
    @DisplayName("an undamaged tool pays the same as the quoted price")
    void undamagedToolPaysTheQuotedPrice() {
        // sellTotalDamaged has its own arithmetic, so it needs its own guard: at full
        // durability it must agree with the plain path rather than losing a Blockie to
        // integer division.
        PriceEntry pickaxe = new PriceEntry("minecraft:iron_pickaxe",
                457_000_000L, PriceSource.DERIVED, null);

        assertEquals(TradeCalculator.sellTotal(pickaxe, 1, SELL),
                TradeCalculator.sellTotalDamaged(pickaxe, 1, SELL, 250, 250));
    }

    @Test
    @DisplayName("a half-worn tool is worth about half, and never more than a fresh one")
    void damagedToolIsProRated() {
        PriceEntry pickaxe = new PriceEntry("minecraft:iron_pickaxe",
                457_000_000L, PriceSource.DERIVED, null);
        long fresh = TradeCalculator.sellTotalDamaged(pickaxe, 1, SELL, 250, 250);
        long half = TradeCalculator.sellTotalDamaged(pickaxe, 1, SELL, 125, 250);
        long broken = TradeCalculator.sellTotalDamaged(pickaxe, 1, SELL, 0, 250);

        assertTrue(half < fresh, "a worn tool must be worth less");
        assertTrue(Math.abs(half * 2 - fresh) <= 2, "half durability is about half the value");
        assertEquals(0L, broken);
    }

    @Test
    @DisplayName("selling never pays more than buying costs, at any price")
    void spreadNeverInverts() {
        // The invariant the whole economy rests on. Raising the sell price to match what is
        // displayed must not push it past the buy price on cheap items, where rounding has
        // the most leverage.
        for (long micros = 1L; micros <= 20_000_000L; micros += 999L) {
            PriceEntry entry = new PriceEntry("test:item", micros, PriceSource.DERIVED, null);
            assertTrue(entry.sellPrice(SELL) < entry.buyPrice() || entry.buyPrice() == 0L,
                    "sell " + entry.sellPrice(SELL) + " >= buy " + entry.buyPrice()
                            + " at " + micros + " micros");
        }
    }

    @Test
    @DisplayName("a large price keeps every digit")
    void largePricesDoNotLosePrecision() {
        // Above 2^53 a double cannot hold the value exactly, so the shared formula works in
        // micro-units instead. A near-maximum balance must not quietly lose its low digits.
        assertEquals(750_000_000_000L, Money.sellPrice(1_000_000_000_000L, SELL));
        assertEquals(0L, Money.sellPrice(0L, SELL));
        assertEquals(0L, Money.sellPrice(-5L, SELL));
    }
}
