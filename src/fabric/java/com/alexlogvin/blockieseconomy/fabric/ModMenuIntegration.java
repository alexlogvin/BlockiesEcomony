package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.client.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The Config button in Mod Menu's mod list, which is Fabric and Quilt's answer to the one
 * Forge and NeoForge build in.
 *
 * <p>Mod Menu is a compile-only dependency and is never required. This class is reached
 * only through the {@code modmenu} entrypoint in {@code fabric.mod.json}, and a loader
 * resolves an entrypoint lazily — so with Mod Menu absent the class is never loaded, the
 * missing interface is never looked up, and nothing fails. That is the same trick the
 * recipe-viewer integrations use, and the reason this needs no {@code depends} entry.
 *
 * <p>The API is identical on Mod Menu 7 (Minecraft 1.20.1) and 11 (1.21.1), so one source
 * file covers both nodes.
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
