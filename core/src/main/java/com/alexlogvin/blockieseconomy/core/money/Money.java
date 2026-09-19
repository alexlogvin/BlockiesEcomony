package com.alexlogvin.blockieseconomy.core.money;

/**
 * Blockies arithmetic.
 *
 * <p>Balances and prices are whole Blockies stored in a {@code long}. The price solver,
 * however, works in <em>micro-units</em> ({@link #MICRO_SCALE} per Blockie) and only
 * rounds at the edge. That matters: rounding every intermediate value up would inflate
 * cheap items, and a divide-by-output recipe could then turn one cheap input into
 * several outputs worth more than it — a money loop. Keeping the fractional part until
 * the final conversion removes that whole class of bug.
 *
 * <p>All arithmetic here is overflow-checked. A ledger that silently wraps around is
 * worse than one that refuses the transaction.
 */
public final class Money {

    /** Micro-units per whole Blockie. */
    public static final long MICRO_SCALE = 1_000_000L;

    /**
     * Largest balance the mod will hold. Well below {@code Long.MAX_VALUE} so that
     * intermediate products (balance times a stack count, say) cannot overflow.
     */
    public static final long MAX_BALANCE = 1_000_000_000_000_000L;

    private Money() {
    }

    public static long toMicros(long blockies) {
        if (blockies > Long.MAX_VALUE / MICRO_SCALE || blockies < Long.MIN_VALUE / MICRO_SCALE) {
            throw new ArithmeticException("value too large to express in micro-units: " + blockies);
        }
        return blockies * MICRO_SCALE;
    }

    /**
     * Converts micro-units to whole Blockies, rounding <em>up</em>.
     *
     * <p>Rounding up means nothing purchasable is ever free: an item worth 0.2 Blockies
     * costs 1, not 0. Because the solver stays in micro-units until this point, the
     * inflation is applied once rather than compounding through a recipe chain.
     */
    public static long fromMicros(long micros) {
        if (micros <= 0L) {
            return 0L;
        }
        long whole = micros / MICRO_SCALE;
        return (micros % MICRO_SCALE == 0L) ? whole : whole + 1L;
    }

    /** Converts micro-units to whole Blockies, rounding <em>down</em>. Used for payouts. */
    public static long fromMicrosFloor(long micros) {
        return micros <= 0L ? 0L : micros / MICRO_SCALE;
    }

    /**
     * The sell price of an item, from the buy price the player can actually see.
     *
     * <p><b>The one definition.</b> Both the server, when it pays a player, and the client,
     * when it shows a price, must call this and nothing else. They used to compute it
     * separately — the server from the solver's exact micro value, the client from the
     * rounded buy price — and for any item whose true value was fractional the two
     * disagreed. A stained glass pane priced at 3.4 was bought for 4, displayed as selling
     * for 3, and paid out 2. That is not a rounding quirk; it is the shop quoting a price
     * it does not honour.
     *
     * <p>Deriving from the whole buy price rather than the micro value is the deliberate
     * half of the fix. The buy price is the number the player is charged, so it is the only
     * honest thing for the spread to be a percentage <em>of</em>. Taking the spread off a
     * hidden fraction instead means a 4-Blockie item can pay 2 — a 50% spread wearing a 25%
     * label.
     *
     * <p>Rounds down, so a 1-Blockie item never sells for 1 and the spread cannot invert.
     */
    public static long sellPrice(long buyPrice, double sellMultiplier) {
        if (buyPrice <= 0L || sellMultiplier <= 0.0d) {
            return 0L;
        }
        if (sellMultiplier >= 1.0d) {
            // Not a configuration the mod allows, but clamping beats paying more than the
            // item costs if one ever slips through.
            return buyPrice;
        }
        // Via micro-units rather than double multiplication: a price near MAX_BALANCE has
        // more significant digits than a double can hold, and silently losing the low ones
        // on the largest transactions in the game is not acceptable.
        if (buyPrice <= Long.MAX_VALUE / MICRO_SCALE) {
            return fromMicrosFloor(scale(toMicros(buyPrice), sellMultiplier));
        }
        return (long) Math.floor(buyPrice * sellMultiplier);
    }

    public static long add(long a, long b) {
        long r = a + b;
        // Overflow iff the operands share a sign that the result does not.
        if (((a ^ r) & (b ^ r)) < 0) {
            throw new ArithmeticException("balance overflow adding " + a + " and " + b);
        }
        return r;
    }

    public static long subtract(long a, long b) {
        long r = a - b;
        if (((a ^ b) & (a ^ r)) < 0) {
            throw new ArithmeticException("balance overflow subtracting " + b + " from " + a);
        }
        return r;
    }

    public static long multiply(long a, long b) {
        long r = a * b;
        long aa = Math.abs(a);
        long ab = Math.abs(b);
        if ((aa | ab) >>> 31 != 0) {
            if ((b != 0 && r / b != a) || (a == Long.MIN_VALUE && b == -1)) {
                throw new ArithmeticException("balance overflow multiplying " + a + " by " + b);
            }
        }
        return r;
    }

    /** Multiplies micro-units by a positive factor, such as a recipe or sell multiplier. */
    public static long scale(long micros, double factor) {
        if (factor < 0.0d || Double.isNaN(factor) || Double.isInfinite(factor)) {
            throw new IllegalArgumentException("factor must be finite and non-negative: " + factor);
        }
        double result = micros * factor;
        if (result > Long.MAX_VALUE) {
            throw new ArithmeticException("overflow scaling " + micros + " by " + factor);
        }
        return (long) Math.round(result);
    }

    /** Clamps a balance into the supported range without throwing. */
    public static long clamp(long balance) {
        if (balance < 0L) {
            return 0L;
        }
        return balance > MAX_BALANCE ? MAX_BALANCE : balance;
    }
}
