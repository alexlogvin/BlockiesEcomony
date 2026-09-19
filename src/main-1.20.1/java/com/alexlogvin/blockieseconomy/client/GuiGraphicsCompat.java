package com.alexlogvin.blockieseconomy.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Every {@link GuiGraphics} call whose shape differs between Minecraft versions.
 *
 * <p>One class per era, and the only reason it exists: these are calls rather than
 * overrides, so no amount of care in the shared code makes them portable. Collecting them
 * here means the drawing code above stays shared instead of being copied per era — which,
 * for a screen of several hundred lines, is the difference between a fork and a rewrite.
 *
 * <p>The pre-1.21.2 form, before the render type became an argument.
 */
public final class GuiGraphicsCompat {

    private GuiGraphicsCompat() {
    }

    /**
     * Draws a square texture scaled into a smaller box.
     *
     * <p>{@code textureSize} is the texture's own edge length, passed as both the source
     * region and the texture dimensions so the whole image is sampled and scaled into the
     * destination box rather than cropped.
     */
    public static void drawIcon(GuiGraphics graphics, ResourceLocation texture,
                                int x, int y, int size, int textureSize) {
        graphics.blit(texture, x, y, size, size, 0.0F, 0.0F,
                textureSize, textureSize, textureSize, textureSize);
    }

    /**
     * Draws unshadowed text shrunk by {@code scale}, positioned in unscaled screen
     * coordinates.
     *
     * <p>The caller gives the position it wants the text to land at; dividing by the scale
     * to cancel the transform happens here, so that arithmetic is written once rather than
     * at every call site.
     */
    public static void drawScaledText(GuiGraphics graphics, Font font, String text,
                                      int x, int y, float scale, int colour) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, (int) (x / scale), (int) (y / scale), colour, false);
        graphics.pose().popPose();
    }

    /** Shows a multi-line tooltip beside the cursor. */
    public static void componentTooltip(GuiGraphics graphics, Font font,
                                        List<Component> lines, int x, int y) {
        graphics.renderComponentTooltip(font, lines, x, y);
    }
}
