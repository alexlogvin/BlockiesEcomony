package com.alexlogvin.blockieseconomy.platform;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;

/**
 * Everything that names a game object by id.
 *
 * <p>Ids are strings everywhere above this class — in the price solver, in the config
 * files, on the wire — and become the game’s own id type only here. That was a
 * convenience until 1.21.11 renamed {@code ResourceLocation} to {@code Identifier}, at
 * which point it became the reason ten shared classes did not have to fork.
 *
 * <p>The 1.21.11 form. ResourceLocation was renamed to Identifier, and its command argument
 * with it. Nothing else about either changed.
 */
public final class GameIds {

    private GameIds() {
    }

    /** The item with this id, or null if the id is malformed or the game does not know it. */
    public static Item item(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return null;
        }
        // getOptional rather than get: from 1.21.2 get returns a holder wrapped in an
        // Optional, and this way the call reads the same on every version.
        return BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
    }

    /** An item's id, in {@code namespace:path} form. */
    public static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /**
     * The command argument an item id is read with.
     *
     * <p>An id argument rather than an item argument: the item one parses a full item
     * stack, whose syntax changed with data components, and this mod only ever wants the
     * id. Returned as a wildcard because its type parameter is the game’s id type, which
     * is exactly what callers must not name.
     */
    public static ArgumentType<?> idArgument() {
        return IdentifierArgument.id();
    }

    /** The value of an {@link #idArgument()}, in {@code namespace:path} form. */
    public static String idArgument(CommandContext<CommandSourceStack> ctx, String name) {
        return IdentifierArgument.getId(ctx, name).toString();
    }

    /**
     * Every resource under {@code directory} whose path ends with {@code suffix}, keyed by
     * id and ordered by it.
     *
     * <p>Ordered so that two packs declaring the same thing resolve the same way on every
     * run; within one tier there is no meaningful authority ranking, and a stable arbitrary
     * order beats one that shifts with pack load order.
     */
    public static SortedMap<String, Resource> listResources(MinecraftServer server,
                                                            String directory, String suffix) {
        Map<Identifier, Resource> found = server.getResourceManager()
                .listResources(directory, location -> location.getPath().endsWith(suffix));

        SortedMap<String, Resource> byId = new TreeMap<String, Resource>();
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            byId.put(entry.getKey().toString(), entry.getValue());
        }
        return byId;
    }
}
