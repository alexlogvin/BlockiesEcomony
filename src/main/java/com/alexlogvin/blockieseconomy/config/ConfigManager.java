package com.alexlogvin.blockieseconomy.config;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.core.config.ClientConfig;
import com.alexlogvin.blockieseconomy.core.config.ServerConfig;
import com.alexlogvin.blockieseconomy.core.price.AdvancementPrizeCalculator;
import com.alexlogvin.blockieseconomy.core.price.MultiplierTable;
import com.alexlogvin.blockieseconomy.core.price.PriceSource;
import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import com.alexlogvin.blockieseconomy.core.toml.TomlEntry;
import com.alexlogvin.blockieseconomy.core.toml.TomlTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads every config file and holds the result.
 *
 * <p>One instance per server. {@code /shop reload} replaces its contents by calling
 * {@link #load()} again; nothing caches a reference to the individual config objects
 * across a reload.
 */
public final class ConfigManager {

    private ServerConfig server = new ServerConfig();
    private ClientConfig client = new ClientConfig();
    private AdvancementPrizeCalculator prizes = AdvancementPrizeCalculator.withDefaults();
    private List<PriceDeclarations> declarations = Collections.emptyList();
    private Set<String> whitelist = Collections.emptySet();

    public ServerConfig server() {
        return server;
    }

    public ClientConfig client() {
        return client;
    }

    public AdvancementPrizeCalculator prizes() {
        return prizes;
    }

    /**
     * Price declarations in precedence order, highest authority first.
     *
     * <p>{@code prices.toml} (the admin) outranks {@code prices.d/*.toml} (packs), which
     * outrank datapacks and the Java API, which outrank tag rules and recipe derivation.
     */
    public List<PriceDeclarations> declarations() {
        return declarations;
    }

    /**
     * Items allowed in the shop, or empty for "no whitelist applies".
     *
     * <p>Empty meaning "everything" rather than "nothing" is the useful default: a server
     * that has not opted into a whitelist should not find an empty shop.
     */
    public Set<String> whitelist() {
        return whitelist;
    }

    public boolean hasWhitelist() {
        return !whitelist.isEmpty();
    }

    /** Reads every file, writing defaults for any that are missing. */
    public void load() {
        TomlFiles.ensureDirectories();
        writeDefaultsIfAbsent();

        server = ServerConfig.fromToml(TomlFiles.read(ConfigPaths.server()));
        client = ClientConfig.fromToml(TomlFiles.read(ConfigPaths.client()));

        warnAboutClampedMultipliers();

        prizes = readPrizes(TomlFiles.read(ConfigPaths.advancements()));
        declarations = readDeclarations();
        whitelist = readWhitelist();

        int declared = 0;
        for (int i = 0; i < declarations.size(); i++) {
            declared += declarations.get(i).size();
        }
        BlockiesEconomy.LOGGER.info(
                "Loaded configuration: {} price declarations from {} sources{}",
                declared, declarations.size(),
                hasWhitelist() ? ", whitelist of " + whitelist.size() + " items" : "");
    }

    /** Saves the client config after an in-game change, such as pinning a favourite. */
    public void saveClient() {
        TomlFiles.write(ConfigPaths.client(), client.toToml());
    }

    /**
     * Saves the server config after an in-game change from the config screen.
     *
     * <p><b>This rewrites {@code server.toml} in the mod's canonical shape.</b> Settings
     * survive, including recipe multipliers the mod does not know about, but an admin's
     * own comments and formatting do not — the file is regenerated, not patched. So the
     * config screen calls this only when a value actually changed, and only ever on a
     * single-player world, where the file belongs to the player sitting at the screen.
     * On a real server, {@code server.toml} is the admin's document and the commands are
     * the way in.
     */
    public void saveServer() {
        TomlFiles.write(ConfigPaths.server(), server.toToml());
    }

    private void writeDefaultsIfAbsent() {
        TomlFiles.writeIfAbsent(ConfigPaths.server(), new ServerConfig().toToml());
        TomlFiles.writeIfAbsent(ConfigPaths.client(), new ClientConfig().toToml());
        TomlFiles.writeIfAbsent(ConfigPaths.prices(), DefaultPrices.document());
        TomlFiles.writeIfAbsent(ConfigPaths.advancements(), defaultAdvancementsDocument());
        TomlFiles.writeIfAbsent(ConfigPaths.whitelist(), defaultWhitelistDocument());
    }

    /**
     * Reports any recipe multiplier that had to be clamped.
     *
     * <p>Loudly, because the setting looks harmless and its consequence — players minting
     * money by crafting — is invisible until the economy is already ruined.
     */
    private void warnAboutClampedMultipliers() {
        MultiplierTable table = server.multiplierTable();
        Map<String, Double> clamped = table.clampedEntries();
        if (clamped.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> e : clamped.entrySet()) {
            BlockiesEconomy.LOGGER.warn(
                    "Recipe multiplier {} = {} in server.toml would let players buy "
                            + "ingredients, craft, and sell at a profit forever. Clamped to {}. "
                            + "The ceiling is 1 / sell_multiplier = {}.",
                    e.getKey(), e.getValue(), table.forType(e.getKey()), table.ceiling());
        }
    }

    private AdvancementPrizeCalculator readPrizes(TomlDocument doc) {
        AdvancementPrizeCalculator.Builder builder = AdvancementPrizeCalculator.builder()
                .base(server.advancementBase())
                .exponent(server.advancementExponent());

        TomlTable trees = doc.findTable("trees");
        if (trees != null) {
            for (TomlEntry entry : trees.entries()) {
                try {
                    builder.treeBase(entry.key(), entry.value().asLong());
                } catch (RuntimeException e) {
                    BlockiesEconomy.LOGGER.warn(
                            "Ignoring advancement tree base for {}: expected a whole number.",
                            entry.key());
                }
            }
        }

        TomlTable overrides = doc.findTable("overrides");
        if (overrides != null) {
            for (TomlEntry entry : overrides.entries()) {
                try {
                    builder.override(entry.key(), entry.value().asLong());
                } catch (RuntimeException e) {
                    BlockiesEconomy.LOGGER.warn(
                            "Ignoring advancement override for {}: expected a whole number.",
                            entry.key());
                }
            }
        }

        return builder.build();
    }

    private List<PriceDeclarations> readDeclarations() {
        List<PriceDeclarations> result = new ArrayList<PriceDeclarations>();

        result.add(PriceDeclarations.fromToml(
                TomlFiles.read(ConfigPaths.prices()), PriceSource.MANUAL, ConfigPaths.PRICES_FILE));

        List<Path> dropIns = TomlFiles.listDropIns(ConfigPaths.priceDropIns());
        for (int i = 0; i < dropIns.size(); i++) {
            Path file = dropIns.get(i);
            PriceDeclarations declared = PriceDeclarations.fromToml(
                    TomlFiles.read(file), PriceSource.PACK,
                    ConfigPaths.PRICES_DROPIN_DIR + "/" + file.getFileName());
            if (!declared.isEmpty()) {
                BlockiesEconomy.LOGGER.info("Loaded {} price declarations from {}",
                        declared.size(), declared.origin());
                result.add(declared);
            }
        }

        return Collections.unmodifiableList(result);
    }

    private Set<String> readWhitelist() {
        TomlDocument doc = TomlFiles.read(ConfigPaths.whitelist());
        TomlValue items = doc.value("", "items");
        if (items == null || items.kind() != TomlValue.Kind.ARRAY) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<String>();
        List<TomlValue> values = items.asArray();
        for (int i = 0; i < values.size(); i++) {
            String id = values.get(i).asString();
            if (!id.isEmpty()) {
                result.add(id);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static TomlDocument defaultAdvancementsDocument() {
        TomlDocument doc = new TomlDocument();
        doc.headerComments().addAll(java.util.Arrays.asList(
                "Blockies Economy - advancement prizes.",
                "",
                "Prizes are normally computed as base_prize * depth_exponent ^ depth, using the",
                "values in server.toml. This file is for exceptions.",
                "",
                "The computed value for every advancement is written to generated/prices.toml",
                "after each rebuild, so you can see what a node currently pays before",
                "overriding it."));

        TomlTable trees = doc.table("trees");
        trees.comments().addAll(java.util.Arrays.asList(
                "Per-tree base prize, keyed by the tree's root advancement.",
                "Useful for making one progression line more rewarding than another,",
                "or for switching a grindy modded tree off with a base of 0.",
                "",
                "Examples:",
                "  \"minecraft:nether/root\" = 500",
                "  \"minecraft:end/root\" = 2000",
                "  \"somemod:tedious/root\" = 0"));

        TomlTable overrides = doc.table("overrides");
        overrides.comments().addAll(java.util.Arrays.asList(
                "Fixed prize for one advancement, ignoring its depth entirely.",
                "",
                "Examples:",
                "  \"minecraft:end/kill_dragon\" = 25000",
                "  \"minecraft:nether/all_effects\" = 0"));

        return doc;
    }

    private static TomlDocument defaultWhitelistDocument() {
        TomlDocument doc = new TomlDocument();
        doc.headerComments().addAll(java.util.Arrays.asList(
                "Blockies Economy - shop whitelist.",
                "",
                "Leave this empty and NO whitelist applies: every priced item is available.",
                "Add any item and the shop is restricted to exactly what is listed here.",
                "",
                "This is the opposite of blacklisting, which you do in prices.toml by giving",
                "an item an empty price:  \"minecraft:bedrock\" = \"\"",
                "",
                "Example:",
                "  items = [\"minecraft:dirt\", \"minecraft:oak_log\", \"minecraft:iron_ingot\"]"));

        doc.root().put("items", TomlValue.ofArray(Collections.<TomlValue>emptyList()));
        return doc;
    }
}
