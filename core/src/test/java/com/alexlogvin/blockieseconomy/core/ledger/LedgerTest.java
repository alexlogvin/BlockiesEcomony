package com.alexlogvin.blockieseconomy.core.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexlogvin.blockieseconomy.core.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    private static Ledger unlimited(long starting) {
        return new Ledger(starting, new RateLimiter(0, 0));
    }

    @Test
    @DisplayName("an unknown player reads as the configured starting balance")
    void startingBalance() {
        assertEquals(0L, unlimited(0L).balance(ALICE));
        assertEquals(500L, unlimited(500L).balance(ALICE));
    }

    @Test
    @DisplayName("charging fails rather than going negative")
    void cannotOverdraw() {
        Ledger ledger = unlimited(100L);

        TransactionResult ok = ledger.charge(ALICE, 60L, 0L);
        assertTrue(ok.succeeded());
        assertEquals(40L, ledger.balance(ALICE));

        TransactionResult failed = ledger.charge(ALICE, 100L, 0L);
        assertFalse(failed.succeeded());
        assertEquals(TransactionResult.Status.INSUFFICIENT_FUNDS, failed.status());
        assertEquals(40L, ledger.balance(ALICE), "a failed charge must not change the balance");
        assertEquals(0L, failed.delta());
    }

    @Test
    @DisplayName("zero and negative amounts are rejected outright")
    void rejectsNonPositiveAmounts() {
        Ledger ledger = unlimited(100L);
        assertEquals(TransactionResult.Status.INVALID_AMOUNT,
                ledger.charge(ALICE, 0L, 0L).status());
        assertEquals(TransactionResult.Status.INVALID_AMOUNT,
                ledger.charge(ALICE, -50L, 0L).status());
        assertEquals(TransactionResult.Status.INVALID_AMOUNT,
                ledger.pay(ALICE, -50L, 0L).status());
        assertEquals(100L, ledger.balance(ALICE));
    }

    @Test
    @DisplayName("a payout cannot push a balance past the supported maximum")
    void respectsBalanceCeiling() {
        Ledger ledger = unlimited(0L);
        ledger.load(ALICE, Money.MAX_BALANCE);

        TransactionResult result = ledger.pay(ALICE, 1L, 0L);
        assertEquals(TransactionResult.Status.BALANCE_LIMIT, result.status());
        assertEquals(Money.MAX_BALANCE, ledger.balance(ALICE));
    }

    @Test
    @DisplayName("the death penalty takes what it can instead of failing")
    void deathPenaltyIsPartial() {
        Ledger ledger = unlimited(0L);
        ledger.load(ALICE, 30L);

        TransactionResult result = ledger.applyDeathPenalty(ALICE, 100L);
        assertTrue(result.succeeded());
        assertEquals(30L, result.amount(), "only what the player had is taken");
        assertEquals(0L, ledger.balance(ALICE));
    }

    @Test
    @DisplayName("the default death penalty of zero is a no-op")
    void zeroDeathPenaltyDoesNothing() {
        Ledger ledger = unlimited(0L);
        ledger.load(ALICE, 30L);
        assertFalse(ledger.applyDeathPenalty(ALICE, 0L).succeeded());
        assertEquals(30L, ledger.balance(ALICE));
    }

    @Test
    @DisplayName("operator commands set, add and remove")
    void adminCommands() {
        Ledger ledger = unlimited(0L);

        assertTrue(ledger.adminSet(ALICE, 1_000L).succeeded());
        assertEquals(1_000L, ledger.balance(ALICE));

        assertTrue(ledger.adminAdd(ALICE, 250L).succeeded());
        assertEquals(1_250L, ledger.balance(ALICE));

        assertTrue(ledger.adminRemove(ALICE, 250L).succeeded());
        assertEquals(1_000L, ledger.balance(ALICE));

        // Removing more than the player has fails rather than going negative.
        assertEquals(TransactionResult.Status.INSUFFICIENT_FUNDS,
                ledger.adminRemove(ALICE, 5_000L).status());
        assertEquals(1_000L, ledger.balance(ALICE));

        assertEquals(TransactionResult.Status.INVALID_AMOUNT,
                ledger.adminSet(ALICE, -1L).status());
    }

    @Test
    @DisplayName("player trades are rate limited but server-side awards are not")
    void rateLimiting() {
        Ledger ledger = new Ledger(10_000L, new RateLimiter(3, 0));

        for (int i = 0; i < 3; i++) {
            assertTrue(ledger.charge(ALICE, 1L, 1_000L).succeeded(), "trade " + i);
        }
        assertEquals(TransactionResult.Status.RATE_LIMITED,
                ledger.charge(ALICE, 1L, 1_000L).status());

        // Another player is unaffected.
        assertTrue(ledger.charge(BOB, 1L, 1_000L).succeeded());

        // An advancement payout bypasses the limit: the server, not a packet, triggers it.
        assertTrue(ledger.award(ALICE, 100L).succeeded());

        // The window slides.
        assertTrue(ledger.charge(ALICE, 1L, 70_000L).succeeded());
    }

    @Test
    @DisplayName("the global cap protects the server from one scripted client")
    void globalRateLimit() {
        Ledger ledger = new Ledger(10_000L, new RateLimiter(0, 2));
        assertTrue(ledger.charge(ALICE, 1L, 0L).succeeded());
        assertTrue(ledger.charge(BOB, 1L, 0L).succeeded());
        assertEquals(TransactionResult.Status.RATE_LIMITED,
                ledger.charge(ALICE, 1L, 0L).status());
    }

    @Test
    @DisplayName("every applied change reaches the listener for the transaction log")
    void notifiesListener() {
        Ledger ledger = unlimited(1_000L);
        final List<TransactionResult> seen = new ArrayList<TransactionResult>();
        ledger.setListener((player, result) -> seen.add(result));

        ledger.charge(ALICE, 100L, 0L);
        ledger.pay(ALICE, 50L, 0L);
        ledger.award(ALICE, 200L);
        ledger.charge(ALICE, 999_999L, 0L); // fails, must not be logged as applied

        assertEquals(3, seen.size());
        assertEquals(TransactionType.BUY, seen.get(0).type());
        assertEquals(TransactionType.SELL, seen.get(1).type());
        assertEquals(TransactionType.ADVANCEMENT, seen.get(2).type());
        assertEquals(-100L, seen.get(0).delta());
        assertEquals(50L, seen.get(1).delta());
    }

    @Test
    @DisplayName("the leaderboard is ordered richest first and respects its limit")
    void leaderboard() {
        Ledger ledger = unlimited(0L);
        UUID carol = UUID.nameUUIDFromBytes("carol".getBytes());
        ledger.load(ALICE, 500L);
        ledger.load(BOB, 5_000L);
        ledger.load(carol, 50L);

        List<Map.Entry<UUID, Long>> top = ledger.leaderboard(2);
        assertEquals(2, top.size());
        assertEquals(BOB, top.get(0).getKey());
        assertEquals(ALICE, top.get(1).getKey());

        assertEquals(3, ledger.leaderboard(10).size());
    }

    @Test
    @DisplayName("loading restores balances without applying rules or firing the listener")
    void loadIsSilent() {
        Ledger ledger = unlimited(0L);
        final List<TransactionResult> seen = new ArrayList<TransactionResult>();
        ledger.setListener((player, result) -> seen.add(result));

        ledger.load(ALICE, 12_345L);

        assertEquals(12_345L, ledger.balance(ALICE));
        assertTrue(ledger.isKnown(ALICE));
        assertTrue(seen.isEmpty(), "world load is not a transaction");

        // A negative value on disk is clamped rather than trusted.
        ledger.load(BOB, -50L);
        assertEquals(0L, ledger.balance(BOB));
    }

    @Test
    @DisplayName("canAfford agrees with what charge will actually do")
    void canAffordMatchesCharge() {
        Ledger ledger = unlimited(100L);
        assertTrue(ledger.canAfford(ALICE, 100L));
        assertFalse(ledger.canAfford(ALICE, 101L));
        assertFalse(ledger.canAfford(ALICE, -1L));

        assertTrue(ledger.charge(ALICE, 100L, 0L).succeeded());
        assertFalse(ledger.canAfford(ALICE, 1L));
    }
}
