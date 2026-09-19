package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.config.ClientConfig;
import com.alexlogvin.blockieseconomy.core.config.HudAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The balance readout drawn over the game.
 *
 * <p>Called from each loader's HUD hook, which is the one part of this that differs
 * between them. Everything about what is drawn and where lives here.
 */
public final class BalanceHud {

    /** Kept clear of the screen edge, before the player's own offset is applied. */
    private static final int MARGIN = 4;

    /** How big the coin is drawn on screen. */
    private static final int ICON_SIZE = 16;

    /**
     * The coin is nudged a pixel up and left of where the layout puts it.
     *
     * <p>Not arbitrary: the texture is 32 wide and the coin fills all but its outermost
     * row and column, so scaling to 16 samples the first screen pixel from the empty
     * texel and the coin lands a pixel down and right inside its own box. Correcting the
     * draw position is cheaper than a texture whose art runs to the very edge, where
     * there is no room left for the dark rim to read.
     */
    private static final int ICON_NUDGE = 1;

    private static final int GAP = 3;
    private static final int TEXT_COLOUR = 0xFFFFFF;

    private BalanceHud() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldRender(minecraft)) {
            return;
        }

        ClientConfig config = BlockiesEconomy.server().config().client();
        String text = ClientMoneyText.shortForm(ClientShopState.get().balance());
        boolean icon = config.showCurrencyIcon();

        int badgeWidth = icon ? ICON_SIZE : minecraft.font.width(ClientMoneyText.currency());
        int width = badgeWidth + GAP + minecraft.font.width(text);
        int height = icon ? ICON_SIZE : minecraft.font.lineHeight;

        HudAnchor anchor = config.anchor();
        int x = anchor.x(graphics.guiWidth(), width, MARGIN) + config.offsetX();
        int y = anchor.y(graphics.guiHeight(), height, MARGIN) + config.offsetY();

        if (icon) {
            CoinIcon.draw(graphics, x - ICON_NUDGE, y - ICON_NUDGE, ICON_SIZE);
        } else {
            graphics.drawString(minecraft.font, ClientMoneyText.currency(), x,
                    y + (height - minecraft.font.lineHeight) / 2, TEXT_COLOUR);
        }

        // Vertically centred against the icon, which is taller than a line of text.
        graphics.drawString(minecraft.font, text, x + badgeWidth + GAP,
                y + (height - minecraft.font.lineHeight) / 2, TEXT_COLOUR);
    }

    private static boolean shouldRender(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.options.hideGui) {
            return false;
        }
        // Any open screen hides it, including the shop's own — which shows the balance
        // itself, so leaving the HUD up would draw it twice.
        if (minecraft.screen != null) {
            return false;
        }
        if (!BlockiesEconomy.server().config().client().hudVisible()) {
            return false;
        }
        // Until the server has sent a balance, there is nothing to show. Drawing a 0 would
        // be a lie, and an alarming one for a player who has thousands.
        return ClientShopState.get().balanceKnown();
    }
}
