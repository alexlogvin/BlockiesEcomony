package com.alexlogvin.blockieseconomy.core.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceSolverTest {

    private static final String CRAFTING = "minecraft:crafting";
    private static final String SMELTING = "minecraft:smelting";

    private static MultiplierTable noMarkup() {
        Map<String, Double> m = new HashMap<String, Double>();
        m.put(CRAFTING, Double.valueOf(1.0d));
        return MultiplierTable.of(m, 1.3d, 0.75d);
    }

    @Test
    @DisplayName("the anchor chain holds: log 40 gives plank 10 gives stick 5")
    void anchorChain() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:oak_log", 40L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("planks", CRAFTING, "minecraft:oak_planks", 4)
                        .input("minecraft:oak_log").build())
                .recipe(SimpleRecipe.of("stick", CRAFTING, "minecraft:stick", 4)
                        .input("minecraft:oak_planks", 2).build())
                .solve();

        assertEquals(40L, table.get("minecraft:oak_log").buyPrice());
        assertEquals(10L, table.get("minecraft:oak_planks").buyPrice());
        assertEquals(5L, table.get("minecraft:stick").buyPrice());
    }

    @Test
    @DisplayName("a multi-output recipe divides the cost by the output count")
    void dividesByOutputCount() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:oak_log", 40L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("planks", CRAFTING, "minecraft:oak_planks", 4)
                        .input("minecraft:oak_log").build())
                .solve();

        // Without the division every plank would cost a whole log.
        assertEquals(10L, table.get("minecraft:oak_planks").buyPrice());
    }

    @Test
    @DisplayName("the cheapest of several recipes wins, so the expensive path cannot be gamed")
    void cheapestRecipeWins() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("cheap_input", 10L, PriceSource.MANUAL)
                .seed("costly_input", 500L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("expensive", CRAFTING, "target", 1)
                        .input("costly_input").build())
                .recipe(SimpleRecipe.of("cheap", CRAFTING, "target", 1)
                        .input("cheap_input").build())
                .solve();

        assertEquals(10L, table.get("target").buyPrice());
        assertEquals("cheap", table.get("target").recipeId());
    }

    @Test
    @DisplayName("a tag-style slot prices at its cheapest member")
    void cheapestIngredientAlternativeWins() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:oak_log", 40L, PriceSource.MANUAL)
                .seed("minecraft:spruce_log", 55L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("planks", CRAFTING, "planks", 4)
                        .anyOf(1, "minecraft:oak_log", "minecraft:spruce_log").build())
                .solve();

        assertEquals(10L, table.get("planks").buyPrice()); // 40/4, not 55/4
    }

    @Test
    @DisplayName("the ingot to nugget cycle converges instead of looping forever")
    void resolvesCycles() {
        // 1 ingot -> 9 nuggets, and 9 nuggets -> 1 ingot. A naive walk never terminates.
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:raw_iron", 60L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("smelt", SMELTING, "minecraft:iron_ingot", 1)
                        .input("minecraft:raw_iron").build())
                .recipe(SimpleRecipe.of("to_nuggets", CRAFTING, "minecraft:iron_nugget", 9)
                        .input("minecraft:iron_ingot").build())
                .recipe(SimpleRecipe.of("to_ingot", CRAFTING, "minecraft:iron_ingot", 1)
                        .input("minecraft:iron_nugget", 9).build())
                .solve();

        assertTrue(table.converged(), "solver should reach a fixed point");
        assertEquals(78L, table.get("minecraft:iron_ingot").buyPrice()); // 60 * 1.3
        assertEquals(9L, table.get("minecraft:iron_nugget").buyPrice()); // ceil(78/9)

        // Round-tripping through nuggets must not become cheaper than smelting.
        assertEquals("smelt", table.get("minecraft:iron_ingot").recipeId());
    }

    @Test
    @DisplayName("an admin-declared price is never overridden by a recipe")
    void seedsAreAuthoritative() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:dirt", 5L, PriceSource.MANUAL)
                .seed("minecraft:diamond", 2500L, PriceSource.MANUAL)
                // A silly recipe that would otherwise make diamonds cost 5.
                .recipe(SimpleRecipe.of("silly", CRAFTING, "minecraft:diamond", 1)
                        .input("minecraft:dirt").build())
                .solve();

        assertEquals(2500L, table.get("minecraft:diamond").buyPrice());
        assertEquals(PriceSource.MANUAL, table.get("minecraft:diamond").source());
    }

    @Test
    @DisplayName("blacklisted items are absent and cannot be used as an ingredient")
    void blacklistExcludes() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:bedrock", 1L, PriceSource.MANUAL)
                .blacklist("minecraft:bedrock")
                .recipe(SimpleRecipe.of("cheat", CRAFTING, "minecraft:diamond", 1)
                        .input("minecraft:bedrock").build())
                .solve();

        assertNull(table.get("minecraft:bedrock"));
        // The craft cannot be priced, because its only input is unavailable.
        assertNull(table.get("minecraft:diamond"));
        assertTrue(table.blacklisted().contains("minecraft:bedrock"));
    }

    @Test
    @DisplayName("items reachable by no recipe and no seed stay out of the shop")
    void unpricedItemsAreAbsent() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:dirt", 5L, PriceSource.MANUAL)
                .solve();

        assertEquals(1, table.size());
        assertFalse(table.has("minecraft:elytra"));
    }

    @Test
    @DisplayName("special recipes with no fixed ingredients are skipped and reported")
    void skipsSpecialRecipes() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:arrow", 5L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("minecraft:tipped_arrow", CRAFTING,
                        "minecraft:tipped_arrow", 8).emptySlot().build())
                .recipe(SimpleRecipe.of("no_output", CRAFTING, "", 1)
                        .input("minecraft:arrow").build())
                .solve();

        assertEquals(2, table.skippedRecipes().size());
        assertTrue(table.skippedRecipes().contains("minecraft:tipped_arrow"));
        assertNull(table.get("minecraft:tipped_arrow"));
    }

    @Test
    @DisplayName("a recipe whose only input is its own output cannot pin its own price")
    void skipsSelfReferentialRecipes() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:diamond_sword", 100L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("repair", CRAFTING, "minecraft:diamond_sword", 1)
                        .input("minecraft:diamond_sword", 2).build())
                .solve();

        assertTrue(table.skippedRecipes().contains("repair"));
        assertEquals(100L, table.get("minecraft:diamond_sword").buyPrice());
    }

    @Test
    @DisplayName("the recipe-type multiplier is applied, and unlisted types take the default")
    void appliesTypeMultipliers() {
        Map<String, Double> configured = new HashMap<String, Double>();
        configured.put(CRAFTING, Double.valueOf(1.0d));
        MultiplierTable table = MultiplierTable.of(configured, 1.3d, 0.75d);

        PriceTable prices = new PriceSolver()
                .multipliers(table)
                .seed("raw", 100L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("craft", CRAFTING, "crafted", 1).input("raw").build())
                .recipe(SimpleRecipe.of("smelt", SMELTING, "smelted", 1).input("raw").build())
                .solve();

        assertEquals(100L, prices.get("crafted").buyPrice());  // 1.0
        assertEquals(130L, prices.get("smelted").buyPrice());  // default 1.3
    }

    @Test
    @DisplayName("derived entries record which recipe produced them, for /shop debug price")
    void recordsDerivation() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:oak_log", 40L, PriceSource.MANUAL)
                .recipe(SimpleRecipe.of("minecraft:oak_planks", CRAFTING,
                        "minecraft:oak_planks", 4).input("minecraft:oak_log").build())
                .solve();

        PriceEntry planks = table.get("minecraft:oak_planks");
        assertEquals(PriceSource.DERIVED, planks.source());
        assertEquals("minecraft:oak_planks", planks.recipeId());

        PriceEntry log = table.get("minecraft:oak_log");
        assertEquals(PriceSource.MANUAL, log.source());
        assertNull(log.recipeId());
    }

    @Test
    @DisplayName("an empty recipe set still yields the seeded prices")
    void handlesNoRecipes() {
        PriceTable table = new PriceSolver()
                .multipliers(noMarkup())
                .seed("minecraft:dirt", 5L, PriceSource.MANUAL)
                .recipes(Collections.<RecipeView>emptyList())
                .solve();

        assertEquals(1, table.size());
        assertTrue(table.converged());
    }
}
