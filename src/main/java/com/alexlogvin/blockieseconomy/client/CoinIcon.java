package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The currency symbol: the mod's coin, drawn wherever a balance is shown.
 *
 * <p>One definition rather than one per screen. The HUD and the shop both show a balance,
 * and having each keep its own copy of "which texture, how big, which blit overload" is
 * how they drift into looking like two different mods.
 *
 * <p>A texture rather than a rendered cobblestone item, which is what this used to be. An
 * item goes through the block model renderer every frame for a badge a few pixels across,
 * and it looks like whatever the player's resource pack says cobblestone looks like.
 */
public final class CoinIcon {

    /**
     * Built with {@code tryParse} rather than a constructor: the two-argument
     * {@code ResourceLocation} constructor is deprecated on 1.20.1 and gone by 1.21, while
     * {@code tryParse} is public on both. The id is a literal that cannot fail to parse.
     */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.tryParse(BlockiesEconomy.MOD_ID + ":textures/gui/coin.png");

    /**
     * The texture is 32x32 and is always drawn smaller than that.
     *
     * <p>Two texels per screen pixel at the HUD's 16, so the coin still has detail left at
     * GUI scale 2 and above, where a 16-pixel texture is magnified and goes visibly blocky
     * beside the crisp vanilla font next to it.
     */
    private static final int TEXTURE_SIZE = 32;

    private CoinIcon() {
    }

    /**
     * Draws the coin at {@code size} pixels square.
     *
     * <p>Through {@link GuiGraphicsCompat} because the blit that scales a 32-pixel texture
     * into a smaller box has been resignatured twice since 1.20.1. That is the only line of
     * this class that differs by version, so it is the only line that forks.
     */
    public static void draw(GuiGraphics graphics, int x, int y, int size) {
        GuiGraphicsCompat.drawIcon(graphics, TEXTURE, x, y, size, TEXTURE_SIZE);
    }
}
