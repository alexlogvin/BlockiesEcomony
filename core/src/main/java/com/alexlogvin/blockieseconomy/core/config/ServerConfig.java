package com.alexlogvin.blockieseconomy.core.config;

import com.alexlogvin.blockieseconomy.core.price.AdvancementPrizeCalculator;
import com.alexlogvin.blockieseconomy.core.price.MultiplierTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import com.alexlogvin.blockieseconomy.core.toml.TomlEntry;
import com.alexlogvin.blockieseconomy.core.toml.TomlTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlValue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * server.toml — economy rules, limits and permissions.
 *
 * <p>Written back with explanatory comments so an operator can configure the mod without
 * reading the source. The multiplier block in particular documents the arbitrage ceiling,
 * because that is the one setting where a plausible-looking value breaks the economy.
 */
public final class ServerConfig {

    public static final String TABLE_ECONOMY = "economy";
    public static final String TABLE_MULTIPLIERS = "recipe_multipliers";
    public static final String TABLE_ADVANCEMENTS = "advancements";
    public static final String TABLE_LIMITS = "limits";
    public static final String TABLE_PERMISSIONS = "permissions";

    /** Recipe types that consume nothing beyond their ingredients, so take no markup. */
    private static final List<String> FREE_RECIPE_TYPES =
            Arrays.asList("minecraft:crafting", "minecraft:stonecutting");

    /**
     * Types that fall through to the default. Written into the file commented out, purely
     * so an operator can see which keys are valid without hunting through documentation.
     */
    private static final List<String> DOCUMENTED_RECIPE_TYPES = Arrays.asList(
            "minecraft:smelting",
            "minecraft:blasting",
            "minecraft:smoking",
            "minecraft:campfire_cooking",
            "minecraft:smithing",
            "minecraft:brewing");

    private double sellMultiplier = 0.75d;
    private double defaultRecipeMultiplier = MultiplierTable.DEFAULT_MULTIPLIER;
    private final Map<String, Double> recipeMultipliers = new LinkedHashMap<String, Double>();

    private long startingBalance = 0L;
    private long deathPenalty = 0L;

    private long advancementBase = AdvancementPrizeCalculator.DEFAULT_BASE;
    private double advancementExponent = AdvancementPrizeCalculator.DEFAULT_EXPONENT;

    private int tradesPerMinutePerPlayer = 120;
    private int tradesPerMinuteGlobal = 2_000;
    private boolean transactionLog = false;

    private int adminPermissionLevel = 2;
    private boolean leaderboardPublic = true;
    private long confirmThreshold = 1_000_000L;

    public ServerConfig() {
        for (int i = 0; i < FREE_RECIPE_TYPES.size(); i++) {
            recipeMultipliers.put(FREE_RECIPE_TYPES.get(i),
                    Double.valueOf(MultiplierTable.NO_MARKUP));
        }
    }

    // ---- accessors ---------------------------------------------------------------

    public double sellMultiplier() {
        return sellMultiplier;
    }

    public double defaultRecipeMultiplier() {
        return defaultRecipeMultiplier;
    }

    public Map<String, Double> recipeMultipliers() {
        return recipeMultipliers;
    }

    public long startingBalance() {
        return startingBalance;
    }

    public long deathPenalty() {
        return deathPenalty;
    }

    public long advancementBase() {
        return advancementBase;
    }

    public double advancementExponent() {
        return advancementExponent;
    }

    public int tradesPerMinutePerPlayer() {
        return tradesPerMinutePerPlayer;
    }

    public int tradesPerMinuteGlobal() {
        return tradesPerMinuteGlobal;
    }

    public boolean transactionLog() {
        return transactionLog;
    }

    public int adminPermissionLevel() {
        return adminPermissionLevel;
    }

    public boolean leaderboardPublic() {
        return leaderboardPublic;
    }

    /** Balance edits above this require the command to be confirmed. */
    public long confirmThreshold() {
        return confirmThreshold;
    }

    // ---- mutators ----------------------------------------------------------------
    //
    // Only the settings the in-game config screen offers. Everything else is edited in
    // server.toml, where the surrounding comments explain it.
    //
    // None of these validate the arbitrage invariant, and that is deliberate.
    // MultiplierTable already owns that rule and clamps on load; a second copy here
    // would be a second definition of exactly the kind that made the shop quote a sell
    // price it would not pay. The screen saves, reloads, and reads the clamped values
    // back, so an out-of-range entry corrects itself in front of the player.

    public void setSellMultiplier(double sellMultiplier) {
        this.sellMultiplier = sellMultiplier;
    }

    public void setDefaultRecipeMultiplier(double defaultRecipeMultiplier) {
        this.defaultRecipeMultiplier = defaultRecipeMultiplier;
    }

    public void setStartingBalance(long startingBalance) {
        this.startingBalance = startingBalance;
    }

    public void setDeathPenalty(long deathPenalty) {
        this.deathPenalty = deathPenalty;
    }

    public void setAdvancementBase(long advancementBase) {
        this.advancementBase = advancementBase;
    }

    public void setAdvancementExponent(double advancementExponent) {
        this.advancementExponent = advancementExponent;
    }

    public void setTransactionLog(boolean transactionLog) {
        this.transactionLog = transactionLog;
    }

    public void setLeaderboardPublic(boolean leaderboardPublic) {
        this.leaderboardPublic = leaderboardPublic;
    }

    /** Builds the multiplier table, clamping anything that would allow arbitrage. */
    public MultiplierTable multiplierTable() {
        return MultiplierTable.of(recipeMultipliers, defaultRecipeMultiplier, sellMultiplier);
    }

    // ---- reading -----------------------------------------------------------------

    public static ServerConfig fromToml(TomlDocument doc) {
        ServerConfig c = new ServerConfig();

        c.sellMultiplier = doc.getDouble(TABLE_ECONOMY, "sell_multiplier", c.sellMultiplier);
        c.startingBalance = doc.getLong(TABLE_ECONOMY, "starting_balance", c.startingBalance);
        c.deathPenalty = doc.getLong(TABLE_ECONOMY, "death_penalty", c.deathPenalty);

        TomlTable multipliers = doc.findTable(TABLE_MULTIPLIERS);
        if (multipliers != null) {
            c.recipeMultipliers.clear();
            for (TomlEntry entry : multipliers.entries()) {
                if ("default_recipe_multiplier".equals(entry.key())) {
                    c.defaultRecipeMultiplier = entry.value().asDouble();
                } else {
                    c.recipeMultipliers.put(entry.key(),
                            Double.valueOf(entry.value().asDouble()));
                }
            }
        }

        c.advancementBase = doc.getLong(TABLE_ADVANCEMENTS, "base_prize", c.advancementBase);
        c.advancementExponent =
                doc.getDouble(TABLE_ADVANCEMENTS, "depth_exponent", c.advancementExponent);

        c.tradesPerMinutePerPlayer = (int) doc.getLong(
                TABLE_LIMITS, "trades_per_minute_per_player", c.tradesPerMinutePerPlayer);
        c.tradesPerMinuteGlobal = (int) doc.getLong(
                TABLE_LIMITS, "trades_per_minute_global", c.tradesPerMinuteGlobal);
        c.transactionLog = doc.getBoolean(TABLE_LIMITS, "transaction_log", c.transactionLog);

        c.adminPermissionLevel = (int) doc.getLong(
                TABLE_PERMISSIONS, "admin_level", c.adminPermissionLevel);
        c.leaderboardPublic = doc.getBoolean(
                TABLE_PERMISSIONS, "leaderboard_public", c.leaderboardPublic);
        c.confirmThreshold = doc.getLong(
                TABLE_PERMISSIONS, "confirm_above", c.confirmThreshold);

        return c;
    }

    // ---- writing -----------------------------------------------------------------

    public TomlDocument toToml() {
        TomlDocument doc = new TomlDocument();
        doc.headerComments().addAll(Arrays.asList(
                "Blockies Economy - server configuration.",
                "",
                "Prices themselves live in prices.toml; this file holds the rules applied to them.",
                "Changes take effect on /shop reload, except recipe multipliers, which also",
                "need /shop rebuild to recompute derived prices."));

        economyTable(doc);
        multiplierTable(doc);
        advancementTable(doc);
        limitsTable(doc);
        permissionsTable(doc);

        return doc;
    }

    private void economyTable(TomlDocument doc) {
        TomlTable t = doc.table(TABLE_ECONOMY);
        t.comments().add("Core economy settings.");

        put(t, "sell_multiplier", TomlValue.of(sellMultiplier),
                "What an item sells for, as a fraction of its buy price.",
                "0.75 means selling recovers three quarters of what buying cost.",
                "This also sets the ceiling on every recipe multiplier below.");

        put(t, "starting_balance", TomlValue.of(startingBalance),
                "Balance a player begins with, before earning anything.");

        put(t, "death_penalty", TomlValue.of(deathPenalty),
                "Blockies lost on death. 0 disables it.",
                "A player who cannot pay in full loses everything they have, not more.");
    }

    private void multiplierTable(TomlDocument doc) {
        TomlTable t = doc.table(TABLE_MULTIPLIERS);
        t.comments().addAll(Arrays.asList(
                "Markup applied to the sum of a recipe's ingredient prices.",
                "",
                "GOOD RULE FORMULA: every multiplier must stay below 1 / sell_multiplier.",
                "  At the default sell_multiplier = 0.75 the ceiling is 1.333.",
                "  Above it, a player can buy the ingredients, craft, and sell the result",
                "  at a profit - forever. Values over the ceiling are clamped at startup",
                "  and a warning is written to the server log.",
                "",
                "Recipe types that need no extra resources, so take no markup:"));

        for (Map.Entry<String, Double> e : recipeMultipliers.entrySet()) {
            t.put(e.getKey(), TomlValue.of(e.getValue().doubleValue()));
        }

        // Types that inherit the default are listed commented out, as documentation of
        // which keys exist. Writing them as real entries would be redundant.
        TomlEntry defaultEntry = t.put("default_recipe_multiplier",
                TomlValue.of(defaultRecipeMultiplier));
        defaultEntry.comments().addAll(Arrays.asList(
                "Types that consume fuel or extra resources use the default below.",
                "Uncomment any of these to override one individually:"));
        for (int i = 0; i < DOCUMENTED_RECIPE_TYPES.size(); i++) {
            defaultEntry.comments().add(
                    "  \"" + DOCUMENTED_RECIPE_TYPES.get(i) + "\" = " + defaultRecipeMultiplier);
        }
        defaultEntry.comments().addAll(Arrays.asList(
                "",
                "Applies to every recipe type not listed above, including modded ones."));
    }

    private void advancementTable(TomlDocument doc) {
        TomlTable t = doc.table(TABLE_ADVANCEMENTS);
        t.comments().addAll(Arrays.asList(
                "Advancement prizes, computed as base_prize * depth_exponent ^ depth.",
                "Per-tree and per-advancement overrides go in advancements.toml."));

        put(t, "base_prize", TomlValue.of(advancementBase),
                "What a root advancement pays.");
        put(t, "depth_exponent", TomlValue.of(advancementExponent),
                "Growth per level of depth. 1.5 means each step pays half again as much;",
                "1.0 pays the same everywhere.");
    }

    private void limitsTable(TomlDocument doc) {
        TomlTable t = doc.table(TABLE_LIMITS);
        t.comments().addAll(Arrays.asList(
                "Abuse limits. The defaults are generous enough that nobody trading by",
                "hand will notice; they exist to blunt autoclickers and packet spam."));

        put(t, "trades_per_minute_per_player", TomlValue.of(tradesPerMinutePerPlayer),
                "0 disables the per-player limit.");
        put(t, "trades_per_minute_global", TomlValue.of(tradesPerMinuteGlobal),
                "0 disables the server-wide limit.");
        put(t, "transaction_log", TomlValue.of(transactionLog),
                "Append every transaction to generated/transactions.log.",
                "Useful when investigating a suspected exploit. Operator commands are",
                "always logged regardless of this setting.");
    }

    private void permissionsTable(TomlDocument doc) {
        TomlTable t = doc.table(TABLE_PERMISSIONS);
        t.comments().add("Who may run which commands.");

        put(t, "admin_level", TomlValue.of(adminPermissionLevel),
                "Permission level required for /shop rebuild, /shop reload,",
                "/shop price <item> <price> and /shop balance <player> ...",
                "2 is the same level as /gamemode.");
        put(t, "leaderboard_public", TomlValue.of(leaderboardPublic),
                "Whether everyone can run /shop top, or only operators.");
        put(t, "confirm_above", TomlValue.of(confirmThreshold),
                "Balance edits larger than this must be confirmed, so a mistyped",
                "amount cannot wreck the economy in one keystroke.");
    }

    private static void put(TomlTable table, String key, TomlValue value, String... comments) {
        TomlEntry entry = table.put(key, value);
        for (int i = 0; i < comments.length; i++) {
            entry.comments().add(comments[i]);
        }
    }
}
