package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Networking;
import com.alexlogvin.blockieseconomy.platform.Profiles;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric networking for the Minecraft 1.20.5 era, on the payload API introduced in 1.20.5.
 *
 * <p>Same shape as the raw-buffer implementation the older eras use, and deliberately so:
 * one Minecraft channel carries every logical channel, with the name written into the
 * payload. Here that choice pays for itself twice over, because on these versions each
 * channel would otherwise need its own payload class, codec and pair of registrations.
 *
 * <p>Fabric hands these handlers their payload on the game thread already, so unlike the
 * raw-buffer implementation there is nothing to queue.
 */
public final class FabricNetworking implements Networking {

    /** One payload type for the whole mod; {@code channel} says what it is. */
    public record Envelope(String channel, byte[] data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Envelope> TYPE =
                new CustomPacketPayload.Type<>(
                        // tryParse rather than fromNamespaceAndPath, which does not exist
                        // before 1.21 and would fork this class across the era for no
                        // behavioural difference. The id is a literal that cannot fail to
                        // parse. CoinIcon does the same, for the same reason.
                        ResourceLocation.tryParse(BlockiesEconomy.MOD_ID + ":main"));

        public static final StreamCodec<FriendlyByteBuf, Envelope> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.channel(), 64);
                    buf.writeByteArray(value.data());
                },
                buf -> new Envelope(buf.readUtf(64), buf.readByteArray()));

        @Override
        public CustomPacketPayload.Type<Envelope> type() {
            return TYPE;
        }
    }

    private final Map<String, ServerHandler> serverHandlers = new HashMap<>();
    private final Map<String, ClientHandler> clientHandlers = new HashMap<>();

    private boolean typesRegistered;
    private boolean serverReceiverAttached;

    /**
     * Declares the payload type in both directions.
     *
     * <p>Both are declared on both sides. A dedicated server has to know the client-bound
     * type to encode it, and a client has to know the server-bound one to send it, so
     * splitting this by side would break the half that is doing the sending.
     */
    private void registerTypes() {
        if (typesRegistered) {
            return;
        }
        typesRegistered = true;
        PayloadTypeRegistry.playC2S().register(Envelope.TYPE, Envelope.CODEC);
        PayloadTypeRegistry.playS2C().register(Envelope.TYPE, Envelope.CODEC);
    }

    @Override
    public void registerServerbound(String channel, ServerHandler handler) {
        registerTypes();
        serverHandlers.put(channel, handler);
        if (serverReceiverAttached) {
            return;
        }
        serverReceiverAttached = true;
        ServerPlayNetworking.registerGlobalReceiver(Envelope.TYPE, (payload, context) -> {
            ServerHandler target = serverHandlers.get(payload.channel());
            if (target == null) {
                return;
            }
            try {
                target.handle(context.player(), payload.data());
            } catch (RuntimeException e) {
                // A client can send anything. One bad packet must not take the server down.
                BlockiesEconomy.LOGGER.warn("Bad '{}' packet from {}: {}",
                        payload.channel(), Profiles.nameOf(context.player().getGameProfile()),
                        e.toString());
            }
        });
    }

    @Override
    public void registerClientbound(String channel, ClientHandler handler) {
        registerTypes();
        clientHandlers.put(channel, handler);
        FabricClientNetworking.attach(clientHandlers);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, String channel, byte[] payload) {
        ServerPlayNetworking.send(player, new Envelope(channel, payload));
    }

    @Override
    public void sendToServer(String channel, byte[] payload) {
        FabricClientNetworking.send(new Envelope(channel, payload));
    }

    @Override
    public boolean canReceive(ServerPlayer player, String channel) {
        return ServerPlayNetworking.canSend(player, Envelope.TYPE);
    }
}
