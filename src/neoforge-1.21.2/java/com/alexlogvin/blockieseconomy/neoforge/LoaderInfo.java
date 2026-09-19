package com.alexlogvin.blockieseconomy.neoforge;

import net.neoforged.fml.loading.FMLLoader;

/**
 * What NeoForge knows about the environment it is running in.
 *
 * <p>Forks per era because {@code FMLLoader} turned its statics into instance methods
 * reached through {@code getCurrent()} in 21.10. Two calls, kept here so
 * {@link NeoForgePlatform} does not fork over them.
 *
 * <p>The pre-21.10 form, where the loader answers statically.
 */
final class LoaderInfo {

    private LoaderInfo() {
    }

    /** Whether this side has a client at all. */
    static boolean isPhysicalClient() {
        return FMLLoader.getDist().isClient();
    }

    /** Whether this is a development run rather than a shipped installation. */
    static boolean isDevelopment() {
        return !FMLLoader.isProduction();
    }
}
