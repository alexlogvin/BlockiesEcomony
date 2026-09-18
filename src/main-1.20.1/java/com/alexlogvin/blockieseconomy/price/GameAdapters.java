package com.alexlogvin.blockieseconomy.price;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.price.IngredientView;
import com.alexlogvin.blockieseconomy.core.price.RecipeView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * {@link GameAdapter} for Minecraft 1.20.1.
 *
 * <p>Here recipes carry their own id and advancements carry their own parent pointer, both
 * of which moved elsewhere in later versions. The 1.21.1 twin of this class lives in
 * {@code src/main-1.21.1}.
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
            for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
                ItemStack result = recipe.getResultItem(server.registryAccess());
                if (result == null || result.isEmpty()) {
                    continue;
                }
                views.add(new RecipeViewImpl(
                        recipe.getId().toString(),
                        BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),
                        BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),
                        result.getCount(),
                        adaptIngredients(recipe.getIngredients())));
            }
            return views;
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

            for (Advancement advancement : server.getAdvancements().getAllAdvancements()) {
                // An advancement with no display is technical, not an achievement. Vanilla
                // uses these for the recipe-unlock system: 1277 of 1399 entries on 1.21.1
                // are minecraft:recipes/*, and paying for them meant picking up one log
                // unlocked three plank recipes and paid three prizes.
                if (advancement.getDisplay() == null) {
                    technical++;
                    continue;
                }

                // getAllAdvancements returns nodes in no fixed order, so depth is computed
                // by walking up rather than assumed from iteration order.
                int depth = 0;
                Advancement node = advancement;
                while (node.getParent() != null) {
                    node = node.getParent();
                    depth++;
                }
                result.add(new AdvancementInfo(
                        advancement.getId().toString(), node.getId().toString(), depth));
            }

            BlockiesEconomy.LOGGER.debug(
                    "Ignored {} technical advancements with no display (recipe unlocks).",
                    technical);
            return result;
        }

        @Override
        public boolean hasNonDefaultComponents(ItemStack stack) {
            // 1.20.1 predates data components; NBT serves the same purpose. Any tag at all
            // means the stack is not a plain item: enchantments, custom names, and the
            // dangerous case of a shulker box carrying BlockEntityTag contents.
            return stack.hasTag() && !stack.getTag().isEmpty();
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
            if (ingredient.isEmpty()) {
                // Empty slots are padding in a shaped recipe, not a missing ingredient.
                continue;
            }
            List<String> candidates = new ArrayList<String>();
            ItemStack[] stacks = ingredient.getItems();
            for (int s = 0; s < stacks.length; s++) {
                candidates.add(BuiltInRegistries.ITEM.getKey(stacks[s].getItem()).toString());
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

    /** Unused here, but kept so both version variants expose the same helper surface. */
    static Deque<Object> newDeque() {
        return new ArrayDeque<Object>();
    }
}
