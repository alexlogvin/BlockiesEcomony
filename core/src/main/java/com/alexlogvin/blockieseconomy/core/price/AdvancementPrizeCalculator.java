package com.alexlogvin.blockieseconomy.core.price;

import com.alexlogvin.blockieseconomy.core.money.Money;
import java.util.HashMap;
import java.util.Map;

/**
 * Works out what an advancement pays.
 *
 * <p>The prize grows geometrically with depth in its tree:
 *
 * <pre>prize = base * exponent ^ depth</pre>
 *
 * <p>so a root advancement pays the base and each step deeper pays half again as much by
 * default. That keeps endgame advancements meaningful without hand-authoring a number for
 * every node, including the hundreds that mods add.
 *
 * <p>Both figures are configurable globally and per tree, and any single advancement can
 * be given a flat override. Precedence, highest first: per-advancement override, per-tree
 * settings, global settings.
 */
public final class AdvancementPrizeCalculator {

    /** What a root advancement pays when nothing overrides it. */
    public static final long DEFAULT_BASE = 100L;

    /** Growth per level of depth. */
    public static final double DEFAULT_EXPONENT = 1.5d;

    private final long globalBase;
    private final double globalExponent;
    private final Map<String, Long> baseByTree;
    private final Map<String, Double> exponentByTree;
    private final Map<String, Long> overrides;

    private AdvancementPrizeCalculator(long globalBase, double globalExponent,
                                       Map<String, Long> baseByTree,
                                       Map<String, Double> exponentByTree,
                                       Map<String, Long> overrides) {
        this.globalBase = globalBase;
        this.globalExponent = globalExponent;
        this.baseByTree = baseByTree;
        this.exponentByTree = exponentByTree;
        this.overrides = overrides;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AdvancementPrizeCalculator withDefaults() {
        return builder().build();
    }

    /**
     * Prize in whole Blockies.
     *
     * @param advancementId the advancement's id, used to look up an override
     * @param rootId        the id of its tree's root, used for per-tree settings
     * @param depth         0 for a root, 1 for its children, and so on
     */
    public long prizeFor(String advancementId, String rootId, int depth) {
        Long override = overrides.get(advancementId);
        if (override != null) {
            return Money.clamp(override.longValue());
        }

        if (depth < 0) {
            throw new IllegalArgumentException("depth cannot be negative: " + depth);
        }

        long base = baseFor(rootId);
        if (base == 0L) {
            return 0L;
        }

        double exponent = exponentFor(rootId);
        double value = base * Math.pow(exponent, depth);

        // A deep modded tree can overflow a double into something silly; clamping keeps
        // one pathological advancement from handing out an unspendable fortune.
        if (Double.isNaN(value) || Double.isInfinite(value) || value >= Money.MAX_BALANCE) {
            return Money.MAX_BALANCE;
        }
        return (long) Math.round(value);
    }

    public long baseFor(String rootId) {
        Long perTree = baseByTree.get(rootId);
        return perTree == null ? globalBase : perTree.longValue();
    }

    public double exponentFor(String rootId) {
        Double perTree = exponentByTree.get(rootId);
        return perTree == null ? globalExponent : perTree.doubleValue();
    }

    /** True if this advancement's prize was set explicitly rather than computed. */
    public boolean hasOverride(String advancementId) {
        return overrides.containsKey(advancementId);
    }

    public static final class Builder {
        private long globalBase = DEFAULT_BASE;
        private double globalExponent = DEFAULT_EXPONENT;
        private final Map<String, Long> baseByTree = new HashMap<String, Long>();
        private final Map<String, Double> exponentByTree = new HashMap<String, Double>();
        private final Map<String, Long> overrides = new HashMap<String, Long>();

        public Builder base(long base) {
            if (base < 0L) {
                throw new IllegalArgumentException("base prize cannot be negative");
            }
            this.globalBase = base;
            return this;
        }

        public Builder exponent(double exponent) {
            if (exponent < 0.0d || Double.isNaN(exponent) || Double.isInfinite(exponent)) {
                throw new IllegalArgumentException("exponent must be finite and non-negative");
            }
            this.globalExponent = exponent;
            return this;
        }

        /** Overrides the base prize for one advancement tree. */
        public Builder treeBase(String rootId, long base) {
            baseByTree.put(rootId, Long.valueOf(base));
            return this;
        }

        /** Overrides the growth rate for one advancement tree. */
        public Builder treeExponent(String rootId, double exponent) {
            exponentByTree.put(rootId, Double.valueOf(exponent));
            return this;
        }

        /** Fixes one advancement's prize, ignoring depth entirely. */
        public Builder override(String advancementId, long prize) {
            overrides.put(advancementId, Long.valueOf(prize));
            return this;
        }

        public AdvancementPrizeCalculator build() {
            return new AdvancementPrizeCalculator(globalBase, globalExponent,
                    new HashMap<String, Long>(baseByTree),
                    new HashMap<String, Double>(exponentByTree),
                    new HashMap<String, Long>(overrides));
        }
    }
}
