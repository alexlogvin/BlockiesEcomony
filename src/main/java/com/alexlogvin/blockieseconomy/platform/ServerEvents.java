package com.alexlogvin.blockieseconomy.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side hooks, one implementation per loader.
 *
 * <p>Callbacks are expressed in types that are stable across the supported Minecraft
 * versions. An advancement in particular is reported as its id in string form rather than
 * as the advancement object, or even as the game's own id type: 1.20.1 has
 * {@code Advancement} where later versions have {@code AdvancementHolder}, and 1.21.11
 * renamed {@code ResourceLocation} to {@code Identifier}. Resolving that is each
 * implementation's job, not the shared code's — which only ever wanted the string.
 *
 * <p>Register handlers during mod construction. Every callback fires on the server thread.
 */
public interface ServerEvents {

    /** Fires when a player earns an advancement, including a criterion-completed one. */
    interface AdvancementListener {
        void onEarned(ServerPlayer player, String advancementId);
    }

    /** Fires while the server builds its command tree. */
    interface CommandListener {
        void onRegister(CommandDispatcher<CommandSourceStack> dispatcher,
                        boolean dedicatedServer);
    }

    /**
     * Fires once the server is up and its datapacks have loaded.
     *
     * <p>This is the earliest point the recipe manager is populated, which is what the
     * price solver needs.
     */
    void onServerStarted(Consumer<MinecraftServer> handler);

    /** Fires as the server shuts down. Used to flush balances and drop caches. */
    void onServerStopping(Consumer<MinecraftServer> handler);

    /**
     * Fires after a datapack reload, including {@code /reload}.
     *
     * <p>Essential rather than optional: {@code /reload} replaces the recipe manager, so a
     * price graph built at startup is stale afterwards. Missing this hook is the classic
     * way to end up with a silently empty shop.
     */
    void onDataPackReload(Consumer<MinecraftServer> handler);

    void onPlayerJoin(Consumer<ServerPlayer> handler);

    void onPlayerLeave(Consumer<ServerPlayer> handler);

    /** Fires when a player dies, for the configurable death penalty. */
    void onPlayerDeath(Consumer<ServerPlayer> handler);

    void onAdvancementEarned(AdvancementListener handler);

    void onCommandRegistration(CommandListener handler);
}
