package com.alexlogvin.blockieseconomy.neoforge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Platform;
import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

/** NeoForge implementation of {@link Platform}. */
public final class NeoForgePlatform implements Platform {

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isPhysicalClient() {
        return FMLLoader.getDist().isClient();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public String modVersion() {
        return ModList.get().getModContainerById(BlockiesEconomy.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
