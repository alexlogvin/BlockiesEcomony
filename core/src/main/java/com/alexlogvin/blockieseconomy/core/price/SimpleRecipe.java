package com.alexlogvin.blockieseconomy.core.price;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A plain {@link RecipeView} built in code.
 *
 * <p>Used by tests and by the datapack price source, which describes recipes as data
 * rather than adapting live Minecraft objects.
 */
public final class SimpleRecipe implements RecipeView {

    private final String id;
    private final String type;
    private final String outputItem;
    private final int outputCount;
    private final List<IngredientView> ingredients;

    private SimpleRecipe(String id, String type, String outputItem, int outputCount,
                         List<IngredientView> ingredients) {
        this.id = id;
        this.type = type;
        this.outputItem = outputItem;
        this.outputCount = outputCount;
        this.ingredients = Collections.unmodifiableList(ingredients);
    }

    public static Builder of(String id, String type, String outputItem, int outputCount) {
        return new Builder(id, type, outputItem, outputCount);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String outputItem() {
        return outputItem;
    }

    @Override
    public int outputCount() {
        return outputCount;
    }

    @Override
    public List<IngredientView> ingredients() {
        return ingredients;
    }

    @Override
    public String toString() {
        return id + " -> " + outputCount + "x " + outputItem;
    }

    /** Fluent builder; ingredient order is irrelevant to pricing. */
    public static final class Builder {
        private final String id;
        private final String type;
        private final String outputItem;
        private final int outputCount;
        private final List<IngredientView> ingredients = new ArrayList<IngredientView>();

        private Builder(String id, String type, String outputItem, int outputCount) {
            this.id = id;
            this.type = type;
            this.outputItem = outputItem;
            this.outputCount = outputCount;
        }

        /** Adds a slot consuming {@code count} of one specific item. */
        public Builder input(String itemId, int count) {
            ingredients.add(new SimpleIngredient(Collections.singletonList(itemId), count));
            return this;
        }

        public Builder input(String itemId) {
            return input(itemId, 1);
        }

        /** Adds a slot any of {@code itemIds} can fill, as a tag-based ingredient does. */
        public Builder anyOf(int count, String... itemIds) {
            ingredients.add(new SimpleIngredient(Arrays.asList(itemIds), count));
            return this;
        }

        /** Adds a slot with no valid candidates, as special/dynamic recipes report. */
        public Builder emptySlot() {
            ingredients.add(new SimpleIngredient(Collections.<String>emptyList(), 1));
            return this;
        }

        public SimpleRecipe build() {
            return new SimpleRecipe(id, type, outputItem, outputCount, ingredients);
        }
    }

    private static final class SimpleIngredient implements IngredientView {
        private final List<String> candidates;
        private final int count;

        SimpleIngredient(List<String> candidates, int count) {
            this.candidates = Collections.unmodifiableList(new ArrayList<String>(candidates));
            this.count = count;
        }

        @Override
        public List<String> candidates() {
            return candidates;
        }

        @Override
        public int count() {
            return count;
        }
    }
}
