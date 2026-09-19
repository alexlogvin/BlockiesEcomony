package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws whatever this Minecraft version’s own settings screen draws behind itself.
 *
 * <p>Minecraft 1.20.5 — 1.21.1. Line for line what the 1.20.2 era needs as well; it is
 * copied rather than shared because the two eras fork elsewhere, and a source directory
 * either belongs to an era or it does not.
 */
public final class ScreenBackground {

    private ScreenBackground() {
    }

    public static void draw(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                            float partialTick) {
        screen.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}
