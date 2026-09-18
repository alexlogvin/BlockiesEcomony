package com.alexlogvin.blockieseconomy.core.ledger;

import com.alexlogvin.blockieseconomy.core.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player balances and the rules for changing them.
 *
 * <p>This is the authority. Every transaction the mod performs goes through here on the
 * server; the client only ever displays a balance it was told about. Nothing in this
 * class trusts a caller-supplied balance.
 *
 * <p>Balances are held in a concurrent map because the price solver runs off-thread and
 * may be queried while trades continue, but every <em>mutation</em> happens on the server
 * thread.
 */
public final class Ledger {

    private final Map<UUID, Long> balances = new ConcurrentHashMap<UUID, Long>();
    private final long startingBalance;
    private final RateLimiter rateLimiter;
    private TransactionListener listener = TransactionListener.NONE;

    /** Notified after every applied change, for the transaction log. */
    public interface TransactionListener {
        TransactionListener NONE = (player, result) -> { };

        void onTransaction(UUID player, TransactionResult result);
    }

    public Ledger(long startingBalance, RateLimiter rateLimiter) {
        if (startingBalance < 0L) {
            throw new IllegalArgumentException("starting balance cannot be negative");
        }
        this.startingBalance = startingBalance;
        this.rateLimiter = rateLimiter;
    }

    public static Ledger withDefaults() {
        return new Ledger(0L, RateLimiter.withDefaults());
    }

    public void setListener(TransactionListener listener) {
        this.listener = listener == null ? TransactionListener.NONE : listener;
    }

    public long startingBalance() {
        return startingBalance;
    }

    /** A player who has never traded reads as the configured starting balance. */
    public long balance(UUID player) {
        Long value = balances.get(player);
        return value == null ? startingBalance : value.longValue();
    }

    public boolean isKnown(UUID player) {
        return balances.containsKey(player);
    }

    /** Restores a balance during world load. Applies no rules and fires no listener. */
    public void load(UUID player, long balance) {
        balances.put(player, Long.valueOf(Money.clamp(balance)));
    }

    public Map<UUID, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<UUID, Long>(balances));
    }

    public void forget(UUID player) {
        balances.remove(player);
        rateLimiter.forget(player);
    }

    /**
     * Drops every balance.
     *
     * <p>Called when a world unloads. Without it a single-player client carries one
     * world's balances into the next: the store is per-world on disk, but this map is not,
     * and loading only adds to it.
     */
    public void clear() {
        balances.clear();
        rateLimiter.clear();
    }

    // ---- rate-limited player transactions --------------------------------------

    /** Charges a player. Rate limited, since it is reachable from a client packet. */
    public TransactionResult charge(UUID player, long amount, long now) {
        return apply(player, TransactionType.BUY, amount, true, true, now);
    }

    /** Pays a player for a sale. Rate limited for the same reason. */
    public TransactionResult pay(UUID player, long amount, long now) {
        return apply(player, TransactionType.SELL, amount, false, true, now);
    }

    // ---- server-initiated changes, not rate limited -----------------------------

    /** Advancement prize. Not rate limited: the server decides when this happens. */
    public TransactionResult award(UUID player, long amount) {
        return apply(player, TransactionType.ADVANCEMENT, amount, false, false, 0L);
    }

    /** Death penalty. Takes what it can rather than failing when the player is short. */
    public TransactionResult applyDeathPenalty(UUID player, long amount) {
        if (amount <= 0L) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_AMOUNT,
                    TransactionType.DEATH_PENALTY, amount, balance(player));
        }
        long before = balance(player);
        long taken = Math.min(before, amount);
        long after = before - taken;
        balances.put(player, Long.valueOf(after));

        TransactionResult result =
                TransactionResult.ok(TransactionType.DEATH_PENALTY, taken, before, after);
        listener.onTransaction(player, result);
        return result;
    }

    // ---- operator commands ------------------------------------------------------

    public TransactionResult adminSet(UUID player, long newBalance) {
        if (newBalance < 0L) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_AMOUNT,
                    TransactionType.ADMIN_SET, newBalance, balance(player));
        }
        if (newBalance > Money.MAX_BALANCE) {
            return TransactionResult.failure(TransactionResult.Status.BALANCE_LIMIT,
                    TransactionType.ADMIN_SET, newBalance, balance(player));
        }
        long before = balance(player);
        balances.put(player, Long.valueOf(newBalance));

        TransactionResult result =
                TransactionResult.ok(TransactionType.ADMIN_SET, newBalance, before, newBalance);
        listener.onTransaction(player, result);
        return result;
    }

    public TransactionResult adminAdd(UUID player, long amount) {
        return apply(player, TransactionType.ADMIN_ADD, amount, false, false, 0L);
    }

    public TransactionResult adminRemove(UUID player, long amount) {
        return apply(player, TransactionType.ADMIN_REMOVE, amount, true, false, 0L);
    }

    // ---- queries ----------------------------------------------------------------

    /** Highest balances first, for {@code /shop top}. */
    public List<Map.Entry<UUID, Long>> leaderboard(int limit) {
        List<Map.Entry<UUID, Long>> all =
                new ArrayList<Map.Entry<UUID, Long>>(balances.entrySet());
        Collections.sort(all, new Comparator<Map.Entry<UUID, Long>>() {
            @Override
            public int compare(Map.Entry<UUID, Long> a, Map.Entry<UUID, Long> b) {
                return Long.compare(b.getValue().longValue(), a.getValue().longValue());
            }
        });
        return all.size() <= limit ? all : new ArrayList<Map.Entry<UUID, Long>>(all.subList(0, limit));
    }

    public boolean canAfford(UUID player, long amount) {
        return amount >= 0L && balance(player) >= amount;
    }

    // ---- the single mutation path -----------------------------------------------

    private TransactionResult apply(UUID player, TransactionType type, long amount,
                                    boolean debit, boolean rateLimited, long now) {
        long before = balance(player);

        if (amount <= 0L) {
            return TransactionResult.failure(
                    TransactionResult.Status.INVALID_AMOUNT, type, amount, before);
        }

        if (rateLimited && !rateLimiter.tryAcquire(player, now)) {
            return TransactionResult.failure(
                    TransactionResult.Status.RATE_LIMITED, type, amount, before);
        }

        long after;
        if (debit) {
            if (before < amount) {
                return TransactionResult.failure(
                        TransactionResult.Status.INSUFFICIENT_FUNDS, type, amount, before);
            }
            after = before - amount;
        } else {
            if (before > Money.MAX_BALANCE - amount) {
                return TransactionResult.failure(
                        TransactionResult.Status.BALANCE_LIMIT, type, amount, before);
            }
            after = Money.add(before, amount);
        }

        balances.put(player, Long.valueOf(after));
        TransactionResult result = TransactionResult.ok(type, amount, before, after);
        listener.onTransaction(player, result);
        return result;
    }
}
