package com.alexlogvin.blockieseconomy.core.price;

import com.alexlogvin.blockieseconomy.core.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives item prices from the recipe graph.
 *
 * <p>Seeded prices — anything an admin, pack, datapack, API caller or tag rule declared —
 * are fixed. Everything else is worked out from recipes:
 *
 * <pre>price(output) = sum(count * cheapest(candidates)) * typeMultiplier / outputCount</pre>
 *
 * <p>Four rules shape the result, each chosen to keep players from arbitraging it:
 * <ul>
 *   <li><b>Cheapest recipe wins.</b> An item craftable several ways costs the least of them,
 *       so nobody profits by using the expensive path.</li>
 *   <li><b>Cheapest ingredient alternative wins.</b> A tag-based slot prices at its cheapest
 *       member, which is what a player would actually feed it.</li>
 *   <li><b>Multi-output divides.</b> Four planks from one log means a plank is a quarter of
 *       a log, not a whole one.</li>
 *   <li><b>Cycles resolve by relaxation.</b> Ingot to nuggets and back is a loop that no
 *       single-pass walk can price. Prices start at infinity and only ever fall, so
 *       repeated passes converge to a fixed point.</li>
 * </ul>
 *
 * <p>The whole computation runs in micro-units and rounds only at the edge; see
 * {@link Money} for why that matters.
 */
public final class PriceSolver {

    /** Cap on relaxation passes. Convergence is normally far quicker; this is a stop. */
    public static final int DEFAULT_MAX_PASSES = 64;

    private static final long UNPRICED = Long.MAX_VALUE;

    private final Map<String, Long> seeds = new LinkedHashMap<String, Long>();
    private final Map<String, PriceSource> seedSources = new HashMap<String, PriceSource>();
    private final Set<String> blacklist = new LinkedHashSet<String>();
    private final List<RecipeView> recipes = new ArrayList<RecipeView>();
    private MultiplierTable multipliers =
            MultiplierTable.of(Collections.<String, Double>emptyMap(),
                    MultiplierTable.DEFAULT_MULTIPLIER, 0.75d);
    private int maxPasses = DEFAULT_MAX_PASSES;

    /** Declares a fixed price, in whole Blockies, that the solver must not override. */
    public PriceSolver seed(String itemId, long blockies, PriceSource source) {
        if (blockies < 0L) {
            throw new IllegalArgumentException("price cannot be negative: " + itemId);
        }
        seeds.put(itemId, Long.valueOf(Money.toMicros(blockies)));
        seedSources.put(itemId, source);
        return this;
    }

    /** Excludes an item entirely. Set by an empty price in config. */
    public PriceSolver blacklist(String itemId) {
        blacklist.add(itemId);
        return this;
    }

    public PriceSolver recipe(RecipeView recipe) {
        recipes.add(recipe);
        return this;
    }

    public PriceSolver recipes(List<? extends RecipeView> toAdd) {
        recipes.addAll(toAdd);
        return this;
    }

    public PriceSolver multipliers(MultiplierTable table) {
        this.multipliers = table;
        return this;
    }

    public PriceSolver maxPasses(int passes) {
        this.maxPasses = Math.max(1, passes);
        return this;
    }

    public PriceTable solve() {
        List<String> skipped = new ArrayList<String>();
        List<RecipeView> usable = new ArrayList<RecipeView>(recipes.size());

        for (int i = 0; i < recipes.size(); i++) {
            RecipeView r = recipes.get(i);
            if (isUnusable(r)) {
                skipped.add(r.id());
            } else {
                usable.add(r);
            }
        }

        Map<String, Long> price = new HashMap<String, Long>();
        Map<String, String> viaRecipe = new HashMap<String, String>();

        for (Map.Entry<String, Long> e : seeds.entrySet()) {
            if (!blacklist.contains(e.getKey())) {
                price.put(e.getKey(), e.getValue());
            }
        }

        int pass = 0;
        boolean changed = true;
        while (changed && pass < maxPasses) {
            changed = false;
            pass++;

            for (int i = 0; i < usable.size(); i++) {
                RecipeView r = usable.get(i);
                String output = r.outputItem();

                // A seeded price is authoritative; recipes never lower it.
                if (seeds.containsKey(output) || blacklist.contains(output)) {
                    continue;
                }

                long cost = costOf(r, price);
                if (cost == UNPRICED) {
                    continue;
                }

                long candidate = Money.scale(cost, multipliers.forType(r.type()));
                // Round the per-output share UP. Truncating here lets a cycle shave a
                // few micro-units each lap, so a round trip (ingot -> 9 nuggets -> ingot)
                // would undercut the direct recipe and win the min. Rounding up makes a
                // round trip cost at least as much as the path it came from.
                candidate = ceilDiv(candidate, Math.max(1, r.outputCount()));
                if (candidate < 1L) {
                    candidate = 1L;
                }

                Long existing = price.get(output);
                // Prices only ever fall, which is what makes a cyclic graph converge.
                if (existing == null || candidate < existing.longValue()) {
                    price.put(output, Long.valueOf(candidate));
                    viaRecipe.put(output, r.id());
                    changed = true;
                }
            }
        }

        Map<String, PriceEntry> entries = new LinkedHashMap<String, PriceEntry>();
        List<String> ids = new ArrayList<String>(price.keySet());
        Collections.sort(ids);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            PriceSource source = seedSources.get(id);
            if (source == null) {
                source = PriceSource.DERIVED;
            }
            entries.put(id, new PriceEntry(id, price.get(id).longValue(), source,
                    viaRecipe.get(id)));
        }

        return new PriceTable(entries, blacklist, skipped, pass, !changed);
    }

    private static long ceilDiv(long value, int divisor) {
        long q = value / divisor;
        return (value % divisor == 0L) ? q : q + 1L;
    }

    /**
     * Total ingredient cost in micro-units, or {@link #UNPRICED} if any slot has no
     * priced candidate yet.
     */
    private long costOf(RecipeView recipe, Map<String, Long> price) {
        long total = 0L;
        List<IngredientView> ingredients = recipe.ingredients();

        for (int i = 0; i < ingredients.size(); i++) {
            IngredientView ingredient = ingredients.get(i);
            long cheapest = UNPRICED;

            List<String> candidates = ingredient.candidates();
            for (int c = 0; c < candidates.size(); c++) {
                String candidate = candidates.get(c);
                if (blacklist.contains(candidate)) {
                    continue;
                }
                Long p = price.get(candidate);
                if (p != null && p.longValue() < cheapest) {
                    cheapest = p.longValue();
                }
            }

            if (cheapest == UNPRICED) {
                return UNPRICED;
            }

            int count = Math.max(1, ingredient.count());
            long slot = cheapest * count;
            // Guard the accumulator rather than trusting a pathological modded recipe.
            if (slot < 0L || total > Long.MAX_VALUE - slot) {
                return UNPRICED;
            }
            total += slot;
        }
        return total;
    }

    /**
     * True for recipes the solver cannot price: special/dynamic ones that declare no
     * fixed ingredients, and any with an empty or zero-count result.
     */
    private static boolean isUnusable(RecipeView recipe) {
        if (recipe.outputItem() == null || recipe.outputItem().isEmpty()) {
            return true;
        }
        if (recipe.outputCount() <= 0) {
            return true;
        }
        List<IngredientView> ingredients = recipe.ingredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return true;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            List<String> candidates = ingredients.get(i).candidates();
            if (candidates == null || candidates.isEmpty()) {
                return true;
            }
        }
        // A recipe whose output is also one of its inputs prices itself; skip it rather
        // than let it pin its own value. Ingot -> ingot "repair" recipes look like this.
        Set<String> inputs = new HashSet<String>();
        for (int i = 0; i < ingredients.size(); i++) {
            inputs.addAll(ingredients.get(i).candidates());
        }
        return inputs.size() == 1 && inputs.contains(recipe.outputItem());
    }
}
