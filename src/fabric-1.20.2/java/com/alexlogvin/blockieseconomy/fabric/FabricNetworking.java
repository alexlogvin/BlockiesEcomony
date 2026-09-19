package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Networking;
import com.alexlogvin.blockieseconomy.platform.Profiles;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;

/**
 * Fabric networking for the Minecraft 1.20.2 era, on the raw-buffer API.
 *
 * <p>One Minecraft channel carries every logical channel, with the name written into the
 * payload. Vanilla channels are per-{@code ResourceLocation}, so the alternative is one
 * registration per message type — and from 1.20.5, where each needs a payload class and a
 * codec, that multiplies. Folding the name into the body keeps every era to a single
 * registration and makes adding a message a change in shared code only.
 *
 * <p>Receivers are handed the buffer on netty’s thread. The bytes are copied out there and
 * the handler runs through {@code server.execute}, because everything it goes on to touch —
 * the ledger, the player’s inventory — belongs to the game thread.
 */
public final class FabricNetworking implements Networking {

    private static final ResourceLocation CHANNEL =
            ResourceLocation.tryParse(BlockiesEconomy.MOD_ID + ":main");

    private final Map<String, ServerHandler> serverHandlers =
            new HashMap<String, ServerHandler>();
    private final Map<String, ClientHandler> clientHandlers =
            new HashMap<String, ClientHandler>();

    private boolean serverReceiverAttached;

    @Override
    public void registerServerbound(String channel, ServerHandler handler) {
        serverHandlers.put(channel, handler);
        attachServerReceiver();
    }

    private void attachServerReceiver() {
        if (serverReceiverAttached) {
            return;
        }
        serverReceiverAttached = true;
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, listener, buf, responseSender) -> {
                    String name = buf.readUtf(64);
                    byte[] payload = buf.readByteArray();
                    dispatch(server, player, name, payload);
                });
    }

    private void dispatch(BlockableEventLoop<?> server, ServerPlayer player, String name,
                          byte[] payload) {
        ServerHandler handler = serverHandlers.get(name);
        if (handler == null) {
            return;
        }
        server.execute(() -> {
            try {
                handler.handle(player, payload);
            } catch (RuntimeException e) {
                // A client can send anything. One bad packet must not take the server down.
                BlockiesEconomy.LOGGER.warn("Bad '{}' packet from {}: {}",
                        name, Profiles.nameOf(player.getGameProfile()), e.toString());
            }
        });
    }

    @Override
    public void registerClientbound(String channel, ClientHandler handler) {
        clientHandlers.put(channel, handler);
        FabricClientNetworking.attach(CHANNEL, clientHandlers);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, String channel, byte[] payload) {
        ServerPlayNetworking.send(player, CHANNEL, write(channel, payload));
    }

    @Override
    public void sendToServer(String channel, byte[] payload) {
        FabricClientNetworking.send(CHANNEL, write(channel, payload));
    }

    @Override
    public boolean canReceive(ServerPlayer player, String channel) {
        return ServerPlayNetworking.canSend(player, CHANNEL);
    }

    static FriendlyByteBuf write(String channel, byte[] payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(
                payload.length + channel.length() + 8));
        buf.writeUtf(channel, 64);
        buf.writeByteArray(payload);
        return buf;
    }
}
