package com.alexlogvin.blockieseconomy.fabric;

import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.client.BalanceHud;
import com.alexlogvin.blockieseconomy.client.ClientHooks;
import com.alexlogvin.blockieseconomy.client.ShopKeyMapping;
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
 * Fabric/Quilt client entry point for the Minecraft 1.20.5 era.
 *
 * <p>Forks because Fabric API’s item-tooltip callback gained a tooltip-type argument in
 * 1.20.5. One source covers the whole era even though Fabric API’s HUD
 * callback changes shape inside it, because the lambda registered for it ignores its second
 * parameter and so has that parameter’s type inferred. The 1.20.6 and 1.21.1 jars differ by
 * exactly this one class; every other class in them is byte-identical.
 */
public final class BlockiesEconomyFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientHooks.init();

        // A standard KeyMapping rather than a hardcoded key, so it appears in Controls and
        // a player who already uses Period for something else can move it.
        KeyMapping openShop = KeyBindingHelper.registerKeyBinding(ShopKeyMapping.create());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick drains the queue, so this must run every tick regardless of
            // whether a screen is open, or presses pile up and fire late.
            while (openShop.consumeClick()) {
                ClientHooks.openShop();
            }
        });

        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> BalanceHud.render(graphics));

        // One hook covers JEI, REI and EMI at once: they all draw the vanilla tooltip.
        // The callback gained a tooltip-type argument in 1.20.5, which is why this line
        // differs from its 1.20.1 twin.
        ItemTooltipCallback.EVENT.register(
                (stack, context, type, lines) -> ShopTooltip.append(stack, lines));

        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> ClientHooks.onJoinWorld());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientHooks.onLeaveWorld());
    }
}
