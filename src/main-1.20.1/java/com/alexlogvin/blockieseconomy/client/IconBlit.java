package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a square GUI texture scaled down to a smaller box.
 *
 * <p>Exists only because {@code GuiGraphics.blit} was resignatured in 1.21.2, where it
 * gained a leading {@code Function<ResourceLocation, RenderType>}. That is a call, not an
 * override, so it cannot be written once for both — and rather than fork
 * {@link CoinIcon} and every other drawing class per era, the one incompatible line lives
 * here and everything above it stays shared.
 *
 * <p>This is the pre-1.21.2 form, where the render type is implied.
 */
public final class IconBlit {

    private IconBlit() {
    }

    /**
     * Draws {@code texture} at {@code size} pixels square.
     *
     * <p>{@code textureSize} is the texture's own edge length, and is passed as both the
     * source region and the texture dimensions so the whole image is sampled and scaled
     * into the destination box rather than cropped.
     */
    public static void draw(GuiGraphics graphics, ResourceLocation texture,
                            int x, int y, int size, int textureSize) {
        graphics.blit(texture, x, y, size, size, 0.0F, 0.0F,
                textureSize, textureSize, textureSize, textureSize);
    }
}
