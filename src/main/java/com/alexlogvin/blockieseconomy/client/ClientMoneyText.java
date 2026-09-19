package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.core.money.MoneyFormat;
import net.minecraft.client.resources.language.I18n;

/**
 * Formats Blockies for the HUD and the shop screen, in the player's own language.
 *
 * <p>The counterpart to {@code MoneyText}, which the server uses. The split exists because
 * the server has no language: a suffix chosen there would be the server process's locale,
 * not the reader's. Here {@link I18n} is available and the labels come from the active
 * resource pack, so a Ukrainian client shows {@code тис.} where an English one shows
 * {@code K} — including the decimal separator, which is a comma in most of Europe.
 */
public final class ClientMoneyText {

    /** Resolved per call rather than cached: the player can change language mid-session. */
    private static final MoneyFormat.Labels LABELS = new MoneyFormat.Labels() {
        @Override
        public String suffix(MoneyFormat.Tier tier) {
            switch (tier) {
                case THOUSAND:
                    return I18n.get(Lang.SUFFIX_THOUSAND);
                case MILLION:
                    return I18n.get(Lang.SUFFIX_MILLION);
                case BILLION:
                    return I18n.get(Lang.SUFFIX_BILLION);
                case TRILLION:
                    return I18n.get(Lang.SUFFIX_TRILLION);
                default:
                    return "";
            }
        }

        @Override
        public String groupSeparator() {
            return I18n.get(Lang.GROUP_SEPARATOR);
        }

        @Override
        public String decimalSeparator() {
            return I18n.get(Lang.DECIMAL_SEPARATOR);
        }
    };

    private ClientMoneyText() {
    }

    public static String shortForm(long amount) {
        return MoneyFormat.shortForm(amount, LABELS);
    }

    public static String fullForm(long amount) {
        return MoneyFormat.fullForm(amount, LABELS);
    }

    /** The currency word, for tooltips that spell it out. */
    public static String currency() {
        return I18n.get(Lang.CURRENCY_SYMBOL);
    }
}
