package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;

/**
 * The client-side lifecycle, in one place.
 *
 * <p>Each loader's client entry point calls into here rather than re-implementing the same
 * three steps. What differs between loaders is only <em>how</em> a hook is registered; what
 * happens when it fires is identical everywhere, so it belongs on this side of the line.
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    /** Wires the client's half of the protocol. Called once during client init. */
    public static void init() {
        ClientNetwork.register(Services.NETWORKING);
    }

    /** Called when the player enters a world, single player or otherwise. */
    public static void onJoinWorld() {
        ClientNetwork.onJoin(serverKey());
    }

    public static void onLeaveWorld() {
        ClientNetwork.onDisconnect();
    }

    /** Opens the shop. Bound to the keybind and to {@code /shop ui}. */
    public static void openShop() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.screen == null) {
            minecraft.setScreen(new ShopScreen());
        }
    }

    /**
     * Identifies the world or server the price cache belongs to.
     *
     * <p>Per server rather than global, because two servers can price the same item quite
     * differently — showing one server's economy while connected to another would be worse
     * than showing none at all.
     */
    private static String serverKey() {
        Minecraft minecraft = Minecraft.getInstance();

        ServerData remote = minecraft.getCurrentServer();
        if (remote != null && remote.ip != null && !remote.ip.isEmpty()) {
            return remote.ip;
        }

        MinecraftServer integrated = minecraft.getSingleplayerServer();
        if (integrated != null) {
            return "singleplayer/" + integrated.getWorldData().getLevelName();
        }

        return "unknown";
    }
}
