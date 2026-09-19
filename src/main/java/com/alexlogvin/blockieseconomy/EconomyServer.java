package com.alexlogvin.blockieseconomy;

import com.alexlogvin.blockieseconomy.command.ShopCommand;
import com.alexlogvin.blockieseconomy.config.ConfigManager;
import com.alexlogvin.blockieseconomy.config.ConfigPaths;
import com.alexlogvin.blockieseconomy.config.TomlFiles;
import com.alexlogvin.blockieseconomy.core.price.PriceEntry;
import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import com.alexlogvin.blockieseconomy.core.toml.TomlEntry;
import com.alexlogvin.blockieseconomy.core.toml.TomlTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlValue;
import com.alexlogvin.blockieseconomy.economy.AwardedAdvancements;
import com.alexlogvin.blockieseconomy.economy.BalancePersistence;
import com.alexlogvin.blockieseconomy.economy.EconomyManager;
import com.alexlogvin.blockieseconomy.net.ServerNetwork;
import com.alexlogvin.blockieseconomy.platform.ServerEvents;
import com.alexlogvin.blockieseconomy.platform.Services;
import com.alexlogvin.blockieseconomy.price.GameAdapter;
import com.alexlogvin.blockieseconomy.price.GameAdapters;
import com.alexlogvin.blockieseconomy.price.PriceEngine;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wires the mod together and owns its server-side state.
 *
 * <p>One instance per server. Created during mod init, populated when the server starts,
 * and discarded when it stops — so a single-player client that opens two worlds in a row
 * does not carry the first world's balances into the second.
 */
public final class EconomyServer {

    private final ConfigManager config = new ConfigManager();
    private final GameAdapter adapter = GameAdapters.create();
    private PriceEngine prices;
    private EconomyManager economy;
    private ServerNetwork network;
    private MinecraftServer server;

    /** Advancement id to prize, computed once per rebuild. */
    private Map<String, Long> advancementPrizes = new HashMap<String, Long>();

    /** Stops an operator re-granting an advancement from paying the prize twice. */
    private final AwardedAdvancements awarded = new AwardedAdvancements();

    public ConfigManager config() {
        return config;
    }

    public PriceEngine prices() {
        return prices;
    }

    public EconomyManager economy() {
        return economy;
    }

    /** Registers every server-side hook. Called once during mod construction. */
    public void register(ServerEvents events) {
        config.load();
        prices = new PriceEngine(config, adapter);
        economy = new EconomyManager(config, prices, adapter);
        economy.setBalanceChangeListener(this::onBalanceChanged);

        network = new ServerNetwork(this, Services.NETWORKING);
        network.register();

        events.onServerStarted(this::onServerStarted);
        events.onServerStopping(this::onServerStopping);
        events.onDataPackReload(this::onDataPackReload);
        events.onPlayerDeath(economy::applyDeathPenalty);
        events.onAdvancementEarned(this::onAdvancementEarned);
        events.onCommandRegistration(
                (dispatcher, dedicated) -> ShopCommand.register(dispatcher, this));

        BlockiesEconomy.LOGGER.info("{} ready on {}.",
                BlockiesEconomy.MOD_NAME, Services.PLATFORM.loaderName());
    }

    private void onServerStarted(MinecraftServer started) {
        this.server = started;

        // Balances and advancement history are per-world on disk, but this object outlives
        // any one world: a single-player client keeps the same mod instance across world
        // loads. Clearing before loading is what makes them actually per-world - otherwise
        // world A's balances survive into world B, because loading only adds entries.
        economy.ledger().clear();
        awarded.clear();

        BalancePersistence.load(started, economy.ledger(), awarded);
        rebuildPrices(started, null);
    }

    private void onServerStopping(MinecraftServer stopping) {
        // Balances live in a SavedData the world save already writes; marking dirty one
        // last time makes sure a change made in the final tick is not lost.
        BalancePersistence.markDirty(stopping, economy.ledger(), awarded);
        this.server = null;

        // Deliberately NOT cleared here. markDirty only sets a flag - the actual write
        // happens later, during the world save - so emptying the ledger at this point
        // destroys the data moments before it is written, and every session starts at 0.
        // Clearing at the START of onServerStarted is what isolates one world from the
        // next, and it holds even when a server dies without ever reaching this method.
    }

    /**
     * Rebuilds prices after a datapack reload.
     *
     * <p>Not optional: {@code /reload} replaces the recipe manager, so the table solved at
     * startup describes recipes that no longer exist.
     */
    private void onDataPackReload(MinecraftServer reloaded) {
        BlockiesEconomy.LOGGER.info("Datapacks reloaded; rebuilding prices.");
        rebuildPrices(reloaded, null);
    }

    private void onAdvancementEarned(ServerPlayer player, String advancementId) {
        Long prize = advancementPrizes.get(advancementId);
        if (prize == null || prize.longValue() <= 0L) {
            return;
        }

        // An operator running /advancement revoke then grant re-fires this event. Without
        // this guard that is a money printer handed out by accident, so a prize is paid
        // once per player per advancement and the record is persisted with their balance.
        if (!awarded.claim(player.getUUID(), advancementId)) {
            return;
        }

        economy.awardAdvancement(player, advancementId, prize.longValue());
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                Lang.ADVANCEMENT_REWARD,
                com.alexlogvin.blockieseconomy.command.MoneyText.shortForm(prize.longValue())));
    }

    // ---- prices --------------------------------------------------------------------

    public void rebuildPrices(MinecraftServer target, Runnable onComplete) {
        if (target == null) {
            return;
        }
        computeAdvancementPrizes(target);
        prices.rebuildAsync(target, () -> {
            writeGeneratedPrices();
            // Back onto the server thread for both of these: the network code reads the
            // player list, and the caller sends chat messages.
            target.execute(() -> {
                network.onPricesRebuilt(target.getPlayerList().getPlayers());
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    private void computeAdvancementPrizes(MinecraftServer target) {
        Map<String, Long> computed = new HashMap<String, Long>();
        List<GameAdapter.AdvancementInfo> all = adapter.advancements(target);
        for (int i = 0; i < all.size(); i++) {
            GameAdapter.AdvancementInfo info = all.get(i);
            computed.put(info.id(),
                    Long.valueOf(config.prizes().prizeFor(info.id(), info.rootId(), info.depth())));
        }
        advancementPrizes = computed;
        BlockiesEconomy.LOGGER.info("Computed prizes for {} advancements.", computed.size());
    }

    /** Reloads every config file. Prices are not rebuilt; that is {@code /shop rebuild}. */
    public void reloadConfig() {
        config.load();
        // Not just re-reading the files: the ledger, rate limiter and transaction log each
        // took a copy of their settings when they were built, so without this a reload
        // changes the file and nothing else.
        economy.applyConfig();
        BlockiesEconomy.LOGGER.info("Configuration reloaded.");
    }

    public void markBalancesDirty() {
        if (server != null) {
            BalancePersistence.markDirty(server, economy.ledger(), awarded);
        }
    }

    /** Asks a client to open the shop screen. False when it has no client-side mod. */
    public boolean requestOpenShop(ServerPlayer player) {
        return network.requestOpenShop(player);
    }

    /** Flags the save data and tells that one client its balance moved. */
    private void onBalanceChanged(ServerPlayer player) {
        markBalancesDirty();
        if (player != null) {
            network.sendBalance(player);
        }
    }

    /**
     * Pushes a balance an operator changed to the player, if they are online.
     *
     * <p>Admin commands work by UUID so they can reach an offline player, which is why
     * this takes one rather than a {@code ServerPlayer}. An offline player has nothing to
     * push to and picks the new figure up when they next join.
     */
    public void syncBalance(java.util.UUID playerId) {
        if (server == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            network.sendBalance(online);
        }
    }

    /**
     * Writes a price through to {@code prices.toml}, so {@code /shop price} survives a
     * restart and stays visible to an admin reading the file.
     *
     * @return false if the item does not exist in this version
     */
    public boolean setManualPrice(String itemId, long price, String setBy) {
        if (!adapter.itemExists(itemId)) {
            return false;
        }

        TomlDocument doc = TomlFiles.read(ConfigPaths.prices());
        TomlTable table = doc.table("set_in_game");
        if (table.isEmpty()) {
            table.comments().add("Prices set with /shop price <item> <price>.");
            table.comments().add("Edit or move these freely; they are ordinary entries.");
        }

        TomlEntry entry = table.put(itemId, TomlValue.of(price));
        entry.comments().clear();
        entry.comments().add("set by " + setBy + " on " + LocalDate.now());

        TomlFiles.write(ConfigPaths.prices(), doc);
        config.load();
        return true;
    }

    /**
     * Writes the solved table to {@code generated/prices.toml}.
     *
     * <p>Purely an artefact for admins to read and for balancing spreadsheets — it is
     * never read back. The schema version is stamped so a future change to the solver can
     * be told apart from an older run's output.
     */
    private void writeGeneratedPrices() {
        if (!prices.isReady()) {
            return;
        }
        TomlDocument doc = new TomlDocument();
        doc.headerComments().addAll(java.util.Arrays.asList(
                "GENERATED FILE - DO NOT EDIT.",
                "",
                "Rewritten every time prices are rebuilt. Anything you change here is lost.",
                "To set a price, edit prices.toml or run /shop price <item> <price>.",
                "",
                "This exists so you can see what the solver worked out, and export it for",
                "balancing. Each entry notes where its price came from.",
                "",
                "schema_version = " + PriceEngine.SCHEMA_VERSION,
                "generated on " + LocalDate.now()));

        TomlTable meta = doc.table("meta");
        meta.put("schema_version", TomlValue.of(PriceEngine.SCHEMA_VERSION));
        meta.put("item_count", TomlValue.of(prices.table().size()));
        meta.put("solver_passes", TomlValue.of(prices.table().passes()));
        meta.put("converged", TomlValue.of(prices.table().converged()));

        TomlTable items = doc.table("prices");
        double sellMultiplier = config.server().sellMultiplier();
        for (Map.Entry<String, PriceEntry> e : prices.table().entries().entrySet()) {
            PriceEntry price = e.getValue();
            TomlEntry written = items.put(e.getKey(), TomlValue.of(price.buyPrice()));
            written.setInlineComment("sell " + price.sellPrice(sellMultiplier)
                    + ", " + prices.derivation(e.getKey()));
        }

        if (!advancementPrizes.isEmpty()) {
            TomlTable advancements = doc.table("advancement_prizes");
            advancements.comments().add(
                    "Computed from base_prize * depth_exponent ^ depth.");
            advancements.comments().add(
                    "Override any of these in advancements.toml.");
            for (Map.Entry<String, Long> e : advancementPrizes.entrySet()) {
                advancements.put(e.getKey(), TomlValue.of(e.getValue().longValue()));
            }
        }

        TomlFiles.write(ConfigPaths.generatedPrices(), doc);
    }

    /**
     * Writes the full price table as CSV for balancing in a spreadsheet.
     *
     * <p>Reviewing several thousand derived prices is not something to do in chat, and it
     * is the only practical way to spot an item the solver has priced absurdly.
     *
     * @return the file written, or null on failure
     */
    public java.nio.file.Path exportCsv() {
        if (!prices.isReady()) {
            return null;
        }
        double sellMultiplier = config.server().sellMultiplier();
        StringBuilder csv = new StringBuilder(64 * 1024);
        csv.append("item,buy,sell,source,recipe\n");

        for (Map.Entry<String, PriceEntry> e : prices.table().entries().entrySet()) {
            PriceEntry price = e.getValue();
            csv.append(csvField(e.getKey())).append(',')
               .append(price.buyPrice()).append(',')
               .append(price.sellPrice(sellMultiplier)).append(',')
               .append(price.source()).append(',')
               .append(csvField(price.recipeId() == null ? "" : price.recipeId()))
               .append('\n');
        }

        java.nio.file.Path path = ConfigPaths.generated().resolve("prices.csv");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path,
                    csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            BlockiesEconomy.LOGGER.info("Exported {} prices to {}",
                    prices.table().size(), path);
            return path;
        } catch (java.io.IOException ex) {
            BlockiesEconomy.LOGGER.error("Could not export prices: {}", ex.toString());
            return null;
        }
    }

    /** Quotes a CSV field when it contains a comma, quote or newline. */
    private static String csvField(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
