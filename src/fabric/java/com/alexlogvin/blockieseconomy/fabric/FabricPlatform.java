package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric implementation of {@link Platform}.
 *
 * <p>Also serves Quilt: Quilt Loader runs Fabric mods through its compatibility layer, and
 * with Quilted Fabric API retired there is no Quilt-native API left to target. The Quilt
 * jar reuses this source and differs only in its metadata file.
 */
public final class FabricPlatform implements Platform {

    @Override
    public String loaderName() {
        // Reported as "fabric" even under Quilt Loader, since that is the API in use.
        return "fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(BlockiesEconomy.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
