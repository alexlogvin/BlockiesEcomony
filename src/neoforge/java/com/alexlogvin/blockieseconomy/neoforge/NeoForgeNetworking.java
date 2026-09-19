package com.alexlogvin.blockieseconomy.neoforge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Networking;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge networking, on the payload API.
 *
 * <p>Like the Fabric implementations, one payload carries every logical channel with the
 * name written into the body — but in <em>two</em> types rather than one. NeoForge keeps a
 * single registry for both directions and refuses a second registration of the same id, so
 * the pair is split into {@code c2s} and {@code s2c}. Fabric, whose client-bound and
 * server-bound registries are separate, needs only one.
 *
 * <p>Payload types can only be declared inside {@code RegisterPayloadHandlersEvent}, which
 * fires on the mod bus after mod construction, so handlers registered during construction
 * are held here and attached when it arrives.
 *
 * <p>The registrar is marked {@code optional()}. Without that, NeoForge treats the channel
 * as required and refuses a vanilla client at login — which contradicts a design where the
 * server is authoritative and the client jar is a convenience.
 */
public final class NeoForgeNetworking implements Networking {

    public record ToServer(String channel, byte[] data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ToServer> TYPE =
                new CustomPacketPayload.Type<>(
                        // tryParse rather than fromNamespaceAndPath, which does not
                        // exist before 1.21. This file is shared by every NeoForge node,
                        // so one call that only resolves on the newest of them would fork
                        // the class across the whole loader for no behavioural difference.
                        // The id is a literal that cannot fail to parse.
                        ResourceLocation.tryParse(BlockiesEconomy.MOD_ID + ":c2s"));

        public static final StreamCodec<FriendlyByteBuf, ToServer> CODEC = StreamCodec.of(
                (buf, value) -> write(buf, value.channel(), value.data()),
                buf -> new ToServer(buf.readUtf(64), buf.readByteArray()));

        @Override
        public CustomPacketPayload.Type<ToServer> type() {
            return TYPE;
        }
    }

    public record ToClient(String channel, byte[] data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ToClient> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.tryParse(BlockiesEconomy.MOD_ID + ":s2c"));

        public static final StreamCodec<FriendlyByteBuf, ToClient> CODEC = StreamCodec.of(
                (buf, value) -> write(buf, value.channel(), value.data()),
                buf -> new ToClient(buf.readUtf(64), buf.readByteArray()));

        @Override
        public CustomPacketPayload.Type<ToClient> type() {
            return TYPE;
        }
    }

    private static void write(FriendlyByteBuf buf, String channel, byte[] data) {
        buf.writeUtf(channel, 64);
        buf.writeByteArray(data);
    }

    private static NeoForgeNetworking instance;

    private final Map<String, ServerHandler> serverHandlers = new HashMap<>();
    private final Map<String, ClientHandler> clientHandlers = new HashMap<>();

    public NeoForgeNetworking() {
        instance = this;
    }

    /** Called from the mod constructor, which is the only place with the mod bus. */
    public static void attachTo(IEventBus modBus) {
        modBus.addListener(NeoForgeNetworking::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        NeoForgeNetworking self = instance;
        if (self == null) {
            return;
        }
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToServer(ToServer.TYPE, ToServer.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    ServerHandler handler = self.serverHandlers.get(payload.channel());
                    if (handler == null || !(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    try {
                        handler.handle(player, payload.data());
                    } catch (RuntimeException e) {
                        // A client can send anything; one bad packet is not a crash.
                        BlockiesEconomy.LOGGER.warn("Bad '{}' packet from {}: {}",
                                payload.channel(), player.getGameProfile().getName(),
                                e.toString());
                    }
                }));

        registrar.playToClient(ToClient.TYPE, ToClient.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    ClientHandler handler = self.clientHandlers.get(payload.channel());
                    if (handler == null) {
                        return;
                    }
                    try {
                        handler.handle(payload.data());
                    } catch (RuntimeException e) {
                        BlockiesEconomy.LOGGER.warn("Bad '{}' packet from the server: {}",
                                payload.channel(), e.toString());
                    }
                }));
    }

    @Override
    public void registerServerbound(String channel, ServerHandler handler) {
        serverHandlers.put(channel, handler);
    }

    @Override
    public void registerClientbound(String channel, ClientHandler handler) {
        clientHandlers.put(channel, handler);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, String channel, byte[] payload) {
        PacketDistributor.sendToPlayer(player, new ToClient(channel, payload));
    }

    @Override
    public void sendToServer(String channel, byte[] payload) {
        PacketDistributor.sendToServer(new ToServer(channel, payload));
    }

    @Override
    public boolean canReceive(ServerPlayer player, String channel) {
        return player.connection.hasChannel(ToClient.TYPE);
    }
}
