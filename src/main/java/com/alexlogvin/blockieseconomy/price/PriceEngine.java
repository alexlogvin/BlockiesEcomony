package com.alexlogvin.blockieseconomy.price;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.config.ConfigManager;
import com.alexlogvin.blockieseconomy.config.PriceDeclarations;
import com.alexlogvin.blockieseconomy.core.price.ArbitrageValidator;
import com.alexlogvin.blockieseconomy.core.price.PriceEntry;
import com.alexlogvin.blockieseconomy.core.price.PriceSolver;
import com.alexlogvin.blockieseconomy.core.price.PriceSource;
import com.alexlogvin.blockieseconomy.core.price.PriceTable;
import com.alexlogvin.blockieseconomy.core.price.RecipeView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;

/**
 * Builds and holds the price table.
 *
 * <p>Solving runs off the server thread. A modpack with ten thousand items and a cyclic
 * recipe graph is not something to make the world-load thread wait for, and the result is
 * only needed once a player actually opens the shop. Until it finishes, {@link #isReady()}
 * is false and trades are refused rather than priced from a half-built table.
 */
public final class PriceEngine {

    /**
     * Bumped whenever the solver's output shape or semantics change, so a cached table
     * from an older version is discarded instead of being loaded as if it were current.
     */
    public static final int SCHEMA_VERSION = 1;

    private final ConfigManager config;
    private final GameAdapter adapter;

    private final AtomicBoolean solving = new AtomicBoolean(false);
    private volatile PriceTable table;
    private volatile Map<String, String> derivationNotes = Collections.emptyMap();
    private volatile List<ArbitrageValidator.Violation> violations = Collections.emptyList();

    public PriceEngine(ConfigManager config, GameAdapter adapter) {
        this.config = config;
        this.adapter = adapter;
    }

    /** False until the first solve completes. Trades must check this before pricing. */
    public boolean isReady() {
        return table != null;
    }

    public PriceTable table() {
        return table;
    }

    public PriceEntry price(String itemId) {
        PriceTable current = table;
        return current == null ? null : current.get(itemId);
    }

    /** Where a price came from, in a form suitable for {@code /shop debug price}. */
    public String derivation(String itemId) {
        return derivationNotes.get(itemId);
    }

    public List<ArbitrageValidator.Violation> violations() {
        return violations;
    }

    /**
     * Rebuilds the table on a background thread.
     *
     * <p>Recipes and tags are read on the calling (server) thread, because those registries
     * are not safe to touch concurrently; only the solving itself is handed off.
     */
    public void rebuildAsync(MinecraftServer server, Runnable onComplete) {
        if (!solving.compareAndSet(false, true)) {
            BlockiesEconomy.LOGGER.info("Price rebuild already in progress; ignoring request.");
            return;
        }

        final List<RecipeView> recipes;
        final Map<String, Long> seeds;
        final Map<String, PriceSource> seedSources;
        final Set<String> blacklist;
        try {
            recipes = adapter.recipes(server);
            MergedDeclarations merged = merge(server);
            seeds = merged.prices;
            seedSources = merged.sources;
            blacklist = merged.blacklist;
        } catch (RuntimeException e) {
            solving.set(false);
            BlockiesEconomy.LOGGER.error("Could not read recipes or prices: {}", e.toString(), e);
            return;
        }

        if (recipes.isEmpty()) {
            // Almost always means this ran before the datapack load finished. Worth saying
            // plainly: the symptom is an empty shop with no other error.
            BlockiesEconomy.LOGGER.warn(
                    "No recipes were loaded when prices were rebuilt. The shop will only "
                            + "contain items with a declared price.");
        }

        Thread worker = new Thread(() -> {
            try {
                solve(recipes, seeds, seedSources, blacklist);
            } catch (RuntimeException e) {
                BlockiesEconomy.LOGGER.error("Price solve failed: {}", e.toString(), e);
            } finally {
                solving.set(false);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }, "blockies-economy-price-solver");
        worker.setDaemon(true);
        worker.start();
    }

    private void solve(List<RecipeView> recipes, Map<String, Long> seeds,
                       Map<String, PriceSource> seedSources, Set<String> blacklist) {
        long started = System.nanoTime();

        PriceSolver solver = new PriceSolver()
                .multipliers(config.server().multiplierTable())
                .recipes(recipes);

        for (Map.Entry<String, Long> seed : seeds.entrySet()) {
            solver.seed(seed.getKey(), seed.getValue().longValue(),
                    seedSources.get(seed.getKey()));
        }
        for (String blocked : blacklist) {
            solver.blacklist(blocked);
        }

        PriceTable solved = applyWhitelist(solver.solve());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        this.table = solved;
        this.derivationNotes = buildDerivationNotes(solved);
        this.violations = ArbitrageValidator.validate(
                solved, recipes, config.server().sellMultiplier());

        BlockiesEconomy.LOGGER.info(
                "Priced {} items from {} recipes in {} ms ({} relaxation passes{}).",
                solved.size(), recipes.size(), elapsedMs, solved.passes(),
                solved.converged() ? "" : ", DID NOT CONVERGE");

        if (!solved.skippedRecipes().isEmpty()) {
            BlockiesEconomy.LOGGER.info(
                    "Skipped {} recipes with no fixed ingredients or no result "
                            + "(special recipes such as firework crafting and map cloning). "
                            + "Items reachable only that way need a price in prices.toml.",
                    solved.skippedRecipes().size());
        }

        reportViolations();
    }

    /**
     * Restricts the table to the whitelist, when one is configured.
     *
     * <p>An empty whitelist means no restriction at all, not an empty shop.
     */
    private PriceTable applyWhitelist(PriceTable solved) {
        if (!config.hasWhitelist()) {
            return solved;
        }
        Set<String> allowed = config.whitelist();
        PriceSolver filtered = new PriceSolver().multipliers(config.server().multiplierTable());
        for (Map.Entry<String, PriceEntry> entry : solved.entries().entrySet()) {
            if (allowed.contains(entry.getKey())) {
                filtered.seed(entry.getKey(), entry.getValue().buyPrice(),
                        entry.getValue().source());
            }
        }
        BlockiesEconomy.LOGGER.info(
                "Whitelist active: {} of {} priced items are available in the shop.",
                Math.min(allowed.size(), solved.size()), solved.size());
        return filtered.solve();
    }

    private void reportViolations() {
        if (violations.isEmpty()) {
            return;
        }
        BlockiesEconomy.LOGGER.warn(
                "{} recipes can be run at a profit. Players can use these to make Blockies "
                        + "from nothing. Usually this means a price in prices.toml is set "
                        + "below what its own ingredients cost.",
                violations.size());
        int shown = Math.min(violations.size(), 10);
        for (int i = 0; i < shown; i++) {
            BlockiesEconomy.LOGGER.warn("  {}", violations.get(i));
        }
        if (violations.size() > shown) {
            BlockiesEconomy.LOGGER.warn("  ... and {} more.", violations.size() - shown);
        }
    }

    // ---- precedence merge ----------------------------------------------------------

    private static final class MergedDeclarations {
        final Map<String, Long> prices = new LinkedHashMap<String, Long>();
        final Map<String, PriceSource> sources = new HashMap<String, PriceSource>();
        final Map<String, String> origins = new HashMap<String, String>();
        final Set<String> blacklist = new java.util.LinkedHashSet<String>();
    }

    /**
     * Merges every declaration source in precedence order.
     *
     * <p>Sources are visited highest authority first and a key already claimed is never
     * overwritten, so {@code prices.toml} beats a pack, which beats a tag rule.
     */
    private MergedDeclarations merge(MinecraftServer server) {
        MergedDeclarations merged = new MergedDeclarations();
        List<PriceDeclarations> sources = config.declarations();

        // Pass 1: explicit item ids, in precedence order.
        for (int i = 0; i < sources.size(); i++) {
            PriceDeclarations declarations = sources.get(i);
            merged.blacklist.addAll(declarations.blacklist());

            for (Map.Entry<String, Long> entry : declarations.itemPrices().entrySet()) {
                claim(merged, entry.getKey(), entry.getValue().longValue(),
                        declarations.source(), declarations.origin());
            }
        }

        // Pass 2: tag rules, which rank below every explicit id. Applying them second
        // means a tag can never overwrite an item someone named directly.
        for (int i = 0; i < sources.size(); i++) {
            PriceDeclarations declarations = sources.get(i);
            for (Map.Entry<String, Long> entry : declarations.tagPrices().entrySet()) {
                applyTagRule(server, merged, declarations, entry.getKey(),
                        entry.getValue().longValue());
            }
        }

        warnAboutUnknownItems(merged);
        return merged;
    }

    private void applyTagRule(MinecraftServer server, MergedDeclarations merged,
                              PriceDeclarations declarations, String tagId, long price) {
        Set<String> members = adapter.itemsInTag(server, tagId);
        if (members.isEmpty()) {
            BlockiesEconomy.LOGGER.debug(
                    "Tag rule #{} in {} matched no items; the tag may belong to a mod that "
                            + "is not installed.", tagId, declarations.origin());
            return;
        }
        int applied = 0;
        for (String member : members) {
            if (claim(merged, member, price, PriceSource.TAG, "#" + tagId)) {
                applied++;
            }
        }
        BlockiesEconomy.LOGGER.info("Tag rule #{} priced {} of {} matching items at {}.",
                tagId, applied, members.size(), price);
    }

    /** Records a price unless something with higher precedence already claimed the item. */
    private boolean claim(MergedDeclarations merged, String itemId, long price,
                          PriceSource source, String origin) {
        if (merged.prices.containsKey(itemId)) {
            return false;
        }
        merged.prices.put(itemId, Long.valueOf(price));
        merged.sources.put(itemId, source);
        merged.origins.put(itemId, origin);
        return true;
    }

    /**
     * Reports declared items this version does not have.
     *
     * <p>At debug level, not warn: the shipped price file covers several Minecraft
     * versions, so a 1.21-only item being absent on 1.20.1 is entirely normal and would
     * otherwise fill the log with noise on every start.
     */
    private void warnAboutUnknownItems(MergedDeclarations merged) {
        List<String> unknown = new ArrayList<String>();
        for (String itemId : merged.prices.keySet()) {
            if (!adapter.itemExists(itemId)) {
                unknown.add(itemId);
            }
        }
        if (!unknown.isEmpty()) {
            BlockiesEconomy.LOGGER.debug(
                    "{} declared items do not exist in this Minecraft version and were "
                            + "ignored (this is normal: the shipped price file spans several "
                            + "versions). First few: {}",
                    unknown.size(), unknown.subList(0, Math.min(5, unknown.size())));
        }
    }

    private Map<String, String> buildDerivationNotes(PriceTable solved) {
        Map<String, String> notes = new HashMap<String, String>();
        for (Map.Entry<String, PriceEntry> entry : solved.entries().entrySet()) {
            PriceEntry price = entry.getValue();
            notes.put(entry.getKey(), price.recipeId() == null
                    ? "declared (" + price.source() + ")"
                    : "derived from recipe " + price.recipeId());
        }
        return notes;
    }
}
