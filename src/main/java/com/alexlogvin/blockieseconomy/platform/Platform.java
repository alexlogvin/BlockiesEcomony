package com.alexlogvin.blockieseconomy.platform;

import java.nio.file.Path;

/**
 * Everything the mod needs from its loader.
 *
 * <p>This replaces Architectury. The mod ships no runtime dependency on another mod, and
 * Architectury API is itself an installed mod — so the abstraction is hand-written and
 * resolved through {@link java.util.ServiceLoader}. Dropping Architectury also removes its
 * limits: it publishes no Forge artifact past 1.20.4 and no Quilt artifact past 1.20.1.
 *
 * <p>Implementations live in {@code src/<loader>/java} and are registered in that loader's
 * {@code META-INF/services}. Keep this interface free of loader types.
 */
public interface Platform {

    /** Loader name for logs and {@code /shop}: fabric, quilt, forge or neoforge. */
    String loaderName();

    /** The game's config directory. The mod's own files live in a subfolder of it. */
    Path configDir();

    boolean isModLoaded(String modId);

    /**
     * True on a physical client. False on a dedicated server.
     *
     * <p>Not the same as "is this the client thread": a single-player game is a physical
     * client running an integrated server, and the shop is authoritative there too.
     */
    boolean isPhysicalClient();

    /** True in a development environment, where extra validation is worth the cost. */
    boolean isDevelopment();

    /** The mod's own version, for {@code /shop}. */
    String modVersion();
}
