package com.alexlogvin.blockieseconomy.core.price;

import com.alexlogvin.blockieseconomy.core.money.Money;

/** A resolved price for one item, with enough provenance for {@code /shop debug price}. */
public final class PriceEntry {

    private final String itemId;
    private final long buyMicros;
    private final PriceSource source;
    private final String recipeId;

    public PriceEntry(String itemId, long buyMicros, PriceSource source, String recipeId) {
        this.itemId = itemId;
        this.buyMicros = buyMicros;
        this.source = source;
        this.recipeId = recipeId;
    }

    public String itemId() {
        return itemId;
    }

    /** Exact value in micro-units, before display rounding. */
    public long buyMicros() {
        return buyMicros;
    }

    /** Buy price in whole Blockies, rounded up so nothing purchasable is free. */
    public long buyPrice() {
        return Money.fromMicros(buyMicros);
    }

    /**
     * Sell price in whole Blockies, rounded down.
     *
     * <p>Computed from {@link #buyPrice()}, not from {@link #buyMicros()}. The exact micro
     * value is the solver's business; the player's is the whole number they are charged,
     * and the sell spread has to be a percentage of that or the shop quotes a price it does
     * not pay. See {@link Money#sellPrice}, which is the single definition both sides use.
     */
    public long sellPrice(double sellMultiplier) {
        return Money.sellPrice(buyPrice(), sellMultiplier);
    }

    public PriceSource source() {
        return source;
    }

    /** Recipe this price was derived from, or {@code null} if it was declared. */
    public String recipeId() {
        return recipeId;
    }

    @Override
    public String toString() {
        return itemId + "=" + buyPrice() + " (" + source
                + (recipeId == null ? "" : " via " + recipeId) + ")";
    }
}
