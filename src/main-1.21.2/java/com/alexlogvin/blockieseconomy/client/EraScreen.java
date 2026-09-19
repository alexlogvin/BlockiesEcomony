package com.alexlogvin.blockieseconomy.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The base class this mod’s screens extend, absorbing the input signatures that change
 * between Minecraft versions.
 *
 * <p>Unlike the other per-era classes this one exists because the calls in question are
 * <em>overrides</em>. A call can be routed through a helper; an override cannot — its
 * signature has to match whatever the version declares, and 1.21.9 replaced the loose
 * arguments with {@code MouseButtonEvent} and {@code KeyEvent} records that do not exist
 * before it. Without this, both screens would fork per era, and between them they are the
 * better part of a thousand lines.
 *
 * <p>Subclasses override the hooks below instead, which stay the same everywhere. Each one
 * returns whether it consumed the input; anything it declines falls through to the widgets
 * in the usual way.
 *
 * <p>The pre-1.21.9 form, where input arrives as loose arguments.
 */
public abstract class EraScreen extends Screen {

    protected EraScreen(Component title) {
        super(title);
    }

    /** A click, before the widgets see it. */
    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        return false;
    }

    /** A key press, before the widgets see it. */
    protected boolean onKeyPress(int keyCode) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onMouseClick(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPress(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Whether either shift key is held. */
    protected static boolean shiftHeld() {
        return hasShiftDown();
    }
}
