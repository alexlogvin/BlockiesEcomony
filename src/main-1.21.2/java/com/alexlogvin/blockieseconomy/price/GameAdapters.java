package com.alexlogvin.blockieseconomy.price;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.price.IngredientView;
import com.alexlogvin.blockieseconomy.core.price.RecipeView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * {@link GameAdapter} for the Minecraft 1.21.2 era.
 *
 * <p>Where the recipe rewrite lands. A recipe no longer reports its own result or
 * ingredients: the result comes from a display entry resolved against the world, and the
 * ingredients from a placement info. Its id is a {@code ResourceKey} rather than a
 * {@code ResourceLocation}, and an ingredient hands over item holders rather than sample
 * stacks.
 *
 * <p>Everything else is the 1.20.5 class. Note that emptiness of an ingredient is read off
 * the resolved item list rather than {@code Ingredient.isEmpty()}, which 1.21.2 and 1.21.3
 * do not have and 1.21.4 does — that single choice is what lets these three versions
 * share one jar.
 */
public final class GameAdapters {

    private GameAdapters() {
    }

    public static GameAdapter create() {
        return new Impl();
    }

    private static final class Impl implements GameAdapter {

        @Override
        public List<RecipeView> recipes(MinecraftServer server) {
            List<RecipeView> views = new ArrayList<RecipeView>();

            // Resolving a result is no longer a property of the recipe alone: a slot
            // display is resolved against the world’s registries and fuel values. One
            // context serves every recipe, so it is built once here.
            ContextMap context = SlotDisplayContext.fromLevel(server.overworld());

            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                Recipe<?> recipe = holder.value();

                PlacementInfo placement = recipe.placementInfo();
                if (placement.isImpossibleToPlace()) {
                    // What used to be an empty ingredient list. Armour dyeing, firework
                    // crafting, book cloning and the rest have no fixed inputs to price
                    // from, and the solver has always skipped them.
                    continue;
                }

                ItemStack result = resultOf(recipe, context);
                if (result.isEmpty()) {
                    continue;
                }

                views.add(new RecipeViewImpl(
                        // The id is a ResourceKey from 1.21.2, not a ResourceLocation.
                        holder.id().location().toString(),
                        BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),
                        BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),
                        result.getCount(),
                        adaptIngredients(placement.ingredients())));
            }
            return views;
        }

        /**
         * The stack a recipe produces, or empty if it does not name one.
         *
         * <p>{@code getResultItem} is gone from 1.21.2. A recipe now exposes display
         * entries instead, each carrying a slot display that has to be resolved; the first
         * one that resolves to a real stack is the result this mod prices. Most recipes
         * have exactly one.
         */
        private static ItemStack resultOf(Recipe<?> recipe, ContextMap context) {
            for (RecipeDisplay display : recipe.display()) {
                ItemStack stack = display.result().resolveForFirstStack(context);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public List<String> allItemIds() {
            List<String> ids = new ArrayList<String>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                ids.add(id.toString());
            }
            return ids;
        }

        @Override
        public Set<String> itemsInTag(MinecraftServer server, String tagId) {
            ResourceLocation location = ResourceLocation.tryParse(tagId);
            if (location == null) {
                return Collections.emptySet();
            }
            TagKey<Item> key = TagKey.create(Registries.ITEM, location);
            Set<String> ids = new LinkedHashSet<String>();
            BuiltInRegistries.ITEM.getTagOrEmpty(key).forEach(holder ->
                    ids.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
            return ids;
        }

        @Override
        public boolean itemExists(String itemId) {
            ResourceLocation location = ResourceLocation.tryParse(itemId);
            return location != null && BuiltInRegistries.ITEM.containsKey(location);
        }

        @Override
        public List<AdvancementInfo> advancements(MinecraftServer server) {
            List<AdvancementInfo> result = new ArrayList<AdvancementInfo>();
            int technical = 0;

            for (AdvancementNode node : server.getAdvancements().tree().nodes()) {
                AdvancementHolder holder = node.holder();

                // An advancement with no display is technical, not an achievement. Vanilla
                // uses these for the recipe-unlock system: 1277 of 1399 entries here are
                // minecraft:recipes/*, and paying for them meant picking up one log
                // unlocked three plank recipes and paid three prizes.
                if (holder.value().display().isEmpty()) {
                    technical++;
                    continue;
                }

                int depth = 0;
                AdvancementNode current = node;
                while (current.parent() != null) {
                    current = current.parent();
                    depth++;
                }
                result.add(new AdvancementInfo(
                        holder.id().toString(), current.holder().id().toString(), depth));
            }

            BlockiesEconomy.LOGGER.debug(
                    "Ignored {} technical advancements with no display (recipe unlocks).",
                    technical);
            return result;
        }

        @Override
        public boolean hasNonDefaultComponents(ItemStack stack) {
            // Components replaced NBT in 1.20.5. A stack whose components differ from its
            // item's prototype carries enchantments, a custom name, potion contents, or
            // container contents - the shulker-box-full-of-diamonds case.
            return !stack.getComponentsPatch().isEmpty();
        }

        @Override
        public int remainingDurability(ItemStack stack) {
            return stack.getMaxDamage() - stack.getDamageValue();
        }

        @Override
        public int maxDurability(ItemStack stack) {
            return stack.isDamageableItem() ? stack.getMaxDamage() : 0;
        }
    }

    static List<IngredientView> adaptIngredients(List<Ingredient> ingredients) {
        List<IngredientView> views = new ArrayList<IngredientView>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);

            // An ingredient hands over item holders rather than sample stacks from 1.21.2.
            List<String> candidates = new ArrayList<String>();
            ingredient.items().forEach(item ->
                    candidates.add(BuiltInRegistries.ITEM.getKey(item.value()).toString()));

            // Emptiness is read off the resolved list rather than from
            // Ingredient.isEmpty(), which does not exist on 1.21.2 or 1.21.3 — it is the
            // one thing that would otherwise split this era into two. The two say the same
            // thing: an ingredient with no items matches nothing.
            if (candidates.isEmpty()) {
                continue;
            }
            views.add(new IngredientViewImpl(candidates, 1));
        }
        return views;
    }

    static final class RecipeViewImpl implements RecipeView {
        private final String id;
        private final String type;
        private final String outputItem;
        private final int outputCount;
        private final List<IngredientView> ingredients;

        RecipeViewImpl(String id, String type, String outputItem, int outputCount,
                       List<IngredientView> ingredients) {
            this.id = id;
            this.type = type;
            this.outputItem = outputItem;
            this.outputCount = outputCount;
            this.ingredients = ingredients;
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
    }

    static final class IngredientViewImpl implements IngredientView {
        private final List<String> candidates;
        private final int count;

        IngredientViewImpl(List<String> candidates, int count) {
            this.candidates = candidates;
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
