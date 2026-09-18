package com.alexlogvin.blockieseconomy.core.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexlogvin.blockieseconomy.core.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdvancementPrizeCalculatorTest {

    private static final String STORY = "minecraft:story/root";

    @Test
    @DisplayName("prize is base times 1.5 to the power of depth")
    void geometricGrowth() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.withDefaults();

        assertEquals(100L, calc.prizeFor("minecraft:story/root", STORY, 0));
        assertEquals(150L, calc.prizeFor("minecraft:story/mine_stone", STORY, 1));
        assertEquals(225L, calc.prizeFor("minecraft:story/upgrade_tools", STORY, 2));
        assertEquals(338L, calc.prizeFor("minecraft:story/smelt_iron", STORY, 3));
    }

    @Test
    @DisplayName("deep endgame advancements pay substantially more than early ones")
    void deepAdvancementsPayMore() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.withDefaults();
        long shallow = calc.prizeFor("a", STORY, 1);
        long deep = calc.prizeFor("b", STORY, 10);
        assertTrue(deep > shallow * 20L, "depth 10 paid " + deep + " vs depth 1 " + shallow);
    }

    @Test
    @DisplayName("base and exponent are configurable globally")
    void configurableGlobals() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.builder()
                .base(50L)
                .exponent(2.0d)
                .build();

        assertEquals(50L, calc.prizeFor("a", STORY, 0));
        assertEquals(100L, calc.prizeFor("b", STORY, 1));
        assertEquals(200L, calc.prizeFor("c", STORY, 2));
    }

    @Test
    @DisplayName("a tree can override base and exponent independently of the global values")
    void perTreeSettings() {
        String nether = "minecraft:nether/root";
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.builder()
                .base(100L)
                .exponent(1.5d)
                .treeBase(nether, 500L)
                .treeExponent(nether, 2.0d)
                .build();

        assertEquals(100L, calc.prizeFor("a", STORY, 0));
        assertEquals(150L, calc.prizeFor("b", STORY, 1));

        assertEquals(500L, calc.prizeFor("c", nether, 0));
        assertEquals(1_000L, calc.prizeFor("d", nether, 1));

        assertEquals(500L, calc.baseFor(nether));
        assertEquals(100L, calc.baseFor(STORY));
    }

    @Test
    @DisplayName("a per-advancement override wins over everything and ignores depth")
    void perAdvancementOverride() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.builder()
                .base(100L)
                .override("minecraft:end/kill_dragon", 25_000L)
                .build();

        assertEquals(25_000L, calc.prizeFor("minecraft:end/kill_dragon", STORY, 0));
        assertEquals(25_000L, calc.prizeFor("minecraft:end/kill_dragon", STORY, 7));
        assertTrue(calc.hasOverride("minecraft:end/kill_dragon"));
        assertFalse(calc.hasOverride("minecraft:story/root"));
    }

    @Test
    @DisplayName("an override of zero disables a prize entirely")
    void zeroOverrideDisablesPrize() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.builder()
                .override("modid:grindy_advancement", 0L)
                .build();
        assertEquals(0L, calc.prizeFor("modid:grindy_advancement", STORY, 4));
    }

    @Test
    @DisplayName("a base of zero turns off payouts for that tree")
    void zeroBaseDisablesTree() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.builder()
                .treeBase("modid:spam_tree", 0L)
                .build();
        assertEquals(0L, calc.prizeFor("modid:a", "modid:spam_tree", 5));
        assertEquals(100L, calc.prizeFor("minecraft:a", STORY, 0));
    }

    @Test
    @DisplayName("an absurdly deep modded tree clamps instead of overflowing")
    void clampsRunawayValues() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.withDefaults();
        assertEquals(Money.MAX_BALANCE, calc.prizeFor("deep", STORY, 500));
    }

    @Test
    @DisplayName("nonsensical configuration is rejected at build time")
    void rejectsBadConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementPrizeCalculator.builder().base(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementPrizeCalculator.builder().exponent(-1.0d));
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementPrizeCalculator.builder().exponent(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementPrizeCalculator.withDefaults().prizeFor("a", STORY, -1));
    }

    @Test
    @DisplayName("an exponent of 1 makes every advancement pay the same")
    void flatExponent() {
        AdvancementPrizeCalculator calc = AdvancementPrizeCalculator.builder()
                .base(200L)
                .exponent(1.0d)
                .build();
        assertEquals(200L, calc.prizeFor("a", STORY, 0));
        assertEquals(200L, calc.prizeFor("b", STORY, 9));
    }
}
