package com.alexlogvin.blockieseconomy.platform;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Reading a player’s name and id, including for players who are not online.
 *
 * <p>Forks per era because {@code GameProfile} became a record in the authlib that ships
 * with 1.21.9, renaming its accessors, and the server’s profile cache was replaced at the
 * same time. Both are used from several places in shared code, so the three calls that
 * changed are collected here rather than spread through it.
 *
 * <p>The offline lookups are what make the admin commands useful: investigating a report
 * almost always happens after the player has logged off.
 *
 * <p>The pre-1.21.9 form, where a profile has getters and the server owns a profile cache.
 */
public final class Profiles {

    private Profiles() {
    }

    /** The name on a profile. */
    public static String nameOf(GameProfile profile) {
        return profile.getName();
    }

    /** The id the server last saw for {@code name}, or null if it has never seen it. */
    public static UUID idFor(MinecraftServer server, String name) {
        if (server.getProfileCache() == null) {
            return null;
        }
        Optional<GameProfile> cached = server.getProfileCache().get(name);
        return cached.map(GameProfile::getId).orElse(null);
    }

    /** The name the server last saw for {@code uuid}, or null if it has never seen it. */
    public static String nameFor(MinecraftServer server, UUID uuid) {
        if (server.getProfileCache() == null) {
            return null;
        }
        Optional<GameProfile> cached = server.getProfileCache().get(uuid);
        return cached.map(GameProfile::getName).orElse(null);
    }
}
