package com.alexlogvin.blockieseconomy.neoforge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(BlockiesEconomy.MOD_ID)
public final class BlockiesEconomyNeoForge {

    public BlockiesEconomyNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        BlockiesEconomy.init();

        // Payload types can only be declared inside RegisterPayloadHandlersEvent, which
        // fires on the mod bus — the one thing a mod constructor has and nothing else does.
        NeoForgeNetworking.attachTo(modBus);

        if (dist.isClient()) {
            // Referenced behind the check, never above it: the client wiring touches HUD
            // and screen classes that a dedicated server does not have.
            NeoForgeClient.init(modBus, container);
        }
    }
}
