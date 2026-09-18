package com.alexlogvin.blockieseconomy.core.price;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The solved result: every item that has a price, plus what was excluded and why.
 *
 * <p>Items absent from {@link #entries()} are absent from the shop. That is the design:
 * an item nobody priced and no recipe reaches is not something the shop should invent a
 * number for.
 */
public final class PriceTable {

    private final Map<String, PriceEntry> entries;
    private final Set<String> blacklisted;
    private final List<String> skippedRecipes;
    private final int passes;
    private final boolean converged;

    PriceTable(Map<String, PriceEntry> entries, Set<String> blacklisted,
               List<String> skippedRecipes, int passes, boolean converged) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<String, PriceEntry>(entries));
        this.blacklisted = Collections.unmodifiableSet(new LinkedHashSet<String>(blacklisted));
        this.skippedRecipes = Collections.unmodifiableList(skippedRecipes);
        this.passes = passes;
        this.converged = converged;
    }

    public Map<String, PriceEntry> entries() {
        return entries;
    }

    public PriceEntry get(String itemId) {
        return entries.get(itemId);
    }

    public boolean has(String itemId) {
        return entries.containsKey(itemId);
    }

    public int size() {
        return entries.size();
    }

    /** Items explicitly excluded by an empty price in config. Never bought or sold. */
    public Set<String> blacklisted() {
        return blacklisted;
    }

    /**
     * Recipes the solver could not use: special/dynamic ones with no fixed ingredients
     * (armour dyeing, firework crafting, map cloning, tipped arrows, repair) or an empty
     * result. Logged so that a missing shop item is explainable rather than mysterious.
     */
    public List<String> skippedRecipes() {
        return skippedRecipes;
    }

    /** How many relaxation passes the solver needed. */
    public int passes() {
        return passes;
    }

    /**
     * False if the solver hit its pass cap with prices still moving. Indicates a
     * pathological recipe graph; the prices are usable but may not be minimal.
     */
    public boolean converged() {
        return converged;
    }
}
