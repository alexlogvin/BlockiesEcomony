package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * The keybind that opens the shop.
 *
 * <p>Built here rather than at the two places that register it, because both loaders want
 * the same mapping and 1.21.9 changed how a keybind names its category: what was a
 * translation key became a {@code KeyMapping.Category} that has to be registered and
 * derives its own key.
 *
 * <p>A standard {@code KeyMapping} rather than a hardcoded key, so it appears in Controls
 * and a player who already uses Period for something else can move it.
 *
 * <p>The 1.21.11 form. A category is a registered object identified by an Identifier,
 * and it derives its own translation key from that — which is why this era reads
 * `key.category.blockies_economy.shop` where the others read `key.categories.blockies_economy`.
 */
public final class ShopKeyMapping {

    private ShopKeyMapping() {
    }

    /**
     * Registered once, at class load.
     *
     * <p>Registering the same id twice would be a mistake, and this class is only touched
     * by whichever loader is present.
     */
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.tryParse(BlockiesEconomy.MOD_ID + ":shop"));

    /** A fresh mapping. The caller registers it with its own loader. */
    public static KeyMapping create() {
        return new KeyMapping(Lang.KEY_OPEN_SHOP, InputConstants.Type.KEYSYM,
                InputConstants.KEY_PERIOD, CATEGORY);
    }
}
