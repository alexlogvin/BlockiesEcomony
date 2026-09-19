package com.alexlogvin.blockieseconomy.platform;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

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
 * <p>The 1.21.9 form. A profile is a record, and the profile cache became a name-to-id
 * resolver hanging off the server’s services.
 */
public final class Profiles {

    private Profiles() {
    }

    /** The name on a profile. */
    public static String nameOf(GameProfile profile) {
        return profile.name();
    }

    /** The id the server last saw for {@code name}, or null if it has never seen it. */
    public static UUID idFor(MinecraftServer server, String name) {
        return server.services().nameToIdCache().get(name).map(NameAndId::id).orElse(null);
    }

    /** The name the server last saw for {@code uuid}, or null if it has never seen it. */
    public static String nameFor(MinecraftServer server, UUID uuid) {
        return server.services().nameToIdCache().get(uuid).map(NameAndId::name).orElse(null);
    }
}
