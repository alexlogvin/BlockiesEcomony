package com.alexlogvin.blockieseconomy.core.ledger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Sliding-window trade limits, per player and server-wide.
 *
 * <p>Aimed at autoclickers and packet spam rather than at players: the defaults are
 * generous enough that nobody trading by hand will ever notice. The server-wide cap
 * exists so one scripted client cannot saturate the transaction path for everyone.
 *
 * <p>Not thread-safe. All calls happen on the server thread, where transactions are
 * applied; the netty receive path queues onto it rather than touching this directly.
 */
public final class RateLimiter {

    /** Per player, per minute. Roughly two trades a second sustained. */
    public static final int DEFAULT_PER_PLAYER = 120;

    /** Across the whole server, per minute. */
    public static final int DEFAULT_GLOBAL = 2_000;

    private static final long WINDOW_MILLIS = 60_000L;

    private int perPlayerLimit;
    private int globalLimit;
    private final Map<UUID, Window> perPlayer = new HashMap<UUID, Window>();
    private final Window global = new Window();

    public RateLimiter(int perPlayerLimit, int globalLimit) {
        this.perPlayerLimit = perPlayerLimit;
        this.globalLimit = globalLimit;
    }

    /**
     * Changes the limits in place.
     *
     * <p>So that a config reload applies without a new limiter, which would discard the
     * windows it is currently counting and briefly let everyone trade without limit.
     */
    public void setLimits(int perPlayerLimit, int globalLimit) {
        this.perPlayerLimit = perPlayerLimit;
        this.globalLimit = globalLimit;
    }

    public static RateLimiter withDefaults() {
        return new RateLimiter(DEFAULT_PER_PLAYER, DEFAULT_GLOBAL);
    }

    /** A limit of zero or less disables that check. */
    public boolean isDisabled() {
        return perPlayerLimit <= 0 && globalLimit <= 0;
    }

    /**
     * Records one trade attempt and reports whether it is allowed.
     *
     * @param now current time in milliseconds; passed in so tests need no real clock
     */
    public boolean tryAcquire(UUID player, long now) {
        if (globalLimit > 0) {
            global.prune(now);
            if (global.count >= globalLimit) {
                return false;
            }
        }

        Window window = null;
        if (perPlayerLimit > 0) {
            window = perPlayer.get(player);
            if (window == null) {
                window = new Window();
                perPlayer.put(player, window);
            }
            window.prune(now);
            if (window.count >= perPlayerLimit) {
                return false;
            }
        }

        if (window != null) {
            window.record(now);
        }
        if (globalLimit > 0) {
            global.record(now);
        }
        return true;
    }

    /** Drops tracking for players who have not traded within the window. */
    public void evictIdle(long now) {
        Iterator<Map.Entry<UUID, Window>> it = perPlayer.entrySet().iterator();
        while (it.hasNext()) {
            Window w = it.next().getValue();
            w.prune(now);
            if (w.count == 0) {
                it.remove();
            }
        }
    }

    public void forget(UUID player) {
        perPlayer.remove(player);
    }

    /** Drops all tracking. Called when a world unloads. */
    public void clear() {
        perPlayer.clear();
    }

    /**
     * A fixed-capacity ring of timestamps. Counting entries in the last minute needs no
     * allocation per trade, which matters when this runs on the server thread.
     */
    private static final class Window {
        private long[] times = new long[16];
        private int head;
        private int count;

        void record(long now) {
            if (count == times.length) {
                grow();
            }
            times[(head + count) % times.length] = now;
            count++;
        }

        void prune(long now) {
            long cutoff = now - WINDOW_MILLIS;
            while (count > 0 && times[head] <= cutoff) {
                head = (head + 1) % times.length;
                count--;
            }
        }

        private void grow() {
            long[] bigger = new long[times.length * 2];
            for (int i = 0; i < count; i++) {
                bigger[i] = times[(head + i) % times.length];
            }
            times = bigger;
            head = 0;
        }
    }
}
