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
