package com.alexlogvin.blockieseconomy.neoforge;

import com.alexlogvin.blockieseconomy.platform.ServerEvents;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** NeoForge implementation of {@link ServerEvents}. */
public final class NeoForgeServerEvents implements ServerEvents {

    /**
     * The running server, captured at start.
     *
     * <p>Needed because the reload event does not carry it, and the reload
     * handler has to be told which server reloaded.
     */
    private static volatile MinecraftServer server;

    @Override
    public void onServerStarted(Consumer<MinecraftServer> handler) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, (ServerStartedEvent event) -> {
            server = event.getServer();
            handler.accept(event.getServer());
        });
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> handler) {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            handler.accept(event.getServer());
            server = null;
        });
    }

    @Override
    public void onDataPackReload(Consumer<MinecraftServer> handler) {
        // Through ReloadListeners, which is per-era: NeoForge renamed this event and gave
        // it a different addListener in 21.4.
        ReloadListeners.onDataPackReload(new ReloadHook(handler));
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> handler) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                handler.accept((ServerPlayer) event.getEntity());
            }
        });
    }

    @Override
    public void onPlayerLeave(Consumer<ServerPlayer> handler) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                handler.accept((ServerPlayer) event.getEntity());
            }
        });
    }

    @Override
    public void onPlayerDeath(Consumer<ServerPlayer> handler) {
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                handler.accept((ServerPlayer) event.getEntity());
            }
        });
    }

    @Override
    public void onAdvancementEarned(AdvancementListener handler) {
        NeoForge.EVENT_BUS.addListener((AdvancementEvent.AdvancementEarnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                handler.onEarned((ServerPlayer) event.getEntity(),
                        event.getAdvancement().id());
            }
        });
    }

    @Override
    public void onCommandRegistration(CommandListener handler) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                handler.onRegister(event.getDispatcher(),
                        event.getCommandSelection()
                                == net.minecraft.commands.Commands.CommandSelection.DEDICATED));
    }

    static MinecraftServer currentServer() {
        return server;
    }

    /**
     * Runs after the datapack reload completes.
     *
     * <p>Registered last so it sees a fully rebuilt recipe manager; a price graph solved
     * before recipes finish loading comes out silently empty.
     */
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
