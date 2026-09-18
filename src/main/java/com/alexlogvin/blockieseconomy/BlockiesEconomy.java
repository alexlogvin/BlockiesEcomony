package com.alexlogvin.blockieseconomy;

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

    private BlockiesEconomy() {
    }

    public static void init() {
        // M5 onward wires the price engine, ledger and commands here.
    }
}
