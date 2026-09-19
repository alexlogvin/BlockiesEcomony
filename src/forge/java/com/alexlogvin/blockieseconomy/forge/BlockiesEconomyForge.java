package com.alexlogvin.blockieseconomy.forge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(BlockiesEconomy.MOD_ID)
public final class BlockiesEconomyForge {

    public BlockiesEconomyForge() {
        BlockiesEconomy.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Referenced behind the check, never above it: the client wiring touches HUD
            // and screen classes that a dedicated server does not have.
            ForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
