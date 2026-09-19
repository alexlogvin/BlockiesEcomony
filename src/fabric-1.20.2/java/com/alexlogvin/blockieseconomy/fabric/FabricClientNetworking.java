package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Networking;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * The client-only half of {@link FabricNetworking}, for the Minecraft 1.20.2 era.
 *
 * <p>Separate from its caller so a dedicated server never loads it. Fabric’s
 * {@code ClientPlayNetworking} lives in a client-only module, and a class that merely
 * mentions it is fine on a server right up until something causes it to be loaded.
 */
final class FabricClientNetworking {

    private static boolean attached;

    private FabricClientNetworking() {
    }

    static void attach(ResourceLocation channel, Map<String, Networking.ClientHandler> handlers) {
        if (attached) {
            return;
        }
        attached = true;
        ClientPlayNetworking.registerGlobalReceiver(channel,
                (client, listener, buf, responseSender) -> {
                    String name = buf.readUtf(64);
                    byte[] payload = buf.readByteArray();
                    Networking.ClientHandler handler = handlers.get(name);
                    if (handler == null) {
                        return;
                    }
                    // Off netty, onto the client thread: these handlers write the state the
                    // HUD and shop screen read while rendering.
                    client.execute(() -> {
                        try {
                            handler.handle(payload);
                        } catch (RuntimeException e) {
                            BlockiesEconomy.LOGGER.warn("Bad '{}' packet from the server: {}",
                                    name, e.toString());
                        }
                    });
                });
    }

    static void send(ResourceLocation channel, FriendlyByteBuf buf) {
        if (ClientPlayNetworking.canSend(channel)) {
            ClientPlayNetworking.send(channel, buf);
        }
    }
}
