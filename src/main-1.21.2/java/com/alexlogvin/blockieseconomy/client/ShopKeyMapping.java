package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

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
 * <p>The pre-1.21.9 form, where the category is a translation key.
 */
public final class ShopKeyMapping {

    private ShopKeyMapping() {
    }

    /** A fresh mapping. The caller registers it with its own loader. */
    public static KeyMapping create() {
        return new KeyMapping(Lang.KEY_OPEN_SHOP, InputConstants.Type.KEYSYM,
                InputConstants.KEY_PERIOD, Lang.KEY_CATEGORY);
    }
}
