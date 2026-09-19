package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.core.net.ByteReader;
import com.alexlogvin.blockieseconomy.core.net.ByteWriter;
import com.alexlogvin.blockieseconomy.core.net.ChunkedTransfer;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshotCodec;
import com.alexlogvin.blockieseconomy.economy.TradeOutcome;
import com.alexlogvin.blockieseconomy.net.Channels;
import com.alexlogvin.blockieseconomy.platform.Networking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The client half of the protocol.
 *
 * <p>Loaded only from a client entry point. A dedicated server has no
 * {@code net.minecraft.client} classes, so touching this from shared code would be fatal
 * there — the split is what keeps the server jar honest.
 */
public final class ClientNetwork {

    private static final byte ACTION_BUY = 0;
    private static final byte ACTION_SELL = 1;

    private static Networking networking;
    private static final ChunkedTransfer.Reassembler REASSEMBLER =
            new ChunkedTransfer.Reassembler();

    /** Set by the shop screen while it is open, so results land there rather than in chat. */
    private static TradeResultListener listener;

    /** Notified of a trade outcome that came back over the wire. */
    public interface TradeResultListener {
        void onResult(boolean wasBuy, TradeOutcome.Reason reason, String itemId, int count,
                      long total, int dropped);
    }

    private ClientNetwork() {
    }

    public static void register(Networking net) {
        networking = net;
        net.registerClientbound(Channels.PRICES, ClientNetwork::onPrices);
        net.registerClientbound(Channels.BALANCE, ClientNetwork::onBalance);
        net.registerClientbound(Channels.RESULT, ClientNetwork::onResult);
        net.registerClientbound(Channels.OPEN, payload -> ClientHooks.openShop());
    }

    public static void setResultListener(TradeResultListener value) {
        listener = value;
    }

    // ---- joining ------------------------------------------------------------------------

    /**
     * Announces the client and offers the hash of whatever table it has cached.
     *
     * <p>Called once the player is in a world. Sending the hash rather than waiting to be
     * given a table is what lets an unchanged economy cost one small packet instead of a
     * full resend on every single join.
     *
     * @param serverKey the address joined, or the world folder in single player
     */
    public static void onJoin(String serverKey) {
        if (networking == null) {
            return;
        }
        ClientShopState state = ClientShopState.get();
        state.useCacheFor(serverKey);

        ByteWriter out = new ByteWriter(48);
        out.writeVarInt(Channels.PROTOCOL_VERSION);
        out.writeBytes(state.cachedHash());
        networking.sendToServer(Channels.HELLO, out.toByteArray());
    }

    public static void onDisconnect() {
        REASSEMBLER.clear();
        ClientShopState.get().onDisconnect();
        listener = null;
    }

    // ---- receiving ----------------------------------------------------------------------

    private static void onPrices(byte[] frame) {
        byte[] message;
        try {
            message = REASSEMBLER.accept(frame);
        } catch (RuntimeException e) {
            BlockiesEconomy.LOGGER.warn("Discarding a malformed price frame: {}", e.toString());
            REASSEMBLER.clear();
            return;
        }
        if (message == null) {
            return;
        }

        ShopSnapshotCodec.Decoded decoded;
        try {
            decoded = ShopSnapshotCodec.decode(message, ClientShopState.get().snapshot());
        } catch (RuntimeException e) {
            BlockiesEconomy.LOGGER.warn("Could not read the price table: {}", e.toString());
            return;
        }

        if (decoded.kind() == ShopSnapshotCodec.KIND_UNCHANGED) {
            BlockiesEconomy.LOGGER.debug("Server confirms the cached price table is current.");
            return;
        }

        if (decoded.snapshot() == null) {
            // A delta against a table we do not have. Nothing to apply and nothing to fix
            // locally, so drop the cache and ask again from scratch.
            BlockiesEconomy.LOGGER.info(
                    "Server sent a price update for a table we do not hold; asking for a full one.");
            ClientShopState.get().setSnapshot(null);
            if (networking != null) {
                ByteWriter out = new ByteWriter(16);
                out.writeVarInt(Channels.PROTOCOL_VERSION);
                out.writeBytes(new byte[0]);
                networking.sendToServer(Channels.HELLO, out.toByteArray());
            }
            return;
        }

        ClientShopState state = ClientShopState.get();
        state.setSnapshot(decoded.snapshot());

        // Cached in full form regardless of how it arrived, so a delta applied today is
        // what the next session offers the server as its base.
        state.writeCache(ShopSnapshotCodec.encodeFull(decoded.snapshot().buyPrices(),
                decoded.snapshot().sellMultiplier(), decoded.snapshot().hash()));

        BlockiesEconomy.LOGGER.info("Shop has {} priced items ({}).",
                Integer.valueOf(decoded.snapshot().size()),
                ShopSnapshotCodec.hashToString(decoded.snapshot().hash()));
    }

    private static void onBalance(byte[] payload) {
        ClientShopState.get().setBalance(new ByteReader(payload).readLong());
    }

    private static void onResult(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        boolean wasBuy = in.readByte() == ACTION_BUY;
        int reasonOrdinal = in.readByte();
        String itemId = in.readString();
        int count = in.readVarInt();
        long total = in.readLong();
        int dropped = in.readVarInt();

        TradeOutcome.Reason[] reasons = TradeOutcome.Reason.values();
        TradeOutcome.Reason reason = reasonOrdinal >= 0 && reasonOrdinal < reasons.length
                ? reasons[reasonOrdinal]
                : TradeOutcome.Reason.INVALID_AMOUNT;

        if (listener != null) {
            listener.onResult(wasBuy, reason, itemId, count, total, dropped);
            return;
        }

        // No shop screen open — a recipe-viewer button, say. The action bar is the right
        // place for it: visible, and gone again without cluttering chat.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(describe(reason, wasBuy, total), true);
        }
    }

    private static Component describe(TradeOutcome.Reason reason, boolean wasBuy, long total) {
        if (reason != TradeOutcome.Reason.OK) {
            return Component.translatable(errorKey(reason));
        }
        return Component.literal((wasBuy ? "-" : "+") + ClientMoneyText.shortForm(total)
                + " " + ClientMoneyText.currency());
    }

    /** Shared with the shop screen, which shows the same text in its status line. */
    public static String errorKey(TradeOutcome.Reason reason) {
        switch (reason) {
            case NOT_READY:
                return Lang.ERROR_NOT_READY;
            case NOT_TRADEABLE:
                return Lang.ERROR_NOT_TRADEABLE;
            case INSUFFICIENT_FUNDS:
                return Lang.ERROR_INSUFFICIENT_FUNDS;
            case INSUFFICIENT_ITEMS:
                return Lang.ERROR_INSUFFICIENT_ITEMS;
            case NON_DEFAULT_COMPONENTS:
                return Lang.ERROR_NON_DEFAULT_COMPONENTS;
            case RATE_LIMITED:
                return Lang.ERROR_RATE_LIMITED;
            case UNKNOWN_ITEM:
                return Lang.ERROR_UNKNOWN_ITEM;
            default:
                return Lang.ERROR_INVALID_AMOUNT;
        }
    }

    // ---- sending ------------------------------------------------------------------------

    public static void requestBuy(String itemId, int count) {
        sendTrade(ACTION_BUY, itemId, count);
    }

    public static void requestSell(String itemId, int count) {
        sendTrade(ACTION_SELL, itemId, count);
    }

    /** True when the server has this mod and the shop can be used. */
    public static boolean connected() {
        return networking != null && ClientShopState.get().hasPrices();
    }

    private static void sendTrade(byte action, String itemId, int count) {
        if (networking == null || count <= 0) {
            return;
        }
        ByteWriter out = new ByteWriter(64);
        out.writeByte(action);
        out.writeString(itemId);
        out.writeVarInt(count);
        networking.sendToServer(Channels.TRADE, out.toByteArray());
    }
}
