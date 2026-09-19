package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws whatever this Minecraft version’s own settings screen draws behind itself.
 *
 * <p>The Minecraft 1.21.2 era. {@code renderBackground} gained the mouse position and
 * partial tick in 1.20.2, which it needs to animate the panorama; see the 1.20.1 copy of
 * this class, where it takes the graphics alone.
 *
 * <p>Line for line the same in every era from 1.20.2 on. It is copied rather than shared
 * because an era owns a source directory as a whole, and these eras fork elsewhere.
 *
 * <p>What it draws is the blurred title-screen panorama over the menus and a dark gradient
 * in a world — the look vanilla’s own settings screens have.
 */
public final class ScreenBackground {

    private ScreenBackground() {
    }

    public static void draw(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                            float partialTick) {
        screen.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}
