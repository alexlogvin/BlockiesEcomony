package com.alexlogvin.blockieseconomy.core.ledger;

/**
 * Outcome of a balance change.
 *
 * <p>Failures carry a {@link Status} rather than a message, because the message has to be
 * translated by the platform layer — {@code :core} has no access to the language files.
 */
public final class TransactionResult {

    public enum Status {
        OK,
        /** The player could not afford it. */
        INSUFFICIENT_FUNDS,
        /** Too many trades in the configured window. */
        RATE_LIMITED,
        /** Amount was zero or negative where a positive value was required. */
        INVALID_AMOUNT,
        /** The change would exceed {@link com.alexlogvin.blockieseconomy.core.money.Money#MAX_BALANCE}. */
        BALANCE_LIMIT
    }

    private final Status status;
    private final long balanceBefore;
    private final long balanceAfter;
    private final long amount;
    private final TransactionType type;

    private TransactionResult(Status status, TransactionType type,
                              long amount, long balanceBefore, long balanceAfter) {
        this.status = status;
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
    }

    static TransactionResult ok(TransactionType type, long amount, long before, long after) {
        return new TransactionResult(Status.OK, type, amount, before, after);
    }

    static TransactionResult failure(Status status, TransactionType type,
                                     long amount, long balance) {
        return new TransactionResult(status, type, amount, balance, balance);
    }

    public boolean succeeded() {
        return status == Status.OK;
    }

    public Status status() {
        return status;
    }

    public TransactionType type() {
        return type;
    }

    /** The amount requested, always positive. */
    public long amount() {
        return amount;
    }

    public long balanceBefore() {
        return balanceBefore;
    }

    /** Unchanged from {@link #balanceBefore()} when the transaction failed. */
    public long balanceAfter() {
        return balanceAfter;
    }

    /** Signed change applied to the balance; zero on failure. */
    public long delta() {
        return balanceAfter - balanceBefore;
    }

    @Override
    public String toString() {
        return type + " " + amount + " -> " + status + " (" + balanceBefore
                + " to " + balanceAfter + ")";
    }
}
