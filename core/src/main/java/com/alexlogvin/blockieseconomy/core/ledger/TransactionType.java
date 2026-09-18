package com.alexlogvin.blockieseconomy.core.ledger;

/** Why a balance changed. Recorded in the transaction log when it is enabled. */
public enum TransactionType {
    BUY,
    SELL,
    ADVANCEMENT,
    DEATH_PENALTY,
    ADMIN_SET,
    ADMIN_ADD,
    ADMIN_REMOVE;

    /** True for changes an operator made, which are always logged regardless of config. */
    public boolean isAdmin() {
        return this == ADMIN_SET || this == ADMIN_ADD || this == ADMIN_REMOVE;
    }
}
