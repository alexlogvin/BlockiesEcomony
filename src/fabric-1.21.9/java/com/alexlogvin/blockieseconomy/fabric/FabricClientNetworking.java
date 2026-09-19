package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Networking;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The client-only half of {@link FabricNetworking}, for the Minecraft 1.21.5 era.
 *
 * <p>Separate from its caller so a dedicated server never loads it. Fabric’s
 * {@code ClientPlayNetworking} lives in a client-only module, and a class that merely
 * mentions it is fine on a server right up until something causes it to be loaded.
 */
final class FabricClientNetworking {

    private static boolean attached;

    private FabricClientNetworking() {
    }

    static void attach(Map<String, Networking.ClientHandler> handlers) {
        if (attached) {
            return;
        }
        attached = true;
        ClientPlayNetworking.registerGlobalReceiver(FabricNetworking.Envelope.TYPE,
                (payload, context) -> {
                    Networking.ClientHandler handler = handlers.get(payload.channel());
                    if (handler == null) {
                        return;
                    }
                    try {
                        handler.handle(payload.data());
                    } catch (RuntimeException e) {
                        BlockiesEconomy.LOGGER.warn("Bad '{}' packet from the server: {}",
                                payload.channel(), e.toString());
                    }
                });
    }

    static void send(FabricNetworking.Envelope envelope) {
        if (ClientPlayNetworking.canSend(FabricNetworking.Envelope.TYPE)) {
            ClientPlayNetworking.send(envelope);
        }
    }
}
