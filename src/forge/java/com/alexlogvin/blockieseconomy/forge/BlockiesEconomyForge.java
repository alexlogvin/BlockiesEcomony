package com.alexlogvin.blockieseconomy.forge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(BlockiesEconomy.MOD_ID)
public final class BlockiesEconomyForge {

    /**
     * Takes the loading context rather than reaching for it statically.
     *
     * <p>{@code FMLJavaModLoadingContext.get()} and {@code ModLoadingContext.get()} are
     * both deprecated for removal: they read a thread local, which only holds the right
     * value while the mod constructor is on the stack. Forge injects the context into this
     * constructor instead, and since {@code FMLJavaModLoadingContext} extends
     * {@code ModLoadingContext}, this one object covers both the mod event bus and the
     * extension points. Nothing here needs the static accessors.
     */
    public BlockiesEconomyForge(FMLJavaModLoadingContext context) {
        BlockiesEconomy.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Referenced behind the check, never above it: the client wiring touches HUD
            // and screen classes that a dedicated server does not have.
            ForgeClient.init(context);
        }
    }
}
