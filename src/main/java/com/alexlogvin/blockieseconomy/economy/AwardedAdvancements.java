package com.alexlogvin.blockieseconomy.economy;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which advancements each player has already been paid for.
 *
 * <p>Needed because "has this advancement" and "has been paid for this advancement" are
 * not the same thing. An operator running {@code /advancement revoke} followed by
 * {@code /advancement grant} re-fires the earned event, and without this the player would
 * be paid again — every time. That is a money printer an admin could hand out by accident.
 *
 * <p>Persisted alongside balances, so the guard survives a restart.
 */
public final class AwardedAdvancements {

    private final Map<UUID, Set<String>> byPlayer = new LinkedHashMap<UUID, Set<String>>();

    /**
     * Records that a player has been paid for an advancement.
     *
     * @return true if this is the first time, and a prize should be paid
     */
    public boolean claim(UUID player, String advancementId) {
        Set<String> awarded = byPlayer.get(player);
        if (awarded == null) {
            awarded = new HashSet<String>();
            byPlayer.put(player, awarded);
        }
        return awarded.add(advancementId);
    }

    public boolean hasClaimed(UUID player, String advancementId) {
        Set<String> awarded = byPlayer.get(player);
        return awarded != null && awarded.contains(advancementId);
    }

    /** Restores from disk. Applies no rules. */
    public void load(UUID player, Set<String> advancementIds) {
        byPlayer.put(player, new HashSet<String>(advancementIds));
    }

    public Map<UUID, Set<String>> snapshot() {
        return Collections.unmodifiableMap(byPlayer);
    }

    /**
     * Clears a player's record so their advancements pay out again.
     *
     * <p>Deliberately not exposed as a command. It exists for the case where an operator
     * genuinely wants to reset someone, and is called from code rather than chat so it
     * cannot be triggered by accident.
     */
    public void forget(UUID player) {
        byPlayer.remove(player);
    }

    public int trackedPlayers() {
        return byPlayer.size();
    }
}
