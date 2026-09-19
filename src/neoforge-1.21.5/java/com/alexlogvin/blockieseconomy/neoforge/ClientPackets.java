package com.alexlogvin.blockieseconomy.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a payload to the server.
 *
 * <p>Separate from {@link NeoForgeNetworking} so a dedicated server never loads it, the
 * same reasoning as the Fabric side: from NeoForge 21.6 the client-bound distributor sits
 * in a client-only package, and naming it from a class the server loads would put a client
 * class within its reach. A class is loaded when something first calls into it, and
 * nothing on a server ever calls this.
 *
 * <p>The pre-21.6 form, where one distributor serves both directions.
 */
final class ClientPackets {

    private ClientPackets() {
    }

    static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
