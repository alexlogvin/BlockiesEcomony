package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws whatever this Minecraft version's own settings screen draws behind itself.
 *
 * <p>Split per version because the method changed shape in 1.20.5: here it takes only the
 * graphics, and from 1.20.5 it also takes the mouse position and partial tick. A call
 * cannot be written once for both the way an override can, so this is one of the few
 * classes that genuinely forks — hence {@code src/main-&lt;mc&gt;}, not a comment gate.
 *
 * <p>On 1.20.1 that means the dirt texture over the title screen and a dark gradient in a
 * world, which is exactly what vanilla Options looks like here. The blurred panorama
 * arrives with 1.20.5; see the 1.21.1 copy of this class.
 */
public final class ScreenBackground {

    private ScreenBackground() {
    }

    public static void draw(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                            float partialTick) {
        screen.renderBackground(graphics);
    }
}
