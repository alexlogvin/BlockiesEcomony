package com.alexlogvin.blockieseconomy.economy;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.config.ConfigManager;
import com.alexlogvin.blockieseconomy.core.ledger.Ledger;
import com.alexlogvin.blockieseconomy.core.ledger.RateLimiter;
import com.alexlogvin.blockieseconomy.core.ledger.TransactionResult;
import com.alexlogvin.blockieseconomy.core.price.PriceEntry;
import com.alexlogvin.blockieseconomy.core.price.TradeCalculator;
import com.alexlogvin.blockieseconomy.price.GameAdapter;
import com.alexlogvin.blockieseconomy.price.PriceEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Executes trades.
 *
 * <p>Every method here runs on the server, and nothing trusts a figure the client sent.
 * The client computes the same totals for display, but they are recomputed here before a
 * single Blockie moves — otherwise a modified client could name its own price.
 */
public final class EconomyManager {

    /** Refuse absurd quantities outright rather than doing arithmetic on them. */
    private static final int MAX_TRADE_COUNT = 10_000;

    private final ConfigManager config;
    private final PriceEngine prices;
    private final GameAdapter adapter;
    private final Ledger ledger;
    private final TransactionLog log;

    /**
     * Called after any balance change so the owner can flag the save data dirty.
     *
     * <p>A callback rather than a direct call into BalancePersistence: that class is
     * version-specific and owns state this class has no business knowing about.
     */
    private Runnable onBalanceChanged = () -> { };

    public EconomyManager(ConfigManager config, PriceEngine prices, GameAdapter adapter) {
        this.config = config;
        this.prices = prices;
        this.adapter = adapter;
        this.ledger = new Ledger(
                config.server().startingBalance(),
                new RateLimiter(config.server().tradesPerMinutePerPlayer(),
                        config.server().tradesPerMinuteGlobal()));
        this.log = new TransactionLog(config.server().transactionLog());
    }

    public void setBalanceChangeListener(Runnable listener) {
        this.onBalanceChanged = listener == null ? () -> { } : listener;
    }

    public Ledger ledger() {
        return ledger;
    }

    public long balance(ServerPlayer player) {
        return ledger.balance(player.getUUID());
    }

    // ---- buying --------------------------------------------------------------------

    /**
     * Buys {@code count} of an item.
     *
     * <p>Charged first, then delivered. Anything that will not fit is dropped at the
     * player's feet rather than refunded — the player asked for it and gets it.
     */
    public TradeOutcome buy(ServerPlayer player, String itemId, int count) {
        if (!prices.isReady()) {
            return TradeOutcome.failure(TradeOutcome.Reason.NOT_READY, itemId);
        }
        if (count <= 0 || count > MAX_TRADE_COUNT) {
            return TradeOutcome.failure(TradeOutcome.Reason.INVALID_AMOUNT, itemId);
        }

        Item item = resolveItem(itemId);
        if (item == null) {
            return TradeOutcome.failure(TradeOutcome.Reason.UNKNOWN_ITEM, itemId);
        }

        PriceEntry entry = prices.price(itemId);
        if (entry == null) {
            return TradeOutcome.failure(TradeOutcome.Reason.NOT_TRADEABLE, itemId);
        }

        long total = TradeCalculator.buyTotal(entry, count);
        TransactionResult charged =
                ledger.charge(player.getUUID(), total, System.currentTimeMillis());
        if (!charged.succeeded()) {
            return TradeOutcome.failure(mapFailure(charged), itemId, count, total);
        }

        int dropped = deliver(player, item, count);
        log.record(player.getUUID(), player.getGameProfile().getName(), charged,
                "buy " + count + "x " + itemId);
        markDirty(player.getServer());

        return TradeOutcome.ok(itemId, count, total, charged.balanceAfter(), dropped);
    }

    /**
     * Puts items in the inventory, dropping what will not fit.
     *
     * @return how many were dropped on the ground
     */
    private int deliver(ServerPlayer player, Item item, int count) {
        int remaining = count;
        int dropped = 0;
        int stackLimit = Math.max(1, new ItemStack(item).getMaxStackSize());

        while (remaining > 0) {
            int size = Math.min(remaining, stackLimit);
            ItemStack stack = new ItemStack(item, size);
            if (!player.getInventory().add(stack)) {
                // add() may have partially consumed the stack, so drop what is left of it.
                dropped += stack.getCount();
                if (!stack.isEmpty()) {
                    player.drop(stack, false);
                }
            }
            remaining -= size;
        }
        return dropped;
    }

    // ---- selling -------------------------------------------------------------------

    /**
     * Sells {@code count} of an item from the player's inventory.
     *
     * <p>Stacks carrying non-default components are skipped entirely, not sold at the base
     * price. That covers enchanted gear and renamed items, and — the one that actually
     * matters — a shulker box full of diamonds, which shares an item id with an empty one.
     */
    public TradeOutcome sell(ServerPlayer player, String itemId, int count) {
        if (!prices.isReady()) {
            return TradeOutcome.failure(TradeOutcome.Reason.NOT_READY, itemId);
        }
        if (count <= 0 || count > MAX_TRADE_COUNT) {
            return TradeOutcome.failure(TradeOutcome.Reason.INVALID_AMOUNT, itemId);
        }

        Item item = resolveItem(itemId);
        if (item == null) {
            return TradeOutcome.failure(TradeOutcome.Reason.UNKNOWN_ITEM, itemId);
        }

        PriceEntry entry = prices.price(itemId);
        if (entry == null) {
            return TradeOutcome.failure(TradeOutcome.Reason.NOT_TRADEABLE, itemId);
        }

        int sellable = countSellable(player, item);
        if (sellable <= 0) {
            // Distinguish "you have none" from "yours are all enchanted", because the
            // second is otherwise baffling for a player looking at a full inventory.
            return TradeOutcome.failure(
                    hasAny(player, item) ? TradeOutcome.Reason.NON_DEFAULT_COMPONENTS
                            : TradeOutcome.Reason.INSUFFICIENT_ITEMS, itemId);
        }
        if (sellable < count) {
            return TradeOutcome.failure(
                    TradeOutcome.Reason.INSUFFICIENT_ITEMS, itemId, sellable, 0L);
        }

        long total = takeAndPrice(player, item, count, entry);
        if (total <= 0L) {
            // A heavily damaged item can be worth nothing; taking it for free would be
            // a worse outcome than refusing the sale.
            return TradeOutcome.failure(TradeOutcome.Reason.NOT_TRADEABLE, itemId);
        }

        TransactionResult paid = ledger.pay(player.getUUID(), total, System.currentTimeMillis());
        if (!paid.succeeded()) {
            // The items are already gone at this point, so put them back.
            deliver(player, item, count);
            return TradeOutcome.failure(mapFailure(paid), itemId, count, total);
        }

        log.record(player.getUUID(), player.getGameProfile().getName(), paid,
                "sell " + count + "x " + itemId);
        markDirty(player.getServer());

        return TradeOutcome.ok(itemId, count, total, paid.balanceAfter(), 0);
    }

    /** How many of an item the player could actually sell, ignoring modified stacks. */
    public int countSellable(ServerPlayer player, Item item) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isSellable(stack, item)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private boolean hasAny(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSellable(ItemStack stack, Item item) {
        if (stack.isEmpty() || !stack.is(item)) {
            return false;
        }
        if (adapter.hasNonDefaultComponents(stack)) {
            // Damage alone is fine and pro-rated below; anything else is refused.
            return adapter.maxDurability(stack) > 0 && !stack.isEnchanted();
        }
        return true;
    }

    /**
     * Removes the items and returns what they are worth.
     *
     * <p>Priced per stack rather than in bulk, because a damaged tool is worth less than a
     * fresh one and each stack may have taken different wear.
     */
    private long takeAndPrice(ServerPlayer player, Item item, int count, PriceEntry entry) {
        double sellMultiplier = config.server().sellMultiplier();
        int remaining = count;
        long total = 0L;

        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0;
                slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isSellable(stack, item)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            int max = adapter.maxDurability(stack);
            total += max > 0
                    ? TradeCalculator.sellTotalDamaged(entry, take, sellMultiplier,
                            adapter.remainingDurability(stack), max)
                    : TradeCalculator.sellTotal(entry, take, sellMultiplier);

            stack.shrink(take);
            remaining -= take;
        }
        return total;
    }

    // ---- other balance changes ------------------------------------------------------

    /** Applies the configured death penalty. A penalty of 0 is a no-op. */
    public void applyDeathPenalty(ServerPlayer player) {
        long penalty = config.server().deathPenalty();
        if (penalty <= 0L) {
            return;
        }
        TransactionResult result = ledger.applyDeathPenalty(player.getUUID(), penalty);
        if (result.succeeded()) {
            log.record(player.getUUID(), player.getGameProfile().getName(), result, "death");
            markDirty(player.getServer());
        }
    }

    /** Pays an advancement prize. Not rate limited: the server decides when this happens. */
    public void awardAdvancement(ServerPlayer player, String advancementId, long prize) {
        if (prize <= 0L) {
            return;
        }
        TransactionResult result = ledger.award(player.getUUID(), prize);
        if (result.succeeded()) {
            log.record(player.getUUID(), player.getGameProfile().getName(), result,
                    "advancement " + advancementId);
            markDirty(player.getServer());
        }
    }

    // ---- helpers ---------------------------------------------------------------------

    private static Item resolveItem(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(location);
    }

    private static TradeOutcome.Reason mapFailure(TransactionResult result) {
        switch (result.status()) {
            case INSUFFICIENT_FUNDS:
                return TradeOutcome.Reason.INSUFFICIENT_FUNDS;
            case RATE_LIMITED:
                return TradeOutcome.Reason.RATE_LIMITED;
            default:
                return TradeOutcome.Reason.INVALID_AMOUNT;
        }
    }

    private void markDirty(MinecraftServer server) {
        try {
            onBalanceChanged.run();
        } catch (RuntimeException e) {
            BlockiesEconomy.LOGGER.error("Could not mark balances dirty: {}", e.toString());
        }
    }
}
