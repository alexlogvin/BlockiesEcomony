package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws whatever this Minecraft version’s own settings screen draws behind itself.
 *
 * <p>The Minecraft 1.20.1 era, where {@code renderBackground} takes the graphics alone.
 * It gained the mouse position and partial tick in 1.20.2, to animate the panorama behind
 * the menus. A call cannot be written once for both the way an override can, so this is
 * one of the few classes that genuinely forks — hence {@code src/main-&lt;era&gt;}, not a
 * comment gate.
 *
 * <p>Here that means the dirt texture over the title screen and a dark gradient in a
 * world, which is exactly what vanilla Options looks like on this version.
 */
public final class ScreenBackground {

    private ScreenBackground() {
    }

    public static void draw(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                            float partialTick) {
        screen.renderBackground(graphics);
    }
}
