package com.alexlogvin.blockieseconomy.core.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexlogvin.blockieseconomy.core.money.Money;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The rounding rule and the arbitrage defences that depend on it. */
class PricingRulesTest {

    private static final String CRAFTING = "minecraft:crafting";

    // ---- rounding --------------------------------------------------------------

    @Test
    @DisplayName("buying rounds up and selling rounds down, always")
    void buyRoundsUpSellRoundsDown() {
        // 10.5 Blockies exactly.
        long micros = Money.toMicros(10L) + Money.MICRO_SCALE / 2L;
        PriceEntry entry = new PriceEntry("minecraft:test", micros, PriceSource.DERIVED, null);

        assertEquals(11L, entry.buyPrice());              // 10.5 -> 11
        assertEquals(7L, entry.sellPrice(0.75d));         // 7.875 -> 7
    }

    @Test
    @DisplayName("the spread never inverts, even on the cheapest possible item")
    void spreadNeverInverts() {
        // Every price from a fraction of a Blockie up to 50; buy must always exceed sell.
        for (long micros = 1L; micros <= Money.toMicros(50L); micros += 7_777L) {
            PriceEntry entry = new PriceEntry("x", micros, PriceSource.DERIVED, null);
            long buy = entry.buyPrice();
            long sell = entry.sellPrice(0.75d);
            assertTrue(sell < buy,
                    "sell " + sell + " must stay below buy " + buy + " at " + micros + " micros");
        }
    }

    @Test
    @DisplayName("a 1-Blockie item cannot be sold back for 1")
    void cheapestItemCannotBeFlipped() {
        PriceEntry entry = new PriceEntry("minecraft:stick", Money.toMicros(1L),
                PriceSource.DERIVED, null);
        assertEquals(1L, entry.buyPrice());
        assertEquals(0L, entry.sellPrice(0.75d));
    }

    // ---- the multiplier ceiling ------------------------------------------------

    @Test
    @DisplayName("the ceiling is 1 over the sell multiplier")
    void ceilingFormula() {
        assertEquals(1.0d / 0.75d, MultiplierTable.ceilingFor(0.75d), 1e-9);
        assertEquals(2.0d, MultiplierTable.ceilingFor(0.5d), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> MultiplierTable.ceilingFor(0.0d));
        assertThrows(IllegalArgumentException.class, () -> MultiplierTable.ceilingFor(1.5d));
    }

    @Test
    @DisplayName("the brief's 1.5 smelting multiplier is an exploit and gets clamped")
    void clampsTheOriginalBriefsExploit() {
        // Buy raw iron at 60, smelt into an ingot priced 60*1.5=90, sell for 67.50.
        // That is 7.50 profit per cycle, forever.
        Map<String, Double> configured = new HashMap<String, Double>();
        configured.put("minecraft:smelting", Double.valueOf(1.5d));

        MultiplierTable table = MultiplierTable.of(configured, 1.3d, 0.75d);

        assertEquals(MultiplierTable.ceilingFor(0.75d), table.forType("minecraft:smelting"), 1e-9);
        assertTrue(table.clampedEntries().containsKey("minecraft:smelting"),
                "the clamp must be reported so an admin sees it");
        assertEquals(1.5d, table.clampedEntries().get("minecraft:smelting").doubleValue(), 1e-9);
    }

    @Test
    @DisplayName("the configured defaults are inside the ceiling and left untouched")
    void defaultsAreSafe() {
        Map<String, Double> configured = new HashMap<String, Double>();
        configured.put(CRAFTING, Double.valueOf(MultiplierTable.NO_MARKUP));
        configured.put("minecraft:stonecutting", Double.valueOf(MultiplierTable.NO_MARKUP));

        MultiplierTable table = MultiplierTable.of(
                configured, MultiplierTable.DEFAULT_MULTIPLIER, 0.75d);

        assertTrue(table.clampedEntries().isEmpty(), "sane config must not be clamped");
        assertEquals(1.0d, table.forType(CRAFTING), 1e-9);
        assertEquals(1.3d, table.forType("minecraft:smelting"), 1e-9);   // unlisted -> default
        assertEquals(1.3d, table.forType("create:mixing"), 1e-9);        // modded -> default
    }

    @Test
    @DisplayName("at the default multiplier a buy-craft-sell cycle loses money")
    void defaultMultiplierLosesMoneyOnACycle() {
        double cycle = MultiplierTable.DEFAULT_MULTIPLIER * 0.75d;
        assertTrue(cycle < 1.0d, "1.3 * 0.75 = " + cycle + " must be below 1");
        assertEquals(0.975d, cycle, 1e-9);
    }

    // ---- the validator ---------------------------------------------------------

    @Test
    @DisplayName("a sane table has no profitable recipe")
    void sanTableHasNoViolations() {
        Map<String, Double> configured = new HashMap<String, Double>();
        configured.put(CRAFTING, Double.valueOf(1.0d));

        List<RecipeView> recipes = Arrays.<RecipeView>asList(
                SimpleRecipe.of("planks", CRAFTING, "minecraft:oak_planks", 4)
                        .input("minecraft:oak_log").build(),
                SimpleRecipe.of("stick", CRAFTING, "minecraft:stick", 4)
                        .input("minecraft:oak_planks", 2).build());

        PriceTable table = new PriceSolver()
                .multipliers(MultiplierTable.of(configured, 1.3d, 0.75d))
                .seed("minecraft:oak_log", 40L, PriceSource.MANUAL)
                .recipes(recipes)
                .solve();

        assertTrue(ArbitrageValidator.validate(table, recipes, 0.75d).isEmpty());
    }

    @Test
    @DisplayName("an admin mispricing that the multiplier ceiling cannot catch is reported")
    void catchesManualMispricing() {
        // The ceiling only constrains DERIVED prices. Here the admin hand-priced the
        // output far above its inputs, which no multiplier check would ever see.
        Map<String, Double> configured = new HashMap<String, Double>();
        configured.put(CRAFTING, Double.valueOf(1.0d));

        List<RecipeView> recipes = Collections.<RecipeView>singletonList(
                SimpleRecipe.of("minecraft:iron_block", CRAFTING, "minecraft:iron_block", 1)
                        .input("minecraft:iron_ingot", 9).build());

        PriceTable table = new PriceSolver()
                .multipliers(MultiplierTable.of(configured, 1.3d, 0.75d))
                .seed("minecraft:iron_ingot", 10L, PriceSource.MANUAL)
                .seed("minecraft:iron_block", 500L, PriceSource.MANUAL)
                .recipes(recipes)
                .solve();

        List<ArbitrageValidator.Violation> violations =
                ArbitrageValidator.validate(table, recipes, 0.75d);

        assertEquals(1, violations.size());
        ArbitrageValidator.Violation v = violations.get(0);
        assertEquals("minecraft:iron_block", v.outputItem());
        assertEquals(90L, v.inputCost());     // 9 ingots at 10
        assertEquals(375L, v.outputRevenue()); // 500 * 0.75
        assertEquals(285L, v.profit());
    }

    @Test
    @DisplayName("violations are reported worst first")
    void violationsSortedByProfit() {
        Map<String, Double> configured = new HashMap<String, Double>();
        configured.put(CRAFTING, Double.valueOf(1.0d));

        List<RecipeView> recipes = Arrays.<RecipeView>asList(
                SimpleRecipe.of("small", CRAFTING, "small_out", 1).input("cheap", 1).build(),
                SimpleRecipe.of("large", CRAFTING, "large_out", 1).input("cheap", 1).build());

        PriceTable table = new PriceSolver()
                .multipliers(MultiplierTable.of(configured, 1.3d, 0.75d))
                .seed("cheap", 1L, PriceSource.MANUAL)
                .seed("small_out", 100L, PriceSource.MANUAL)
                .seed("large_out", 10_000L, PriceSource.MANUAL)
                .recipes(recipes)
                .solve();

        List<ArbitrageValidator.Violation> violations =
                ArbitrageValidator.validate(table, recipes, 0.75d);

        assertEquals(2, violations.size());
        assertEquals("large", violations.get(0).recipeId());
        assertTrue(violations.get(0).profit() > violations.get(1).profit());
    }

    @Test
    @DisplayName("a recipe with an unpriced ingredient is not a violation")
    void unbuyableRecipeIsNotAViolation() {
        List<RecipeView> recipes = Collections.<RecipeView>singletonList(
                SimpleRecipe.of("mob_drop", CRAFTING, "output", 1)
                        .input("minecraft:dragon_egg").build());

        PriceTable table = new PriceSolver()
                .seed("output", 10_000L, PriceSource.MANUAL)
                .recipes(recipes)
                .solve();

        // The ingredient cannot be bought at all, so the loop cannot be run.
        assertTrue(ArbitrageValidator.validate(table, recipes, 0.75d).isEmpty());
    }

    @Test
    @DisplayName("the full vanilla-shaped chain is arbitrage-free end to end")
    void vanillaChainIsClean() {
        Map<String, Double> configured = new HashMap<String, Double>();
        configured.put(CRAFTING, Double.valueOf(1.0d));
        configured.put("minecraft:stonecutting", Double.valueOf(1.0d));

        List<RecipeView> recipes = Arrays.<RecipeView>asList(
                SimpleRecipe.of("planks", CRAFTING, "minecraft:oak_planks", 4)
                        .input("minecraft:oak_log").build(),
                SimpleRecipe.of("stick", CRAFTING, "minecraft:stick", 4)
                        .input("minecraft:oak_planks", 2).build(),
                SimpleRecipe.of("ingot", "minecraft:smelting", "minecraft:iron_ingot", 1)
                        .input("minecraft:raw_iron").build(),
                SimpleRecipe.of("nuggets", CRAFTING, "minecraft:iron_nugget", 9)
                        .input("minecraft:iron_ingot").build(),
                SimpleRecipe.of("from_nuggets", CRAFTING, "minecraft:iron_ingot", 1)
                        .input("minecraft:iron_nugget", 9).build(),
                SimpleRecipe.of("block", CRAFTING, "minecraft:iron_block", 1)
                        .input("minecraft:iron_ingot", 9).build(),
                SimpleRecipe.of("unblock", CRAFTING, "minecraft:iron_ingot", 9)
                        .input("minecraft:iron_block").build(),
                SimpleRecipe.of("pickaxe", CRAFTING, "minecraft:iron_pickaxe", 1)
                        .input("minecraft:iron_ingot", 3).input("minecraft:stick", 2).build());

        PriceTable table = new PriceSolver()
                .multipliers(MultiplierTable.of(configured, 1.3d, 0.75d))
                .seed("minecraft:oak_log", 40L, PriceSource.MANUAL)
                .seed("minecraft:raw_iron", 60L, PriceSource.MANUAL)
                .recipes(recipes)
                .solve();

        assertTrue(table.converged());

        List<ArbitrageValidator.Violation> violations =
                ArbitrageValidator.validate(table, recipes, 0.75d);
        assertTrue(violations.isEmpty(), "found money loops: " + violations);

        // Spot-check the anchors the design fixes.
        assertEquals(40L, table.get("minecraft:oak_log").buyPrice());
        assertEquals(10L, table.get("minecraft:oak_planks").buyPrice());
        assertEquals(5L, table.get("minecraft:stick").buyPrice());
        assertEquals(78L, table.get("minecraft:iron_ingot").buyPrice());

        // Block/unblock is the classic dupe loop: 9 ingots in, 9 ingots out.
        assertFalse(violations.stream().anyMatch(v -> "unblock".equals(v.recipeId())));
    }
}
