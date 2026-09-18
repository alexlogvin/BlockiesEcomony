package com.alexlogvin.blockieseconomy.core.price;

import com.alexlogvin.blockieseconomy.core.money.Money;

/**
 * Totals for a proposed buy or sell.
 *
 * <p>Kept separate from the {@link com.alexlogvin.blockieseconomy.core.ledger.Ledger} so
 * the arithmetic can be unit-tested and so the client can compute the same figures for
 * display. The client's answer is advisory only: the server recomputes every total before
 * touching a balance.
 */
public final class TradeCalculator {

    private TradeCalculator() {
    }

    /**
     * Cost of buying {@code count} items, rounded up per unit.
     *
     * <p>Per-unit rather than on the total, so the price shown for one item multiplied by
     * the quantity always equals what the player is actually charged. Rounding the total
     * instead would make the unit price appear to drift with quantity.
     */
    public static long buyTotal(PriceEntry entry, int count) {
        requirePositive(count);
        return Money.multiply(entry.buyPrice(), count);
    }

    /** Payout for selling {@code count} undamaged items, rounded down per unit. */
    public static long sellTotal(PriceEntry entry, int count, double sellMultiplier) {
        requirePositive(count);
        return Money.multiply(entry.sellPrice(sellMultiplier), count);
    }

    /**
     * Payout for a damaged item, pro-rated by remaining durability.
     *
     * <p>A pickaxe at 10% durability is worth a tenth of a fresh one. Without this a
     * player could buy tools, use them almost to breaking, and sell them back at full
     * price — the sell spread alone would not cover the gap for cheap tools.
     *
     * @param remaining remaining durability, 0 to {@code max}
     * @param max       the item's maximum durability; 0 or less means it is not damageable
     */
    public static long sellTotalDamaged(PriceEntry entry, int count, double sellMultiplier,
                                        int remaining, int max) {
        requirePositive(count);
        if (max <= 0) {
            return sellTotal(entry, count, sellMultiplier);
        }
        int clamped = Math.max(0, Math.min(remaining, max));
        long full = Money.scale(entry.buyMicros(), sellMultiplier);
        long prorated = full / max * clamped;
        return Money.multiply(Money.fromMicrosFloor(prorated), count);
    }

    /**
     * How many of an item a balance can buy. Zero when even one is unaffordable.
     *
     * <p>Backs the "max buy" button in the shop.
     */
    public static int maxAffordable(long balance, PriceEntry entry, int cap) {
        long unit = entry.buyPrice();
        if (unit <= 0L || balance < unit) {
            return 0;
        }
        long affordable = balance / unit;
        return (int) Math.min(affordable, (long) Math.max(0, cap));
    }

    private static void requirePositive(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
    }
}
