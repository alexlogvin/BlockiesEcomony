package com.alexlogvin.blockieseconomy.core.price;

/**
 * Where a price came from, highest authority first.
 *
 * <p>The order is the precedence chain: a server admin always outranks a pack, which
 * outranks a mod author, which outranks anything the solver worked out for itself.
 */
public enum PriceSource {
    /** config/blockies_economy/prices.toml — the server admin has the final say. */
    MANUAL,
    /** config/blockies_economy/prices.d/*.toml — drop-in add-ons. */
    PACK,
    /** data/<namespace>/blockies_economy/prices.json shipped in a datapack or mod jar. */
    DATAPACK,
    /** Registered at runtime through the Java API. */
    API,
    /** Matched a tag rule such as "#c:ingots". */
    TAG,
    /** Computed from a recipe. */
    DERIVED;

    /** True if this source outranks {@code other}. */
    public boolean outranks(PriceSource other) {
        return ordinal() < other.ordinal();
    }
}
