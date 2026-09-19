package com.alexlogvin.blockieseconomy.neoforge;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Registers a datapack reload listener with NeoForge.
 *
 * <p>The one line of {@link NeoForgeServerEvents} that is not the same on every NeoForge
 * version. In 21.4 the event was renamed, moved under a sorted base class, and its
 * {@code addListener} began requiring an id. Keeping the difference here means the other
 * hundred-odd lines of server wiring stay shared across the whole loader.
 *
 * <p>This is the pre-21.4 form, where a listener is added anonymously.
 */
final class ReloadListeners {

    private ReloadListeners() {
    }

    static void onDataPackReload(PreparableReloadListener listener) {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
                event.addListener(listener));
    }
}
