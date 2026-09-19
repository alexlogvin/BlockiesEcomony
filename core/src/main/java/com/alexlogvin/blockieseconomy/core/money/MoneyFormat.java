package com.alexlogvin.blockieseconomy.core.money;

/**
 * Renders a balance for display.
 *
 * <p>Two forms exist, as the commands specify: a short form for the HUD and grids
 * ({@code 3.5M}) and a full form for the exact number ({@code 3,500,000}).
 *
 * <p>Suffixes and separators come from a {@link Labels} provider rather than being
 * hardcoded, because they are translated: English uses K/M/B/T, Ukrainian uses
 * тис./млн/млрд/трлн, and the decimal separator differs too. This class lives in
 * {@code :core} and has no access to Minecraft's translation system, so the platform
 * layer supplies a {@code Labels} implementation backed by the language files.
 */
public final class MoneyFormat {

    /** Magnitude tiers a balance can fall into. */
    public enum Tier {
        ONE(1L),
        THOUSAND(1_000L),
        MILLION(1_000_000L),
        BILLION(1_000_000_000L),
        TRILLION(1_000_000_000_000L);

        private final long divisor;

        Tier(long divisor) {
            this.divisor = divisor;
        }

        public long divisor() {
            return divisor;
        }
    }

    /** Supplies the localised pieces of a formatted amount. */
    public interface Labels {
        /** Suffix for a tier; the empty string for {@link Tier#ONE}. */
        String suffix(Tier tier);

        /** Separator between groups of three digits in the full form. */
        String groupSeparator();

        /** Separator before the single decimal place in the short form. */
        String decimalSeparator();
    }

    /**
     * English fallback, used when no translation is available.
     *
     * <p>Note that the billions suffix {@code B} collides with the Blockies symbol
     * {@code B}, giving {@code 3.0B B}. The HUD renders a cobblestone icon rather than
     * the letter by default, so this only surfaces for a player who both disabled the
     * icon and reached a billion Blockies. Translations are free to pick something else.
     */
    public static final Labels DEFAULT = new Labels() {
        @Override
        public String suffix(Tier tier) {
            switch (tier) {
                case THOUSAND: return "K";
                case MILLION: return "M";
                case BILLION: return "B";
                case TRILLION: return "T";
                default: return "";
            }
        }

        @Override
        public String groupSeparator() {
            return ",";
        }

        @Override
        public String decimalSeparator() {
            return ".";
        }
    };

    private MoneyFormat() {
    }

    public static Tier tierOf(long amount) {
        long a = Math.abs(amount);
        if (a >= Tier.TRILLION.divisor()) {
            return Tier.TRILLION;
        }
        if (a >= Tier.BILLION.divisor()) {
            return Tier.BILLION;
        }
        if (a >= Tier.MILLION.divisor()) {
            return Tier.MILLION;
        }
        if (a >= Tier.THOUSAND.divisor()) {
            return Tier.THOUSAND;
        }
        return Tier.ONE;
    }

    public static String shortForm(long amount) {
        return shortForm(amount, DEFAULT);
    }

    /**
     * Compact form: {@code 532}, {@code 1.2K}, {@code 12K}, {@code 3.5M}, {@code 35M}.
     *
     * <p>The decimal place appears only when the scaled value is below 10 — that is, on
     * the first of each tier's three digits. It earns its width there, where {@code 1.2K}
     * and {@code 9.9K} are eight times apart and {@code 1K} and {@code 9K} would both
     * round to nothing useful. Once the scaled value reaches two digits the decimal is
     * noise: {@code 12.3K} and {@code 12K} differ by 2% in a number the player is reading
     * at a glance, and the shorter one fits a narrow HUD and a price cell.
     *
     * <p>Truncates rather than rounds, so the displayed figure never overstates what a
     * player can afford. Showing {@code 1.0K} for 999 would be a lie they act on.
     */
    public static String shortForm(long amount, Labels labels) {
        Tier tier = tierOf(amount);
        if (tier == Tier.ONE) {
            return Long.toString(amount);
        }

        boolean negative = amount < 0L;
        long magnitude = Math.abs(amount);
        long divisor = tier.divisor();
        long whole = magnitude / divisor;

        StringBuilder sb = new StringBuilder(12);
        if (negative) {
            sb.append('-');
        }
        sb.append(whole);
        if (whole < 10L) {
            long tenths = (magnitude % divisor) * 10L / divisor;
            sb.append(labels.decimalSeparator()).append(tenths);
        }
        sb.append(labels.suffix(tier));
        return sb.toString();
    }

    public static String fullForm(long amount) {
        return fullForm(amount, DEFAULT);
    }

    /** Exact value with digit grouping: {@code 3,500,000}. */
    public static String fullForm(long amount, Labels labels) {
        String digits = Long.toString(Math.abs(amount));
        String separator = labels.groupSeparator();

        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        if (amount < 0L) {
            sb.append('-');
        }
        int lead = digits.length() % 3;
        if (lead == 0) {
            lead = 3;
        }
        sb.append(digits, 0, lead);
        for (int i = lead; i < digits.length(); i += 3) {
            sb.append(separator).append(digits, i, i + 3);
        }
        return sb.toString();
    }

    /**
     * Short form with the exact value in parentheses, as {@code /shop balance} specifies.
     * Omits the parenthetical when the two forms would read the same.
     */
    public static String balanceForm(long amount, Labels labels) {
        String shortText = shortForm(amount, labels);
        String fullText = fullForm(amount, labels);
        return shortText.equals(fullText) ? shortText : shortText + " (" + fullText + ")";
    }
}
