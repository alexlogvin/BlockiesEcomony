package com.alexlogvin.blockieseconomy.forge;

import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.client.BalanceHud;
import com.alexlogvin.blockieseconomy.client.ClientHooks;
import com.alexlogvin.blockieseconomy.client.ConfigScreen;
import com.alexlogvin.blockieseconomy.client.ShopTooltip;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Forge client wiring (Minecraft 1.20.1).
 *
 * <p>Loaded only when {@code FMLEnvironment.dist} is the client, from the mod constructor.
 * Nothing in the shared entry point may reference this class, because everything it touches
 * is absent on a dedicated server.
 */
public final class ForgeClient {

    private static KeyMapping openShop;

    private ForgeClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ForgeClient::onRegisterKeyMappings);

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            // consumeClick drains a queue rather than reading a held state, so it has to be
            // polled every tick or presses arrive late.
            while (openShop != null && openShop.consumeClick()) {
                ClientHooks.openShop();
            }
        });

        // Drawn after the hotbar, which is the layer every HUD addition on 1.20.1 hangs
        // off. RenderGuiOverlayEvent does not fire at all while F1 is held, so the
        // hide-on-F1 rule costs nothing here.
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Post event) -> {
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
                BalanceHud.render(event.getGuiGraphics());
            }
        });

        // One hook covers JEI, REI and EMI at once: they all draw the vanilla tooltip.
        MinecraftForge.EVENT_BUS.addListener((ItemTooltipEvent event) ->
                ShopTooltip.append(event.getItemStack(), event.getToolTip()));

        MinecraftForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingIn event) -> ClientHooks.onJoinWorld());
        MinecraftForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut event) -> ClientHooks.onLeaveWorld());

        registerConfigScreen();

        ClientHooks.init();
    }

    /**
     * Enables the Config button beside this mod in the Mods list.
     *
     * <p>Forge asks for the factory through an extension point rather than a registry, so
     * this is the whole integration: hand it something that builds a Screen and the button
     * that was greyed out becomes live.
     */
    private static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ConfigScreen(parent)));
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        openShop = new KeyMapping(Lang.KEY_OPEN_SHOP, InputConstants.Type.KEYSYM,
                InputConstants.KEY_PERIOD, Lang.KEY_CATEGORY);
        event.register(openShop);
    }
}
