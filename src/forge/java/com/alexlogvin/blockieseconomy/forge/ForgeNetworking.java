package com.alexlogvin.blockieseconomy.forge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Networking;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge networking (Minecraft 1.20.1), on {@code SimpleChannel}.
 *
 * <p>One channel and one message type, with the logical channel name in the body — the
 * same shape as every other loader here.
 *
 * <p>{@code acceptMissingOr} is what lets a vanilla client join. Forge's default is to
 * treat a registered channel as required and refuse a connection that lacks it, which for
 * this mod would be wrong: the server is authoritative and a client without the mod simply
 * uses commands instead of a screen.
 */
public final class ForgeNetworking implements Networking {

    private static final String PROTOCOL = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.tryParse(BlockiesEconomy.MOD_ID + ":main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .simpleChannel();

    private static final Map<String, ServerHandler> SERVER_HANDLERS =
            new HashMap<String, ServerHandler>();
    private static final Map<String, ClientHandler> CLIENT_HANDLERS =
            new HashMap<String, ClientHandler>();

    static {
        // Registered eagerly at class load: Forge wants every message declared before the
        // first connection, and this class is loaded during mod construction.
        CHANNEL.registerMessage(0, Envelope.class,
                Envelope::encode, Envelope::decode, ForgeNetworking::handle);
    }

    @Override
    public void registerServerbound(String channel, ServerHandler handler) {
        SERVER_HANDLERS.put(channel, handler);
    }

    @Override
    public void registerClientbound(String channel, ClientHandler handler) {
        CLIENT_HANDLERS.put(channel, handler);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, String channel, byte[] payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Envelope(channel, payload));
    }

    @Override
    public void sendToServer(String channel, byte[] payload) {
        CHANNEL.sendToServer(new Envelope(channel, payload));
    }

    @Override
    public boolean canReceive(ServerPlayer player, String channel) {
        // The raw Connection is a public field on 1.20.1; the getter only arrives in 1.20.2.
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    private static void handle(Envelope envelope, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        boolean toServer = context.getDirection() == NetworkDirection.PLAY_TO_SERVER;

        context.enqueueWork(() -> {
            try {
                if (toServer) {
                    ServerHandler handler = SERVER_HANDLERS.get(envelope.channel);
                    ServerPlayer sender = context.getSender();
                    if (handler != null && sender != null) {
                        handler.handle(sender, envelope.data);
                    }
                } else {
                    ClientHandler handler = CLIENT_HANDLERS.get(envelope.channel);
                    if (handler != null) {
                        handler.handle(envelope.data);
                    }
                }
            } catch (RuntimeException e) {
                // A client can send anything. One bad packet must not take the server down.
                BlockiesEconomy.LOGGER.warn("Bad '{}' packet: {}", envelope.channel,
                        e.toString());
            }
        });
        context.setPacketHandled(true);
    }

    /** The single message type: a logical channel name and an opaque body. */
    public static final class Envelope {

        private final String channel;
        private final byte[] data;

        Envelope(String channel, byte[] data) {
            this.channel = channel;
            this.data = data;
        }

        static void encode(Envelope envelope, FriendlyByteBuf buf) {
            buf.writeUtf(envelope.channel, 64);
            buf.writeByteArray(envelope.data);
        }

        static Envelope decode(FriendlyByteBuf buf) {
            return new Envelope(buf.readUtf(64), buf.readByteArray());
        }
    }
}
