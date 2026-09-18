package com.alexlogvin.blockieseconomy.forge;

import com.alexlogvin.blockieseconomy.platform.ServerEvents;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Forge implementation of {@link ServerEvents} (Minecraft 1.20.1).
 *
 * <p>Structurally identical to the NeoForge implementation — the two forked after 1.20.1
 * — but the packages differ ({@code net.minecraftforge} against {@code net.neoforged}),
 * and advancement identity still lives on {@code Advancement} rather than in a holder.
 */
public final class ForgeServerEvents implements ServerEvents {

    private static volatile MinecraftServer server;

    @Override
    public void onServerStarted(Consumer<MinecraftServer> handler) {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            server = event.getServer();
            handler.accept(event.getServer());
        });
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> handler) {
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            handler.accept(event.getServer());
            server = null;
        });
    }

    @Override
    public void onDataPackReload(Consumer<MinecraftServer> handler) {
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
                event.addListener(new ReloadHook(handler)));
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> handler) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                handler.accept((ServerPlayer) event.getEntity());
            }
        });
    }

    @Override
    public void onPlayerLeave(Consumer<ServerPlayer> handler) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                handler.accept((ServerPlayer) event.getEntity());
            }
        });
    }

    @Override
    public void onPlayerDeath(Consumer<ServerPlayer> handler) {
        MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                handler.accept((ServerPlayer) event.getEntity());
            }
        });
    }

    @Override
    public void onAdvancementEarned(AdvancementListener handler) {
        MinecraftForge.EVENT_BUS.addListener((AdvancementEvent.AdvancementEarnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                // On 1.20.1 the advancement still carries its own id.
                handler.onEarned((ServerPlayer) event.getEntity(),
                        event.getAdvancement().getId());
            }
        });
    }

    @Override
    public void onCommandRegistration(CommandListener handler) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                handler.onRegister(event.getDispatcher(),
                        event.getCommandSelection()
                                == net.minecraft.commands.Commands.CommandSelection.DEDICATED));
    }

    private static final class ReloadHook
            extends net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void> {

        private final Consumer<MinecraftServer> handler;

        ReloadHook(Consumer<MinecraftServer> handler) {
            this.handler = handler;
        }

        @Override
        protected Void prepare(net.minecraft.server.packs.resources.ResourceManager resourceManager,
                               net.minecraft.util.profiling.ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void unused,
                             net.minecraft.server.packs.resources.ResourceManager resourceManager,
                             net.minecraft.util.profiling.ProfilerFiller profiler) {
            MinecraftServer current = server;
            if (current != null) {
                handler.accept(current);
            }
        }
    }
}
