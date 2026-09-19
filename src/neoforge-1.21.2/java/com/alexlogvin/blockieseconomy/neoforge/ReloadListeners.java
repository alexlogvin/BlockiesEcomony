package com.alexlogvin.blockieseconomy.neoforge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Registers a datapack reload listener with NeoForge.
 *
 * <p>The one line of {@link NeoForgeServerEvents} that is not the same on every NeoForge
 * version. Keeping the difference here means the other hundred-odd lines of server wiring
 * stay shared across the whole loader.
 *
 * <p>This is the 21.4 form. The event was renamed from {@code AddReloadListenerEvent},
 * moved under a sorted base class, and now wants an id for the listener so that other mods
 * can declare an ordering against it.
 */
final class ReloadListeners {

    /**
     * What this mod's reload listener is called in NeoForge's dependency graph.
     *
     * <p>Named for what it does rather than for the mod, since it is the mod's namespace
     * that already says whose it is.
     */
    private static final ResourceLocation ID =
            ResourceLocation.tryParse(BlockiesEconomy.MOD_ID + ":prices");

    private ReloadListeners() {
    }

    static void onDataPackReload(PreparableReloadListener listener) {
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                event.addListener(ID, listener));
    }
}
