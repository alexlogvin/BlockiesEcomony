package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.fabricmc.api.ModInitializer;

/** Fabric/Quilt entry point. Quilt Loader runs this via the Fabric compatibility layer. */
public final class BlockiesEconomyFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BlockiesEconomy.init();
    }
}
