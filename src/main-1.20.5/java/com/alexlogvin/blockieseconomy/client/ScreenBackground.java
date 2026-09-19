package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws whatever this Minecraft version's own settings screen draws behind itself.
 *
 * <p>Split per version because the method changed shape in 1.20.5: it gained the mouse
 * position and partial tick, which it needs to animate the panorama. See the 1.20.1 copy
 * of this class, where it takes the graphics alone.
 *
 * <p>From 1.20.5 this is the blurred title-screen panorama over the menus and a dark
 * gradient in a world — the look vanilla's own settings screens have on this version.
 */
public final class ScreenBackground {

    private ScreenBackground() {
    }

    public static void draw(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                            float partialTick) {
        screen.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}
