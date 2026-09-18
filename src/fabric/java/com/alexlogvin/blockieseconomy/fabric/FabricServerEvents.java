package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.platform.ServerEvents;
import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric (and Quilt) implementation of {@link ServerEvents}.
 *
 * <p>Everything here comes from Fabric API except advancements, which Fabric API does not
 * expose an event for. Those arrive through a Mixin on {@code PlayerAdvancements#award}
 * that calls {@link AdvancementBridge}.
 */
public final class FabricServerEvents implements ServerEvents {

    @Override
    public void onServerStarted(Consumer<net.minecraft.server.MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STARTED.register(handler::accept);
    }

    @Override
    public void onServerStopping(Consumer<net.minecraft.server.MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STOPPING.register(handler::accept);
    }

    @Override
    public void onDataPackReload(Consumer<net.minecraft.server.MinecraftServer> handler) {
        // Fires after /reload too, which is the point: a reload replaces the recipe
        // manager, so any price graph built earlier is stale.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> {
                    if (success) {
                        handler.accept(server);
                    }
                });
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> handler) {
        ServerPlayConnectionEvents.JOIN.register(
                (listener, sender, server) -> handler.accept(listener.player));
    }

    @Override
    public void onPlayerLeave(Consumer<ServerPlayer> handler) {
        ServerPlayConnectionEvents.DISCONNECT.register(
                (listener, server) -> handler.accept(listener.player));
    }

    @Override
    public void onPlayerDeath(Consumer<ServerPlayer> handler) {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer) {
                handler.accept((ServerPlayer) entity);
            }
        });
    }

    @Override
    public void onAdvancementEarned(AdvancementListener handler) {
        AdvancementBridge.register(handler);
    }

    @Override
    public void onCommandRegistration(CommandListener handler) {
        CommandRegistrationCallback.EVENT.register(
                (CommandDispatcher<CommandSourceStack> dispatcher,
                 net.minecraft.commands.CommandBuildContext context,
                 Commands.CommandSelection selection) ->
                        handler.onRegister(dispatcher,
                                selection == Commands.CommandSelection.DEDICATED));
    }
}
