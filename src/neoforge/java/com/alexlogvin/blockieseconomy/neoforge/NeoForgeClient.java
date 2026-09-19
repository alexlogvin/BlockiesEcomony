package com.alexlogvin.blockieseconomy.neoforge;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.client.BalanceHud;
import com.alexlogvin.blockieseconomy.client.ClientHooks;
import com.alexlogvin.blockieseconomy.client.ConfigScreen;
import com.alexlogvin.blockieseconomy.client.ShopKeyMapping;
import com.alexlogvin.blockieseconomy.client.ShopTooltip;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client wiring.
 *
 * <p>Loaded only when {@code FMLEnvironment.dist} is the client, from the mod constructor.
 * Nothing in the shared entry point may reference this class, because everything it touches
 * is absent on a dedicated server.
 */
public final class NeoForgeClient {

    private static KeyMapping openShop;

    private NeoForgeClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        // Enables the Config button beside this mod in the Mods list. NeoForge keeps one
        // extension point per mod container rather than a registry, so this is the whole
        // integration: give it something that builds a Screen and the greyed-out button
        // becomes live.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (owner, parent) -> new ConfigScreen(parent));

        modBus.addListener(NeoForgeClient::onRegisterKeyMappings);
        modBus.addListener(NeoForgeClient::onRegisterGuiLayers);

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            // consumeClick drains a queue rather than reading a held state, so it has to be
            // polled every tick or presses arrive late.
            while (openShop != null && openShop.consumeClick()) {
                ClientHooks.openShop();
            }
        });

        // One hook covers JEI, REI and EMI at once: they all draw the vanilla tooltip.
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) ->
                ShopTooltip.append(event.getItemStack(), event.getToolTip()));

        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingIn event) -> ClientHooks.onJoinWorld());
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut event) -> ClientHooks.onLeaveWorld());

        ClientHooks.init();
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        openShop = ShopKeyMapping.create();
        event.register(openShop);
    }

    /**
     * Adds the balance as its own GUI layer.
     *
     * <p>A layer rather than a render event so it sits in the normal HUD stack and is
     * hidden by F1 along with everything else, without this mod having to check for it.
     */
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Ids.of(BlockiesEconomy.MOD_ID + ":balance"),
                (graphics, deltaTracker) -> BalanceHud.render(graphics));
    }

    /** True when this side has a client at all. */
    public static boolean isClient(Dist dist) {
        return dist.isClient();
    }
}
