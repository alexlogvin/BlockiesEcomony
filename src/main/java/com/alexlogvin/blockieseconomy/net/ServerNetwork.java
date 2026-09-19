package com.alexlogvin.blockieseconomy.net;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.EconomyServer;
import com.alexlogvin.blockieseconomy.core.net.ByteReader;
import com.alexlogvin.blockieseconomy.core.net.ByteWriter;
import com.alexlogvin.blockieseconomy.core.net.ChunkedTransfer;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshotCodec;
import com.alexlogvin.blockieseconomy.core.price.PriceEntry;
import com.alexlogvin.blockieseconomy.economy.TradeOutcome;
import com.alexlogvin.blockieseconomy.platform.Networking;
import com.alexlogvin.blockieseconomy.platform.Profiles;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server half of the protocol.
 *
 * <p>Holds the current price table in its wire form and answers clients that ask for it.
 * Nothing here trusts a number a client sent: a trade request carries an item id and a
 * count, and every price is looked up server-side before anything moves.
 */
public final class ServerNetwork {

    private static final byte ACTION_BUY = 0;
    private static final byte ACTION_SELL = 1;

    /**
     * How many past tables to keep for delta encoding.
     *
     * <p>Small on purpose. A client reconnecting after an admin tweaked a few prices is
     * the case worth optimising; one that has been away for ten rebuilds gets a full
     * table, which is correct and costs one send.
     */
    private static final int HISTORY = 4;

    private final EconomyServer economy;
    private final Networking networking;

    /** The current table in the form the wire and the hash both use. */
    private TreeMap<String, Long> current = new TreeMap<String, Long>();
    private double sellMultiplier = 0.75;
    private byte[] currentHash = new byte[0];

    /** Previous tables, newest first, for answering a client that holds one of them. */
    private final Deque<Snapshot> history = new ArrayDeque<Snapshot>();

    /** Distinguishes one price broadcast from the next when frames interleave. */
    private int transferId;

    public ServerNetwork(EconomyServer economy, Networking networking) {
        this.economy = economy;
        this.networking = networking;
    }

    public void register() {
        networking.registerServerbound(Channels.HELLO, this::onHello);
        networking.registerServerbound(Channels.TRADE, this::onTrade);
    }

    // ---- price table ------------------------------------------------------------------

    /**
     * Rebuilds the wire form after the solver finishes, and pushes it to everyone online.
     *
     * <p>Called on the server thread once {@code rebuildAsync} completes.
     */
    public void onPricesRebuilt(List<ServerPlayer> online) {
        if (!economy.prices().isReady()) {
            return;
        }

        TreeMap<String, Long> rebuilt = new TreeMap<String, Long>();
        for (Map.Entry<String, PriceEntry> e : economy.prices().table().entries().entrySet()) {
            rebuilt.put(e.getKey(), Long.valueOf(e.getValue().buyPrice()));
        }
        double multiplier = economy.config().server().sellMultiplier();
        byte[] hash = ShopSnapshotCodec.hash(rebuilt, multiplier);

        if (ShopSnapshotCodec.sameHash(hash, currentHash)) {
            // A /reload that changed nothing a client can see. Saying so costs nothing and
            // saves every player a resend.
            BlockiesEconomy.LOGGER.debug("Prices rebuilt with no visible change ({}).",
                    ShopSnapshotCodec.hashToString(hash));
            return;
        }

        if (currentHash.length > 0) {
            history.addFirst(new Snapshot(current, sellMultiplier, currentHash));
            while (history.size() > HISTORY) {
                history.removeLast();
            }
        }

        current = rebuilt;
        sellMultiplier = multiplier;
        currentHash = hash;
        transferId++;

        BlockiesEconomy.LOGGER.info("Price table {} ready: {} items.",
                ShopSnapshotCodec.hashToString(hash), rebuilt.size());

        for (int i = 0; i < online.size(); i++) {
            sendPrices(online.get(i), null);
        }
    }

    /**
     * Sends the price table, as cheaply as the client's cached hash allows.
     *
     * @param cachedHash what the client says it holds, or null for "send it all"
     */
    private void sendPrices(ServerPlayer player, byte[] cachedHash) {
        if (currentHash.length == 0 || !networking.canReceive(player, Channels.PRICES)) {
            return;
        }

        byte[] message;
        String how;
        if (ShopSnapshotCodec.sameHash(cachedHash, currentHash)) {
            message = ShopSnapshotCodec.encodeUnchanged();
            how = "unchanged";
        } else {
            Snapshot base = findInHistory(cachedHash);
            if (base != null && base.sellMultiplier == sellMultiplier) {
                message = ShopSnapshotCodec.encodeDelta(base.prices, current, sellMultiplier,
                        base.hash, currentHash);
                how = "delta";
            } else {
                message = ShopSnapshotCodec.encodeFull(current, sellMultiplier, currentHash);
                how = "full";
            }
        }

        List<byte[]> frames = ChunkedTransfer.split(transferId, message);
        for (int i = 0; i < frames.size(); i++) {
            networking.sendToPlayer(player, Channels.PRICES, frames.get(i));
        }

        BlockiesEconomy.LOGGER.debug("Sent {} price table to {} ({} bytes in {} frame(s)).",
                how, Profiles.nameOf(player.getGameProfile()), message.length, frames.size());
    }

    private Snapshot findInHistory(byte[] hash) {
        if (hash == null || hash.length == 0) {
            return null;
        }
        for (Snapshot snapshot : history) {
            if (ShopSnapshotCodec.sameHash(snapshot.hash, hash)) {
                return snapshot;
            }
        }
        return null;
    }

    // ---- incoming ---------------------------------------------------------------------

    private void onHello(ServerPlayer player, byte[] payload) {
        ByteReader in = new ByteReader(payload);
        int protocol = in.readVarInt();
        byte[] cachedHash = in.readBytes();

        if (protocol != Channels.PROTOCOL_VERSION) {
            // Sending a table this client would misparse is worse than sending none: it
            // keeps every command working and simply leaves the GUI without prices.
            BlockiesEconomy.LOGGER.warn(
                    "{} is on protocol {}, this server speaks {}; the shop screen will be "
                            + "empty for them until they update.",
                    Profiles.nameOf(player.getGameProfile()), protocol, Channels.PROTOCOL_VERSION);
            return;
        }

        sendPrices(player, cachedHash);
        sendBalance(player);
    }

    private void onTrade(ServerPlayer player, byte[] payload) {
        ByteReader in = new ByteReader(payload);
        byte action = (byte) in.readByte();
        String itemId = in.readString();
        int count = in.readVarInt();

        // Everything the client asked for is re-derived here. The prices it displayed are
        // a copy for the player's benefit and carry no authority.
        TradeOutcome outcome = action == ACTION_BUY
                ? economy.economy().buy(player, itemId, count)
                : economy.economy().sell(player, itemId, count);

        sendResult(player, action, outcome);
        sendBalance(player);
    }

    // ---- outgoing ---------------------------------------------------------------------

    /**
     * Asks a client to open the shop screen.
     *
     * @return false when the client cannot, so the caller can say so instead of leaving
     *         the player waiting for a window that will never appear
     */
    public boolean requestOpenShop(ServerPlayer player) {
        if (!networking.canReceive(player, Channels.OPEN)) {
            return false;
        }
        networking.sendToPlayer(player, Channels.OPEN, new byte[0]);
        return true;
    }

    public void sendBalance(ServerPlayer player) {
        if (!networking.canReceive(player, Channels.BALANCE)) {
            return;
        }
        networking.sendToPlayer(player, Channels.BALANCE,
                new ByteWriter(16).writeLong(economy.economy().balance(player)).toByteArray());
    }

    private void sendResult(ServerPlayer player, byte action, TradeOutcome outcome) {
        if (!networking.canReceive(player, Channels.RESULT)) {
            return;
        }
        ByteWriter out = new ByteWriter(64);
        out.writeByte(action);
        out.writeByte(outcome.reason().ordinal());
        out.writeString(outcome.itemId() == null ? "" : outcome.itemId());
        out.writeVarInt(Math.max(0, outcome.count()));
        out.writeLong(outcome.total());
        out.writeVarInt(Math.max(0, outcome.droppedOnGround()));
        networking.sendToPlayer(player, Channels.RESULT, out.toByteArray());
    }

    /** One past version of the table, kept only so a delta can be built against it. */
    private static final class Snapshot {
        private final TreeMap<String, Long> prices;
        private final double sellMultiplier;
        private final byte[] hash;

        Snapshot(TreeMap<String, Long> prices, double sellMultiplier, byte[] hash) {
            this.prices = prices;
            this.sellMultiplier = sellMultiplier;
            this.hash = hash;
        }
    }
}
