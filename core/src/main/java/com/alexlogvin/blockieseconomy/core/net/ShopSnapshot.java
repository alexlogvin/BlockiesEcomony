package com.alexlogvin.blockieseconomy.core.net;

import com.alexlogvin.blockieseconomy.core.money.Money;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The price table as the client sees it.
 *
 * <p>A deliberately thinner thing than the server's {@code PriceTable}: the client needs
 * an item id, a buy price and the sell multiplier, and nothing else. Provenance, recipe
 * ids and the skipped-recipe list stay on the server, which roughly halves what has to
 * cross the wire and keeps the client from displaying internals it cannot act on.
 *
 * <p>Sorted by item id, so the hash is a function of content rather than of the order the
 * solver happened to finish in.
 */
public final class ShopSnapshot {

    private final TreeMap<String, Long> buyPrices;
    private final double sellMultiplier;
    private final byte[] hash;

    public ShopSnapshot(Map<String, Long> buyPrices, double sellMultiplier, byte[] hash) {
        this.buyPrices = new TreeMap<String, Long>(buyPrices);
        this.sellMultiplier = sellMultiplier;
        this.hash = hash;
    }

    /** Sorted item id to buy price in whole Blockies. */
    public Map<String, Long> buyPrices() {
        return Collections.unmodifiableMap(buyPrices);
    }

    public double sellMultiplier() {
        return sellMultiplier;
    }

    /** Content hash, used to skip a resend when the client already has this table. */
    public byte[] hash() {
        return hash;
    }

    public int size() {
        return buyPrices.size();
    }

    public boolean has(String itemId) {
        return buyPrices.containsKey(itemId);
    }

    /** Buy price, or -1 when the item is not tradeable. */
    public long buyPrice(String itemId) {
        Long price = buyPrices.get(itemId);
        return price == null ? -1L : price.longValue();
    }

    /**
     * Sell price for one unit, rounded down.
     *
     * <p>Delegates to {@link Money#sellPrice} — the same call the server makes when it
     * actually pays — so the figure shown before a trade is the figure the player gets.
     * This class once had its own copy of the arithmetic, and the two drifted: the server
     * took the spread off the solver's hidden micro value while this took it off the whole
     * buy price, and 92 of 946 vanilla items were quoted a price the server would not
     * honour. Display code must never re-derive a number the server will re-derive too.
     */
    public long sellPrice(String itemId) {
        long buy = buyPrice(itemId);
        if (buy < 0L) {
            return -1L;
        }
        return Money.sellPrice(buy, sellMultiplier);
    }
}
