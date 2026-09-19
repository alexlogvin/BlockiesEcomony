package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshot;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Adds a buy/sell line to item tooltips.
 *
 * <p>This is the recipe-viewer integration that always works. JEI, REI and EMI all render
 * the vanilla tooltip, so one hook covers all three — and covers the inventory, the
 * creative menu and every other screen besides, with no dependency on any of them and
 * nothing to break when one of them changes its API.
 *
 * <p>Deliberately not a substitute for buying from inside a viewer. It is the floor of the
 * capability ladder, and it is a floor that cannot fall out.
 */
public final class ShopTooltip {

    private ShopTooltip() {
    }

    /**
     * Appends the price line, if there is one.
     *
     * <p>Called from each loader's tooltip hook with the list it is building.
     */
    public static void append(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty() || lines == null) {
            return;
        }
        if (!BlockiesEconomy.server().config().client().tooltipPrices()) {
            return;
        }

        ShopSnapshot snapshot = ClientShopState.get().snapshot();
        if (snapshot == null) {
            return;
        }

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            // An item another mod created without registering. Rare, but a tooltip runs for
            // every stack the player points at, so it is not the place to throw.
            return;
        }

        long buy = snapshot.buyPrice(key.toString());
        if (buy < 0L) {
            // Not tradeable. Saying so on every unpriced item would double the length of
            // half the tooltips in the game for no information.
            return;
        }

        lines.add(Component.translatable(Lang.TOOLTIP_PRICE,
                        ClientMoneyText.fullForm(buy),
                        ClientMoneyText.fullForm(snapshot.sellPrice(key.toString())))
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
