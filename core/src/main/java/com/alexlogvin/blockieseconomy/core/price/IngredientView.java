package com.alexlogvin.blockieseconomy.core.price;

import java.util.List;

/**
 * One input slot of a recipe, reduced to what pricing needs.
 *
 * <p>Deliberately free of Minecraft types: the platform layer adapts whatever
 * {@code Ingredient} looks like on a given version. That keeps the solver testable
 * without a game runtime and survives the 1.21.2 change where ingredients became
 * HolderSet-backed.
 */
public interface IngredientView {

    /**
     * Item ids that would satisfy this slot. A tag-based ingredient reports every
     * member; the solver prices it at the cheapest one, since that is what a player
     * would actually use.
     */
    List<String> candidates();

    /** How many items this slot consumes. */
    int count();
}
