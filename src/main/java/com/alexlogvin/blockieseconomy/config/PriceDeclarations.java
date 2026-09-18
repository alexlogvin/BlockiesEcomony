package com.alexlogvin.blockieseconomy.config;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.price.PriceSource;
import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import com.alexlogvin.blockieseconomy.core.toml.TomlEntry;
import com.alexlogvin.blockieseconomy.core.toml.TomlTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Declared prices gathered from one source, before the precedence merge.
 *
 * <p>A declaration is one of three things:
 * <ul>
 *   <li>a price for an item id — {@code "minecraft:dirt" = 5}</li>
 *   <li>a price for an item <em>tag</em> — {@code "#c:ingots" = 90}, which prices every
 *       member at once and is the main lever for supporting mods nobody has hand-priced</li>
 *   <li>a blacklisting — {@code "minecraft:bedrock" = ""}, which removes the item from the
 *       shop entirely rather than giving it a price of zero</li>
 * </ul>
 *
 * <p>The empty string is deliberately distinct from an absent key. Absent means "work it
 * out from recipes"; empty means "never trade this".
 */
public final class PriceDeclarations {

    /** Prefix that marks a key as a tag rather than an item id. */
    public static final String TAG_PREFIX = "#";

    private final PriceSource source;
    private final String origin;
    private final Map<String, Long> itemPrices = new LinkedHashMap<String, Long>();
    private final Map<String, Long> tagPrices = new LinkedHashMap<String, Long>();
    private final Set<String> blacklist = new LinkedHashSet<String>();

    public PriceDeclarations(PriceSource source, String origin) {
        this.source = source;
        this.origin = origin;
    }

    public PriceSource source() {
        return source;
    }

    /** Where these came from, for logs and {@code /shop debug price}. */
    public String origin() {
        return origin;
    }

    public Map<String, Long> itemPrices() {
        return Collections.unmodifiableMap(itemPrices);
    }

    /** Tag id (without the leading hash) to price. */
    public Map<String, Long> tagPrices() {
        return Collections.unmodifiableMap(tagPrices);
    }

    public Set<String> blacklist() {
        return Collections.unmodifiableSet(blacklist);
    }

    public boolean isEmpty() {
        return itemPrices.isEmpty() && tagPrices.isEmpty() && blacklist.isEmpty();
    }

    public int size() {
        return itemPrices.size() + tagPrices.size() + blacklist.size();
    }

    public void putItem(String itemId, long price) {
        itemPrices.put(itemId, Long.valueOf(price));
    }

    public void putTag(String tagId, long price) {
        tagPrices.put(tagId, Long.valueOf(price));
    }

    public void blacklist(String itemId) {
        blacklist.add(itemId);
    }

    /**
     * Reads declarations from a TOML document.
     *
     * <p>Keys are accepted at the document root and inside any table, so an admin can
     * group them however they like — by mod, by tier, or not at all. Table names carry no
     * meaning; they exist purely to keep a long file navigable.
     */
    public static PriceDeclarations fromToml(TomlDocument doc, PriceSource source, String origin) {
        PriceDeclarations declarations = new PriceDeclarations(source, origin);

        for (TomlTable table : doc.tables()) {
            for (TomlEntry entry : table.entries()) {
                read(declarations, entry, origin);
            }
        }
        return declarations;
    }

    private static void read(PriceDeclarations target, TomlEntry entry, String origin) {
        String key = entry.key();
        TomlValue value = entry.value();

        if (value.isEmptyString()) {
            target.blacklist(key.startsWith(TAG_PREFIX) ? key.substring(1) : key);
            return;
        }

        long price;
        try {
            price = value.asLong();
        } catch (RuntimeException e) {
            BlockiesEconomy.LOGGER.warn(
                    "Ignoring {} in {}: a price must be a whole number, or \"\" to blacklist "
                            + "the item. Found {}.",
                    key, origin, value);
            return;
        }

        if (price < 0L) {
            BlockiesEconomy.LOGGER.warn(
                    "Ignoring {} in {}: a price cannot be negative ({}).", key, origin, price);
            return;
        }

        if (key.startsWith(TAG_PREFIX)) {
            target.putTag(key.substring(1), price);
        } else {
            target.putItem(key, price);
        }
    }
}
