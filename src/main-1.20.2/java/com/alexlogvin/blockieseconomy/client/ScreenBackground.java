package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;


/**
 * Draws whatever this Minecraft version’s own settings screen draws behind itself.
 *
 * <p>Minecraft 1.20.2 — 1.20.4. {@code renderBackground} gained the mouse position and
 * partial tick here, which it needs to animate the panorama; see the 1.20.1 copy of this
 * class, where it takes the graphics alone.
 *
 * <p>From this version on that is the blurred title-screen panorama over the menus and a
 * dark gradient in a world — the look vanilla’s own settings screens have.
 */
public final class ScreenBackground {

    private ScreenBackground() {
    }

    public static void draw(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                            float partialTick) {
        screen.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}
