package com.alexlogvin.blockieseconomy.economy;

/**
 * Result of an attempted buy or sell.
 *
 * <p>Carries a {@link Reason} rather than a message: the text has to be translated, and
 * the same outcome is reported through chat, the shop screen and a recipe-viewer button.
 */
public final class TradeOutcome {

    public enum Reason {
        OK,
        /** The price table has not finished building yet. */
        NOT_READY,
        /** No price, blacklisted, or excluded by the whitelist. */
        NOT_TRADEABLE,
        /** Not enough Blockies. */
        INSUFFICIENT_FUNDS,
        /** The player does not hold enough of the item to sell. */
        INSUFFICIENT_ITEMS,
        /** Enchanted, renamed, potion-filled, or a container with contents. */
        NON_DEFAULT_COMPONENTS,
        /** Too many trades in the configured window. */
        RATE_LIMITED,
        /** Amount was zero, negative, or absurd. */
        INVALID_AMOUNT,
        /** The item id does not exist in this version. */
        UNKNOWN_ITEM
    }

    private final Reason reason;
    private final String itemId;
    private final int count;
    private final long total;
    private final long balanceAfter;
    private final int droppedOnGround;

    private TradeOutcome(Reason reason, String itemId, int count, long total,
                         long balanceAfter, int droppedOnGround) {
        this.reason = reason;
        this.itemId = itemId;
        this.count = count;
        this.total = total;
        this.balanceAfter = balanceAfter;
        this.droppedOnGround = droppedOnGround;
    }

    public static TradeOutcome ok(String itemId, int count, long total, long balanceAfter,
                                  int droppedOnGround) {
        return new TradeOutcome(Reason.OK, itemId, count, total, balanceAfter, droppedOnGround);
    }

    public static TradeOutcome failure(Reason reason, String itemId) {
        return new TradeOutcome(reason, itemId, 0, 0L, 0L, 0);
    }

    /** Failure carrying the amount that was asked for, for a clearer message. */
    public static TradeOutcome failure(Reason reason, String itemId, int count, long total) {
        return new TradeOutcome(reason, itemId, count, total, 0L, 0);
    }

    public boolean succeeded() {
        return reason == Reason.OK;
    }

    public Reason reason() {
        return reason;
    }

    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }

    /** Blockies spent (buy) or received (sell). */
    public long total() {
        return total;
    }

    public long balanceAfter() {
        return balanceAfter;
    }

    /**
     * How many items would not fit in the inventory and were dropped at the player's feet.
     * Zero in the normal case; the caller mentions it only when nonzero.
     */
    public int droppedOnGround() {
        return droppedOnGround;
    }
}
