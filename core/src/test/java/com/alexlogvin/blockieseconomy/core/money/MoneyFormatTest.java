package com.alexlogvin.blockieseconomy.core.money;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alexlogvin.blockieseconomy.core.money.MoneyFormat.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyFormatTest {

    @Test
    @DisplayName("short form matches the spec examples")
    void shortFormExamples() {
        assertEquals("3.0K", MoneyFormat.shortForm(3_000L));
        assertEquals("1.2K", MoneyFormat.shortForm(1_200L));
        assertEquals("3.5M", MoneyFormat.shortForm(3_500_000L));
    }

    @Test
    @DisplayName("below a thousand renders exactly, with no decimal")
    void smallAmountsAreExact() {
        assertEquals("0", MoneyFormat.shortForm(0L));
        assertEquals("5", MoneyFormat.shortForm(5L));
        assertEquals("999", MoneyFormat.shortForm(999L));
        assertEquals("1.0K", MoneyFormat.shortForm(1_000L));
    }

    @Test
    @DisplayName("short form truncates, so it never overstates what a player can afford")
    void truncatesRatherThanRounds() {
        // Rounding would show 1.0K for 999, implying the player has a thousand.
        assertEquals("999", MoneyFormat.shortForm(999L));
        // 1999 is 1.999K; truncation gives 1.9K, never 2.0K.
        assertEquals("1.9K", MoneyFormat.shortForm(1_999L));
        assertEquals("9.9M", MoneyFormat.shortForm(9_999_999L));
    }

    @Test
    @DisplayName("the decimal place appears only while the scaled value is a single digit")
    void decimalOnlyBelowTen() {
        // Below 10 the decimal carries real information: 1.2K and 9.9K are eight times
        // apart, and dropping it would round both to a single misleading digit.
        assertEquals("9.9K", MoneyFormat.shortForm(9_999L));
        // At two digits it is noise — 12.3K and 12K differ by 2% in a number read at a
        // glance — and the shorter form fits the HUD and a price cell.
        assertEquals("10K", MoneyFormat.shortForm(10_000L));
        assertEquals("12K", MoneyFormat.shortForm(12_345L));
        assertEquals("999K", MoneyFormat.shortForm(999_999L));
        // The rule restarts at every tier, not once.
        assertEquals("9.9M", MoneyFormat.shortForm(9_999_999L));
        assertEquals("10M", MoneyFormat.shortForm(10_000_000L));
        assertEquals("999M", MoneyFormat.shortForm(999_999_999L));
        assertEquals("1.0B", MoneyFormat.shortForm(1_000_000_000L));
        assertEquals("-12K", MoneyFormat.shortForm(-12_345L));
    }

    @Test
    @DisplayName("tiers cover thousands through trillions")
    void tiers() {
        assertEquals(Tier.ONE, MoneyFormat.tierOf(999L));
        assertEquals(Tier.THOUSAND, MoneyFormat.tierOf(1_000L));
        assertEquals(Tier.MILLION, MoneyFormat.tierOf(1_000_000L));
        assertEquals(Tier.BILLION, MoneyFormat.tierOf(1_000_000_000L));
        assertEquals(Tier.TRILLION, MoneyFormat.tierOf(1_000_000_000_000L));
        assertEquals("2.5B", MoneyFormat.shortForm(2_500_000_000L));
        assertEquals("1.0T", MoneyFormat.shortForm(1_000_000_000_000L));
    }

    @Test
    @DisplayName("full form groups digits")
    void fullForm() {
        assertEquals("0", MoneyFormat.fullForm(0L));
        assertEquals("5", MoneyFormat.fullForm(5L));
        assertEquals("999", MoneyFormat.fullForm(999L));
        assertEquals("1,000", MoneyFormat.fullForm(1_000L));
        assertEquals("3,500,000", MoneyFormat.fullForm(3_500_000L));
        assertEquals("-1,234,567", MoneyFormat.fullForm(-1_234_567L));
    }

    @Test
    @DisplayName("balance form adds the exact value only when it differs")
    void balanceForm() {
        assertEquals("3.5M (3,500,000)", MoneyFormat.balanceForm(3_500_000L, MoneyFormat.DEFAULT));
        // Under a thousand both forms agree, so the parenthetical would be noise.
        assertEquals("532", MoneyFormat.balanceForm(532L, MoneyFormat.DEFAULT));
    }

    @Test
    @DisplayName("negative balances format sensibly")
    void negatives() {
        assertEquals("-1.2K", MoneyFormat.shortForm(-1_200L));
        assertEquals("-42", MoneyFormat.shortForm(-42L));
    }

    @Test
    @DisplayName("labels are swappable, so translations control suffixes and separators")
    void localisedLabels() {
        MoneyFormat.Labels ukrainian = new MoneyFormat.Labels() {
            @Override
            public String suffix(Tier tier) {
                switch (tier) {
                    case THOUSAND: return " тис.";
                    case MILLION: return " млн";
                    case BILLION: return " млрд";
                    case TRILLION: return " трлн";
                    default: return "";
                }
            }

            @Override
            public String groupSeparator() {
                return " ";
            }

            @Override
            public String decimalSeparator() {
                return ",";
            }
        };

        assertEquals("1,2 тис.", MoneyFormat.shortForm(1_200L, ukrainian));
        assertEquals("3,5 млн", MoneyFormat.shortForm(3_500_000L, ukrainian));
        assertEquals("3 500 000", MoneyFormat.fullForm(3_500_000L, ukrainian));
    }
}
