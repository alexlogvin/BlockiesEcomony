package com.alexlogvin.blockieseconomy.core.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A config reload has to reach the objects that copied the config.
 *
 * <p>These exist because it did not. The ledger read {@code starting_balance} and the rate
 * limiter read its limits once, when they were built at mod init, and nothing updated them
 * afterwards — so editing either did nothing until the game was restarted, whether the edit
 * came from {@code /shop reload} or from the config screen.
 */
class ReloadableSettingsTest {

    @Test
    @DisplayName("a new starting balance applies to players with no record, and only them")
    void startingBalanceIsLive() {
        Ledger ledger = new Ledger(0L, RateLimiter.withDefaults());
        UUID veteran = UUID.randomUUID();
        UUID newcomer = UUID.randomUUID();

        ledger.load(veteran, 500L);
        assertEquals(0L, ledger.balance(newcomer));

        ledger.setStartingBalance(12_345L);

        assertEquals(12_345L, ledger.balance(newcomer), "an unknown player gets the new figure");
        // The one thing this must never do. A player who has earned 500 does not get reset
        // to the starting balance because an operator changed a config line.
        assertEquals(500L, ledger.balance(veteran), "a known balance is untouched");
    }

    @Test
    @DisplayName("changing the starting balance keeps every balance already recorded")
    void reloadDoesNotWipeBalances() {
        Ledger ledger = new Ledger(0L, RateLimiter.withDefaults());
        UUID player = UUID.randomUUID();
        ledger.load(player, 900L);

        ledger.setStartingBalance(50L);

        assertTrue(ledger.isKnown(player), "the reload must not drop the ledger's contents");
        assertEquals(900L, ledger.balance(player));
    }

    @Test
    @DisplayName("new rate limits apply in place, without resetting the windows")
    void rateLimitsAreLive() {
        RateLimiter limiter = new RateLimiter(2, 100);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(player, 0L));
        assertTrue(limiter.tryAcquire(player, 0L));
        assertFalse(limiter.tryAcquire(player, 0L), "the third trade is over the limit of 2");

        limiter.setLimits(4, 100);

        // Raising the limit lets the counted window continue rather than starting it over:
        // two trades are already on record, so two more fit under the new limit of four.
        assertTrue(limiter.tryAcquire(player, 0L));
        assertTrue(limiter.tryAcquire(player, 0L));
        assertFalse(limiter.tryAcquire(player, 0L));
    }

    @Test
    @DisplayName("a limit of zero still disables the check after a reload")
    void zeroDisablesAfterReload() {
        RateLimiter limiter = new RateLimiter(1, 1);
        limiter.setLimits(0, 0);
        assertTrue(limiter.isDisabled());

        UUID player = UUID.randomUUID();
        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.tryAcquire(player, 0L), "no limit should apply at all");
        }
    }
}
