package com.alexlogvin.blockieseconomy.core.price;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks the solved table for money loops.
 *
 * <p>The multiplier ceiling in {@link MultiplierTable} prevents the obvious one, but it
 * is not a complete guarantee. A price that came from config rather than from the solver
 * is not bound by any multiplier, so an admin can hand-price an ingredient below what its
 * own output sells for. Integer rounding can do the same to very cheap items.
 *
 * <p>So after solving, every recipe is checked directly: selling the outputs must never
 * pay more than buying the inputs cost. Violations are reported with the offending
 * recipe id rather than silently corrected, because the fix is usually a config mistake
 * the admin needs to see.
 */
public final class ArbitrageValidator {

    /** One recipe that can be run at a profit. */
    public static final class Violation {
        private final String recipeId;
        private final String outputItem;
        private final long inputCost;
        private final long outputRevenue;

        Violation(String recipeId, String outputItem, long inputCost, long outputRevenue) {
            this.recipeId = recipeId;
            this.outputItem = outputItem;
            this.inputCost = inputCost;
            this.outputRevenue = outputRevenue;
        }

        public String recipeId() {
            return recipeId;
        }

        public String outputItem() {
            return outputItem;
        }

        /** Whole Blockies to buy every ingredient. */
        public long inputCost() {
            return inputCost;
        }

        /** Whole Blockies gained by selling the full output stack. */
        public long outputRevenue() {
            return outputRevenue;
        }

        public long profit() {
            return outputRevenue - inputCost;
        }

        @Override
        public String toString() {
            return recipeId + ": buying inputs costs " + inputCost
                    + " but selling " + outputItem + " pays " + outputRevenue
                    + " (profit " + profit() + ")";
        }
    }

    private ArbitrageValidator() {
    }

    /**
     * Returns every recipe that can be run for profit, worst first.
     *
     * @param table          the solved prices
     * @param recipes        the same recipes handed to the solver
     * @param sellMultiplier the configured sell spread
     */
    public static List<Violation> validate(PriceTable table,
                                           List<? extends RecipeView> recipes,
                                           double sellMultiplier) {
        List<Violation> violations = new ArrayList<Violation>();

        for (int i = 0; i < recipes.size(); i++) {
            RecipeView recipe = recipes.get(i);
            PriceEntry output = table.get(recipe.outputItem());
            if (output == null || recipe.outputCount() <= 0) {
                continue;
            }

            long inputCost = buyCostOfInputs(table, recipe);
            if (inputCost < 0L) {
                continue; // an unpriced ingredient means the craft is not purchasable
            }

            long revenue = output.sellPrice(sellMultiplier) * recipe.outputCount();
            if (revenue > inputCost) {
                violations.add(new Violation(recipe.id(), recipe.outputItem(), inputCost, revenue));
            }
        }

        Collections.sort(violations, (a, b) -> Long.compare(b.profit(), a.profit()));
        return violations;
    }

    /**
     * What a player pays to buy every ingredient, using the cheapest candidate per slot,
     * or -1 if any slot cannot be bought.
     */
    private static long buyCostOfInputs(PriceTable table, RecipeView recipe) {
        long total = 0L;
        List<IngredientView> ingredients = recipe.ingredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return -1L;
        }

        for (int i = 0; i < ingredients.size(); i++) {
            IngredientView ingredient = ingredients.get(i);
            long cheapest = -1L;
            List<String> candidates = ingredient.candidates();
            for (int c = 0; c < candidates.size(); c++) {
                PriceEntry entry = table.get(candidates.get(c));
                if (entry == null) {
                    continue;
                }
                long buy = entry.buyPrice();
                if (cheapest < 0L || buy < cheapest) {
                    cheapest = buy;
                }
            }
            if (cheapest < 0L) {
                return -1L;
            }
            total += cheapest * Math.max(1, ingredient.count());
        }
        return total;
    }
}
