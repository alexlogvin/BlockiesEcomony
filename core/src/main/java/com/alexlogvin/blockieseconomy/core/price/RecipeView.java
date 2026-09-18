package com.alexlogvin.blockieseconomy.core.price;

import java.util.List;

/** A recipe reduced to what pricing needs: inputs, one output, and a type. */
public interface RecipeView {

    /** Recipe id, used in logs and by {@code /shop debug price}. */
    String id();

    /**
     * Recipe type id, e.g. {@code minecraft:crafting} or {@code minecraft:smelting}.
     * Selects the markup applied to the ingredient total.
     */
    String type();

    String outputItem();

    /** Output stack size. A recipe yielding 4 planks divides the log cost by 4. */
    int outputCount();

    List<IngredientView> ingredients();
}
