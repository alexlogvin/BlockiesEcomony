package com.alexlogvin.blockieseconomy.core.price;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-recipe-type markups, with the arbitrage ceiling enforced.
 *
 * <p>Deriving a craft's price as {@code ingredients * multiplier} opens a money loop if
 * the multiplier is too high: buy the ingredients for {@code C}, craft, sell for
 * {@code C * multiplier * sellMultiplier}. That profits whenever
 *
 * <pre>multiplier &gt; 1 / sellMultiplier</pre>
 *
 * At the default {@code sellMultiplier = 0.75} the ceiling is 1.333. Anything above it
 * is clamped here, at load time, with the violation reported so an admin can see what
 * happened rather than discovering it as runaway inflation weeks later.
 */
public final class MultiplierTable {

    /** Recipe types that consume nothing beyond their ingredients get no markup. */
    public static final double NO_MARKUP = 1.0d;

    /** Applied to any type not listed, including every modded type. */
    public static final double DEFAULT_MULTIPLIER = 1.3d;

    private final Map<String, Double> byType;
    private final double defaultMultiplier;
    private final double ceiling;
    private final Map<String, Double> clamped;

    private MultiplierTable(Map<String, Double> byType, double defaultMultiplier,
                            double ceiling, Map<String, Double> clamped) {
        this.byType = Collections.unmodifiableMap(byType);
        this.defaultMultiplier = defaultMultiplier;
        this.ceiling = ceiling;
        this.clamped = Collections.unmodifiableMap(clamped);
    }

    /** The highest markup that cannot be arbitraged at the given sell multiplier. */
    public static double ceilingFor(double sellMultiplier) {
        if (sellMultiplier <= 0.0d || sellMultiplier > 1.0d) {
            throw new IllegalArgumentException(
                    "sell multiplier must be in (0, 1]: " + sellMultiplier);
        }
        return 1.0d / sellMultiplier;
    }

    /**
     * Builds a table, clamping any multiplier that would allow buy-craft-sell profit.
     *
     * @param configured        recipe type id to multiplier, as read from server.toml
     * @param defaultMultiplier applied to unlisted types
     * @param sellMultiplier    the configured sell spread, which sets the ceiling
     */
    public static MultiplierTable of(Map<String, Double> configured,
                                     double defaultMultiplier,
                                     double sellMultiplier) {
        double ceiling = ceilingFor(sellMultiplier);
        Map<String, Double> safe = new LinkedHashMap<String, Double>();
        Map<String, Double> clamped = new LinkedHashMap<String, Double>();

        for (Map.Entry<String, Double> e : configured.entrySet()) {
            double requested = e.getValue().doubleValue();
            double applied = clamp(requested, ceiling);
            safe.put(e.getKey(), Double.valueOf(applied));
            if (applied != requested) {
                clamped.put(e.getKey(), Double.valueOf(requested));
            }
        }

        double safeDefault = clamp(defaultMultiplier, ceiling);
        if (safeDefault != defaultMultiplier) {
            clamped.put("default_recipe_multiplier", Double.valueOf(defaultMultiplier));
        }

        return new MultiplierTable(safe, safeDefault, ceiling, clamped);
    }

    private static double clamp(double requested, double ceiling) {
        if (requested < 0.0d || Double.isNaN(requested)) {
            return NO_MARKUP;
        }
        return requested > ceiling ? ceiling : requested;
    }

    public double forType(String recipeType) {
        Double v = byType.get(recipeType);
        return v == null ? defaultMultiplier : v.doubleValue();
    }

    public double defaultMultiplier() {
        return defaultMultiplier;
    }

    public double ceiling() {
        return ceiling;
    }

    /**
     * Entries that were reduced to the ceiling, mapped to the value originally asked for.
     * Empty when the config is sound. The caller logs these as warnings.
     */
    public Map<String, Double> clampedEntries() {
        return clamped;
    }
}
