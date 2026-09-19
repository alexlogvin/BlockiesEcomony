package com.alexlogvin.blockieseconomy.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
 * <p>The 1.21.9 form, where input arrives as a record and the double-click flag is passed
 * alongside it.
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return onMouseClick(event.x(), event.y(), event.button())
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPress(event.key()) || super.keyPressed(event);
    }

    /**
     * Whether either shift key is held.
     *
     * <p>Read from the window rather than from {@code Screen}, whose static went away with
     * the loose arguments. The events carry their own modifier state, but the caller here is
     * a scroll handler, and scrolling is not one of the inputs that became an event.
     */
    protected static boolean shiftHeld() {
        return InputConstants.isKeyDown(
                        Minecraft.getInstance().getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(
                        Minecraft.getInstance().getWindow(), InputConstants.KEY_RSHIFT);
    }
}
