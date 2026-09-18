package com.alexlogvin.blockieseconomy.command;

import com.alexlogvin.blockieseconomy.core.money.MoneyFormat;

/**
 * Formats Blockies for messages the server sends.
 *
 * <p>Uses the plain K/M/B/T labels rather than translated ones, and that is deliberate: a
 * server has no language of its own, so a translated suffix baked into a chat message
 * would be whichever locale the server process happens to run in, not the reader's.
 *
 * <p>The client renders its own HUD and shop text through
 * {@link com.alexlogvin.blockieseconomy.core.money.MoneyFormat.Labels} backed by the
 * active language, so numbers shown in the UI <em>are</em> localised. That lives in the
 * client source set, where referencing {@code net.minecraft.client} is safe — doing it
 * from here would risk tripping side-stripping on Forge and NeoForge.
 */
public final class MoneyText {

    private MoneyText() {
    }

    /** Short form, e.g. {@code 3.5M}. */
    public static String shortForm(long amount) {
        return MoneyFormat.shortForm(amount, MoneyFormat.DEFAULT);
    }

    /** Exact value with grouping, e.g. {@code 3,500,000}. */
    public static String fullForm(long amount) {
        return MoneyFormat.fullForm(amount, MoneyFormat.DEFAULT);
    }

    /** Short form with the exact value in parentheses, as {@code /shop balance} specifies. */
    public static String balanceForm(long amount) {
        return MoneyFormat.balanceForm(amount, MoneyFormat.DEFAULT);
    }
}
