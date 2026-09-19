package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshot;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshotCodec;
import com.alexlogvin.blockieseconomy.platform.Services;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * What the client knows: its balance, the price table, and where it cached them.
 *
 * <p>Everything here is display state. The server recomputes every figure before acting on
 * it, so the worst a stale or tampered cache can do is show the player a wrong number and
 * have the trade come back with a different one.
 *
 * <p>The cache is per server, keyed by address, so switching between two servers with
 * different economies does not make either of them appear to have the other's prices.
 */
public final class ClientShopState {

    private static final ClientShopState INSTANCE = new ClientShopState();

    private ShopSnapshot snapshot;
    private long balance;
    private boolean balanceKnown;
    private String cacheKey;

    private ClientShopState() {
    }

    public static ClientShopState get() {
        return INSTANCE;
    }

    // ---- state ------------------------------------------------------------------------

    /** The price table, or null before the server has sent one. */
    public ShopSnapshot snapshot() {
        return snapshot;
    }

    public boolean hasPrices() {
        return snapshot != null && snapshot.size() > 0;
    }

    public long balance() {
        return balance;
    }

    /**
     * False until the server sends a balance.
     *
     * <p>The HUD stays hidden until then rather than showing a confident 0, which is a
     * real balance and would be alarming to a player who has thousands.
     */
    public boolean balanceKnown() {
        return balanceKnown;
    }

    public void setBalance(long value) {
        this.balance = value;
        this.balanceKnown = true;
    }

    public void setSnapshot(ShopSnapshot value) {
        this.snapshot = value;
    }

    /** Called when leaving a world, so the next server starts from its own cache. */
    public void onDisconnect() {
        snapshot = null;
        balance = 0L;
        balanceKnown = false;
        cacheKey = null;
    }

    // ---- disk cache ---------------------------------------------------------------------

    /**
     * Points the cache at a particular server and loads whatever was stored for it.
     *
     * @param serverKey the server address, or the world folder in single player
     */
    public void useCacheFor(String serverKey) {
        this.cacheKey = sanitise(serverKey);
        this.snapshot = readCache();
        if (snapshot != null) {
            BlockiesEconomy.LOGGER.debug("Loaded {} cached prices for {} ({}).",
                    Integer.valueOf(snapshot.size()), cacheKey,
                    ShopSnapshotCodec.hashToString(snapshot.hash()));
        }
    }

    /** The hash to offer the server, or an empty array when nothing is cached. */
    public byte[] cachedHash() {
        return snapshot == null ? new byte[0] : snapshot.hash();
    }

    /** Stores the raw wire message so the next join can be answered with "unchanged". */
    public void writeCache(byte[] fullMessage) {
        Path path = cachePath();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, fullMessage);
        } catch (IOException e) {
            // A cache is an optimisation. Failing to write one is not worth interrupting
            // a player's session over, so it is logged and forgotten.
            BlockiesEconomy.LOGGER.warn("Could not cache prices: {}", e.toString());
        }
    }

    private ShopSnapshot readCache() {
        Path path = cachePath();
        if (path == null || !Files.isReadable(path)) {
            return null;
        }
        try {
            return ShopSnapshotCodec.decode(Files.readAllBytes(path), null).snapshot();
        } catch (IOException | RuntimeException e) {
            // Written by an older format, or truncated by a crash mid-write. Either way it
            // is not worth diagnosing: delete it and pull a fresh table.
            BlockiesEconomy.LOGGER.info("Discarding an unreadable price cache: {}",
                    e.toString());
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Nothing useful to do; the next write overwrites it anyway.
            }
            return null;
        }
    }

    private Path cachePath() {
        if (cacheKey == null) {
            return null;
        }
        return Services.PLATFORM.configDir()
                .resolve(BlockiesEconomy.MOD_ID)
                .resolve("cache")
                .resolve("prices-v" + ShopSnapshotCodec.FORMAT_VERSION + "-" + cacheKey + ".bin");
    }

    /**
     * Turns a server address into something safe to use as a filename.
     *
     * <p>An address can contain a colon and a path separator, and on Windows a colon in a
     * filename is not merely ugly but invalid. A short hash is appended so two addresses
     * that sanitise to the same text do not share one cache file.
     */
    private static String sanitise(String key) {
        String safe = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        int hash = 0;
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            hash = hash * 31 + bytes[i];
        }
        return safe + "-" + Integer.toHexString(hash);
    }
}
