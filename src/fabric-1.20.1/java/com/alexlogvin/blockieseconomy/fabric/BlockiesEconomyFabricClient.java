package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.client.BalanceHud;
import com.alexlogvin.blockieseconomy.client.ClientHooks;
import com.alexlogvin.blockieseconomy.client.ShopTooltip;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;

/**
 * Fabric/Quilt client entry point for Minecraft 1.20.1.
 *
 * <p>Forks because Fabric API’s item-tooltip callback gained a tooltip-type argument in
 * 1.20.5. The HUD callback changed too — a float partial tick up to 1.20.6, a
 * {@code DeltaTracker} from 1.21 — but that one is registered with a lambda whose second
 * parameter is unused and therefore inferred, so it is not what splits this class.
 */
public final class BlockiesEconomyFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientHooks.init();

        // A standard KeyMapping rather than a hardcoded key, so it appears in Controls and
        // a player who already uses Period for something else can move it.
        KeyMapping openShop = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                Lang.KEY_OPEN_SHOP,
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_PERIOD,
                Lang.KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick drains the queue, so this must run every tick regardless of
            // whether a screen is open, or presses pile up and fire late.
            while (openShop.consumeClick()) {
                ClientHooks.openShop();
            }
        });

        HudRenderCallback.EVENT.register((graphics, partialTick) -> BalanceHud.render(graphics));

        // One hook covers JEI, REI and EMI at once: they all draw the vanilla tooltip.
        ItemTooltipCallback.EVENT.register(
                (stack, context, lines) -> ShopTooltip.append(stack, lines));

        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> ClientHooks.onJoinWorld());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientHooks.onLeaveWorld());
    }
}
