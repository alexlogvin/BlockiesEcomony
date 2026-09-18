package com.alexlogvin.blockieseconomy.price;

import com.alexlogvin.blockieseconomy.core.price.RecipeView;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the game's registries in whatever shape the running version uses.
 *
 * <p>The implementation lives in {@code src/main-<mcversion>} — one class, same fully
 * qualified name, compiled per node. That is preferred over Stonecutter comment gates here
 * because the two versions differ structurally rather than line by line:
 *
 * <ul>
 *   <li>1.20.1 has {@code Recipe#getId()}; 1.20.2 lifted identity out into
 *       {@code RecipeHolder<T>}</li>
 *   <li>{@code getResultItem} takes a {@code RegistryAccess} on 1.20.1 and a
 *       {@code HolderLookup.Provider} on 1.21</li>
 *   <li>advancement hierarchy moved from {@code Advancement} itself into
 *       {@code AdvancementTree}/{@code AdvancementNode}</li>
 * </ul>
 *
 * <p>Everything returned here is expressed in version-neutral terms, so the solver and the
 * rest of the mod never see a version-specific type.
 */
public interface GameAdapter {

    /** One advancement, flattened to what prize calculation needs. */
    final class AdvancementInfo {
        private final String id;
        private final String rootId;
        private final int depth;

        public AdvancementInfo(String id, String rootId, int depth) {
            this.id = id;
            this.rootId = rootId;
            this.depth = depth;
        }

        public String id() {
            return id;
        }

        /** The id of this advancement's tree root; equal to {@link #id()} for a root. */
        public String rootId() {
            return rootId;
        }

        /** 0 for a root, 1 for its children, and so on. */
        public int depth() {
            return depth;
        }
    }

    /**
     * Every loaded recipe, reduced to {@link RecipeView}.
     *
     * <p>Recipes the solver cannot use are still returned; it reports them as skipped so a
     * missing shop item can be explained.
     */
    List<RecipeView> recipes(MinecraftServer server);

    /** Every registered item id, in registry order. */
    List<String> allItemIds();

    /** Item ids belonging to a tag, for tag pricing rules. Empty if the tag is unknown. */
    Set<String> itemsInTag(MinecraftServer server, String tagId);

    /** True if an item id exists in this version's registry. */
    boolean itemExists(String itemId);

    /** Every advancement, walked depth-first from its tree root. */
    List<AdvancementInfo> advancements(MinecraftServer server);

    /**
     * True if the stack carries non-default components and must not be traded.
     *
     * <p>Covers enchantments, potion contents, custom names, written books and — the one
     * that actually matters — containers with contents. A shulker box full of diamonds
     * shares an item id with an empty one, so without this check it would sell at the
     * empty-box price.
     */
    boolean hasNonDefaultComponents(ItemStack stack);

    /** Remaining durability and maximum, for pro-rating a damaged item's sell price. */
    int remainingDurability(ItemStack stack);

    int maxDurability(ItemStack stack);
}
