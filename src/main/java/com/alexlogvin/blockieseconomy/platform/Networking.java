package com.alexlogvin.blockieseconomy.platform;

import net.minecraft.server.level.ServerPlayer;

/**
 * Moving opaque byte arrays between client and server, one implementation per loader.
 *
 * <p>Payloads are {@code byte[]} rather than {@code FriendlyByteBuf} on purpose. Minecraft
 * replaced raw-buffer channels with {@code CustomPacketPayload} and {@code StreamCodec} in
 * 1.20.5, and the four loaders wrap that differently again. Keeping the shared code to
 * "here are some bytes, put them on that channel" confines all of it to four small classes
 * rather than to every packet the mod defines. Encoding lives in
 * {@code com.alexlogvin.blockieseconomy.core.net}, which needs no Minecraft types at all.
 *
 * <p><b>Threading.</b> Implementations must hand handlers their payload on the game thread,
 * not on netty's. Every handler here touches world state, a ledger or the client's screen,
 * none of which is safe off-thread. Payload bytes must be copied off the network buffer
 * before the hand-off, because the buffer is released the moment the netty handler returns.
 *
 * <p><b>Registration.</b> {@code register*} is called during mod construction, before the
 * loader's own registration event fires. Implementations record the handlers and attach
 * them when their loader lets them.
 */
public interface Networking {

    /** Receives a payload from a client. Called on the server thread. */
    interface ServerHandler {
        void handle(ServerPlayer player, byte[] payload);
    }

    /** Receives a payload from the server. Called on the client thread. */
    interface ClientHandler {
        void handle(byte[] payload);
    }

    void registerServerbound(String channel, ServerHandler handler);

    /** Client side only. Never called on a dedicated server. */
    void registerClientbound(String channel, ClientHandler handler);

    void sendToPlayer(ServerPlayer player, String channel, byte[] payload);

    /** Client side only. */
    void sendToServer(String channel, byte[] payload);

    /**
     * Whether this player's client can accept the channel.
     *
     * <p>False for a vanilla client, or one without the mod. The server stays playable for
     * them — they lose the GUI and HUD, not the shop — so this is a check before sending,
     * not a reason to disconnect anyone.
     */
    boolean canReceive(ServerPlayer player, String channel);
}
