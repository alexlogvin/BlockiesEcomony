package com.alexlogvin.blockieseconomy.price;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.config.PriceDeclarations;
import com.alexlogvin.blockieseconomy.core.price.PriceSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/**
 * Prices shipped inside datapacks and mod jars.
 *
 * <p>A mod author drops {@code data/<yourmod>/blockies_economy/prices.json} into their jar
 * and their items are priced, with no compile-time dependency on this mod and no harm done
 * if it is not installed. A server admin can override any of it from a datapack of their
 * own, or from {@code prices.toml}, which outranks everything here.
 *
 * <p>Read straight from the server's resource manager at build time rather than through a
 * registered reload listener. The rebuild already runs after a datapack reload has
 * finished, so a listener would add a per-loader registration and buy nothing — and
 * listener ordering against the recipe manager is exactly the kind of thing that fails
 * silently.
 *
 * <p>Format:
 *
 * <pre>{@code
 * {
 *   "prices": {
 *     "mymod:copper_gear": 180,
 *     "#c:raw_materials": 60,
 *     "mymod:creative_generator": ""
 *   }
 * }
 * }</pre>
 *
 * <p>A number is a price in whole Blockies, a leading {@code #} makes the key an item tag,
 * and {@code ""} removes the item from the shop. The bare object without the {@code prices}
 * wrapper is accepted too.
 */
public final class DatapackPrices {

    private static final String DIRECTORY = BlockiesEconomy.MOD_ID;
    private static final String FILE_SUFFIX = "prices.json";
    private static final String PRICES_KEY = "prices";

    private DatapackPrices() {
    }

    /**
     * Reads every {@code prices.json} the server's packs provide.
     *
     * <p>One {@link PriceDeclarations} per namespace, ordered by namespace so the result is
     * the same on every run. Within the datapack tier there is no meaningful authority
     * ranking — two mods pricing the same item is a conflict either way — so a stable
     * arbitrary order beats one that shifts with pack load order.
     */
    public static List<PriceDeclarations> load(MinecraftServer server) {
        List<PriceDeclarations> result = new ArrayList<PriceDeclarations>();
        Map<ResourceLocation, Resource> found;
        try {
            found = server.getResourceManager().listResources(DIRECTORY,
                    location -> location.getPath().endsWith(FILE_SUFFIX));
        } catch (RuntimeException e) {
            BlockiesEconomy.LOGGER.warn("Could not scan datapacks for prices: {}", e.toString());
            return result;
        }

        Map<ResourceLocation, Resource> sorted =
                new TreeMap<ResourceLocation, Resource>(found);
        for (Map.Entry<ResourceLocation, Resource> entry : sorted.entrySet()) {
            PriceDeclarations declarations = read(entry.getKey(), entry.getValue());
            if (declarations != null && !declarations.isEmpty()) {
                BlockiesEconomy.LOGGER.info("Loaded {} price declarations from datapack {}",
                        declarations.size(), declarations.origin());
                result.add(declarations);
            }
        }
        return result;
    }

    private static PriceDeclarations read(ResourceLocation location, Resource resource) {
        String origin = location.toString();
        JsonObject root;
        try (BufferedReader reader = resource.openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                BlockiesEconomy.LOGGER.warn("{} is not a JSON object; ignoring it.", origin);
                return null;
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            BlockiesEconomy.LOGGER.warn("Could not read {}: {}", origin, e.toString());
            return null;
        }

        JsonObject prices = root.has(PRICES_KEY) && root.get(PRICES_KEY).isJsonObject()
                ? root.getAsJsonObject(PRICES_KEY)
                : root;

        PriceDeclarations declarations = new PriceDeclarations(PriceSource.DATAPACK, origin);
        for (Map.Entry<String, JsonElement> entry : prices.entrySet()) {
            apply(declarations, entry.getKey(), entry.getValue(), origin);
        }
        return declarations;
    }

    private static void apply(PriceDeclarations target, String key, JsonElement value,
                              String origin) {
        if (PRICES_KEY.equals(key) && value.isJsonObject()) {
            return;
        }
        if (!value.isJsonPrimitive()) {
            BlockiesEconomy.LOGGER.warn(
                    "Ignoring {} in {}: expected a number, or \"\" to blacklist the item.",
                    key, origin);
            return;
        }

        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString() && primitive.getAsString().isEmpty()) {
            target.blacklist(key.startsWith(PriceDeclarations.TAG_PREFIX)
                    ? key.substring(1) : key);
            return;
        }

        long price;
        try {
            price = primitive.getAsLong();
        } catch (NumberFormatException e) {
            BlockiesEconomy.LOGGER.warn(
                    "Ignoring {} in {}: a price must be a whole number, found {}.",
                    key, origin, primitive);
            return;
        }
        if (price < 0L) {
            BlockiesEconomy.LOGGER.warn("Ignoring {} in {}: a price cannot be negative ({}).",
                    key, origin, Long.valueOf(price));
            return;
        }

        if (key.startsWith(PriceDeclarations.TAG_PREFIX)) {
            target.putTag(key.substring(1), price);
        } else {
            target.putItem(key, price);
        }
    }
}
