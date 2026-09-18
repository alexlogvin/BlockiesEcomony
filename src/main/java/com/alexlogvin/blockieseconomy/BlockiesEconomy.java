package com.alexlogvin.blockieseconomy;

import com.alexlogvin.blockieseconomy.platform.ServerEvents;
import com.alexlogvin.blockieseconomy.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared entry point, compiled into every loader jar.
 *
 * <p>Each loader's own entry class calls {@link #init()} once its platform services are
 * registered. Nothing here touches loader APIs directly.
 */
public final class BlockiesEconomy {

    public static final String MOD_ID = "blockies_economy";
    public static final String MOD_NAME = "Blockies Economy";

    /** SLF4J ships with Minecraft itself, so this needs no loader-specific logger. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static EconomyServer economyServer;

    private BlockiesEconomy() {
    }

    public static void init() {
        if (economyServer != null) {
            return;
        }
        economyServer = new EconomyServer();
        economyServer.register(Services.load(ServerEvents.class));
    }

    /** The running server-side state, or null before {@link #init()}. */
    public static EconomyServer server() {
        return economyServer;
    }
}
