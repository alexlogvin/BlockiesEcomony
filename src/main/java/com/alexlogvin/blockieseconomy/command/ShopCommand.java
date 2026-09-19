package com.alexlogvin.blockieseconomy.command;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.EconomyServer;
import com.alexlogvin.blockieseconomy.core.price.PriceEntry;
import com.alexlogvin.blockieseconomy.economy.TradeOutcome;
import com.alexlogvin.blockieseconomy.platform.Services;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code /shop} command tree.
 *
 * <p>Item arguments use {@code ResourceLocationArgument} rather than {@code ItemArgument}.
 * That avoids the 1.20.1/1.21 split where {@code ItemInput} moved from NBT to a
 * {@code DataComponentPatch}, and it lets suggestions be limited to items that actually
 * have a price — suggesting the whole item registry when most of it is not for sale would
 * be worse than useless.
 *
 * <p>A plain string argument does NOT work here: Brigadier only accepts
 * {@code [a-zA-Z0-9_.+-]} in an unquoted token, so typing the colon in
 * {@code minecraft:diamond} failed with "expected whitespace to end one argument".
 */
public final class ShopCommand {

    private ShopCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                EconomyServer economy) {
        int adminLevel = economy.config().server().adminPermissionLevel();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("shop")
                .executes(ctx -> showHelp(ctx, economy, adminLevel));

        root.then(Commands.literal("ui").executes(ctx -> openUi(ctx, economy)));

        root.then(Commands.literal("balance")
                .executes(ctx -> ownBalance(ctx, economy))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> source.hasPermission(adminLevel))
                        .suggests(playerSuggestions())
                        .executes(ctx -> otherBalance(ctx, economy))
                        .then(balanceOperation("set", economy, adminLevel))
                        .then(balanceOperation("add", economy, adminLevel))
                        .then(balanceOperation("remove", economy, adminLevel))));

        root.then(Commands.literal("buy")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests(pricedItemSuggestions(economy))
                        .executes(ctx -> buy(ctx, economy, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> buy(ctx, economy,
                                        IntegerArgumentType.getInteger(ctx, "amount"))))));

        root.then(Commands.literal("sell")
                .executes(ctx -> sellHeld(ctx, economy, 1))
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests(pricedItemSuggestions(economy))
                        .executes(ctx -> sell(ctx, economy, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> sell(ctx, economy,
                                        IntegerArgumentType.getInteger(ctx, "count"))))));

        root.then(Commands.literal("price")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests(pricedItemSuggestions(economy))
                        .executes(ctx -> showPrice(ctx, economy))
                        .then(Commands.argument("price", LongArgumentType.longArg(0))
                                .requires(source -> source.hasPermission(adminLevel))
                                .executes(ctx -> setPrice(ctx, economy)))));

        root.then(Commands.literal("top")
                .requires(source -> economy.config().server().leaderboardPublic()
                        || source.hasPermission(adminLevel))
                .executes(ctx -> leaderboard(ctx, economy)));

        root.then(Commands.literal("rebuild")
                .requires(source -> source.hasPermission(adminLevel))
                .executes(ctx -> rebuild(ctx, economy)));

        // Kept as an alias because "rebuild" is the clearer name but "recalculate" is
        // what an admin reaching for this is likely to type.
        root.then(Commands.literal("recalculate")
                .requires(source -> source.hasPermission(adminLevel))
                .executes(ctx -> rebuild(ctx, economy)));

        root.then(Commands.literal("reload")
                .requires(source -> source.hasPermission(adminLevel))
                .executes(ctx -> reload(ctx, economy)));

        root.then(Commands.literal("debug")
                .requires(source -> source.hasPermission(adminLevel))
                .then(Commands.literal("export").executes(ctx -> exportCsv(ctx, economy)))
                .then(Commands.literal("price")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .suggests(pricedItemSuggestions(economy))
                                .executes(ctx -> debugPrice(ctx, economy)))));

        dispatcher.register(root);
    }

    // ---- suggestions -----------------------------------------------------------------

    /** Only items with a price, so tab-completion reflects what is actually for sale. */
    private static SuggestionProvider<CommandSourceStack> pricedItemSuggestions(
            EconomyServer economy) {
        return (ctx, builder) -> {
            if (!economy.prices().isReady()) {
                return builder.buildFuture();
            }
            return SharedSuggestionProvider.suggest(
                    economy.prices().table().entries().keySet().stream(), builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> playerSuggestions() {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(
                ctx.getSource().getServer().getPlayerNames(), builder);
    }

    // ---- /shop -----------------------------------------------------------------------

    private static int showHelp(CommandContext<CommandSourceStack> ctx,
                                EconomyServer economy, int adminLevel) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable(Lang.VERSION,
                BlockiesEconomy.MOD_NAME,
                Services.PLATFORM.modVersion(),
                Services.PLATFORM.loaderName()).withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.translatable(Lang.HELP_HEADER)
                .withStyle(ChatFormatting.GRAY), false);
        for (String key : new String[] {Lang.HELP_UI, Lang.HELP_BALANCE, Lang.HELP_BUY,
                Lang.HELP_SELL, Lang.HELP_PRICE, Lang.HELP_TOP}) {
            source.sendSuccess(() -> Component.translatable(key), false);
        }

        if (source.hasPermission(adminLevel)) {
            source.sendSuccess(() -> Component.translatable(Lang.HELP_ADMIN_HEADER)
                    .withStyle(ChatFormatting.GRAY), false);
            for (String key : new String[] {Lang.HELP_ADMIN_BALANCE, Lang.HELP_ADMIN_PRICE,
                    Lang.HELP_ADMIN_REBUILD, Lang.HELP_ADMIN_RELOAD, Lang.HELP_ADMIN_DEBUG,
                    Lang.HELP_ADMIN_EXPORT}) {
                source.sendSuccess(() -> Component.translatable(key), false);
            }
        }
        return 1;
    }

    private static int openUi(CommandContext<CommandSourceStack> ctx, EconomyServer economy) {
        // The screen lives client-side, so this asks the client to open it. A vanilla
        // client has no channel to ask on, which is what the failure below reports.
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_PLAYERS_ONLY));
            return 0;
        }
        if (!economy.requestOpenShop(player)) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_CLIENT_REQUIRED));
            return 0;
        }
        return 1;
    }

    // ---- balances ---------------------------------------------------------------------

    private static int ownBalance(CommandContext<CommandSourceStack> ctx,
                                  EconomyServer economy) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_PLAYERS_ONLY));
            return 0;
        }
        long balance = economy.economy().balance(player);
        ctx.getSource().sendSuccess(() -> Component.translatable(Lang.BALANCE_SELF,
                MoneyText.shortForm(balance), MoneyText.fullForm(balance)), false);
        return 1;
    }

    private static int otherBalance(CommandContext<CommandSourceStack> ctx,
                                    EconomyServer economy) {
        String name = StringArgumentType.getString(ctx, "player");
        UUID uuid = resolvePlayer(ctx.getSource().getServer(), name);
        if (uuid == null) {
            ctx.getSource().sendFailure(
                    Component.translatable(Lang.ERROR_UNKNOWN_PLAYER, name));
            return 0;
        }
        long balance = economy.economy().ledger().balance(uuid);
        ctx.getSource().sendSuccess(() -> Component.translatable(Lang.BALANCE_OTHER,
                name, MoneyText.shortForm(balance), MoneyText.fullForm(balance)), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> balanceOperation(
            String operation, EconomyServer economy, int adminLevel) {
        return Commands.literal(operation)
                .requires(source -> source.hasPermission(adminLevel))
                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                        .executes(ctx -> adjustBalance(ctx, economy, operation, false))
                        // A large edit must be repeated with "confirm", so one mistyped
                        // digit cannot wreck an economy in a single keystroke.
                        .then(Commands.literal("confirm")
                                .executes(ctx -> adjustBalance(ctx, economy, operation, true))));
    }

    private static int adjustBalance(CommandContext<CommandSourceStack> ctx,
                                     EconomyServer economy, String operation,
                                     boolean confirmed) {
        String name = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");

        UUID uuid = resolvePlayer(ctx.getSource().getServer(), name);
        if (uuid == null) {
            ctx.getSource().sendFailure(
                    Component.translatable(Lang.ERROR_UNKNOWN_PLAYER, name));
            return 0;
        }

        long threshold = economy.config().server().confirmThreshold();
        if (!confirmed && amount > threshold) {
            ctx.getSource().sendFailure(Component.translatable(Lang.BALANCE_CONFIRM,
                    MoneyText.fullForm(amount), name, MoneyText.fullForm(threshold)));
            return 0;
        }

        com.alexlogvin.blockieseconomy.core.ledger.TransactionResult result;
        String messageKey;
        switch (operation) {
            case "set":
                result = economy.economy().ledger().adminSet(uuid, amount);
                messageKey = Lang.BALANCE_SET;
                break;
            case "add":
                result = economy.economy().ledger().adminAdd(uuid, amount);
                messageKey = Lang.BALANCE_ADDED;
                break;
            default:
                result = economy.economy().ledger().adminRemove(uuid, amount);
                messageKey = Lang.BALANCE_REMOVED;
                break;
        }

        if (!result.succeeded()) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_INVALID_AMOUNT));
            return 0;
        }

        long after = result.balanceAfter();
        String key = messageKey;
        ctx.getSource().sendSuccess(() -> Component.translatable(key, name,
                MoneyText.fullForm(amount), MoneyText.fullForm(after)), true);
        economy.markBalancesDirty();
        economy.syncBalance(uuid);
        return 1;
    }

    // ---- trading -----------------------------------------------------------------------

    private static int buy(CommandContext<CommandSourceStack> ctx, EconomyServer economy,
                           int amount) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_PLAYERS_ONLY));
            return 0;
        }
        String itemId = ResourceLocationArgument.getId(ctx, "item").toString();
        return report(ctx, economy.economy().buy(player, itemId, amount), true);
    }

    private static int sell(CommandContext<CommandSourceStack> ctx, EconomyServer economy,
                            int count) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_PLAYERS_ONLY));
            return 0;
        }
        String itemId = ResourceLocationArgument.getId(ctx, "item").toString();
        return report(ctx, economy.economy().sell(player, itemId, count), false);
    }

    /** {@code /shop sell} with no item sells what the player is holding. */
    private static int sellHeld(CommandContext<CommandSourceStack> ctx,
                                EconomyServer economy, int count) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_PLAYERS_ONLY));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_INSUFFICIENT_ITEMS));
            return 0;
        }
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(held.getItem()).toString();
        return report(ctx, economy.economy().sell(player, itemId, count), false);
    }

    private static int report(CommandContext<CommandSourceStack> ctx, TradeOutcome outcome,
                              boolean buying) {
        if (!outcome.succeeded()) {
            ctx.getSource().sendFailure(errorFor(outcome));
            return 0;
        }
        if (buying) {
            if (outcome.droppedOnGround() > 0) {
                ctx.getSource().sendSuccess(() -> Component.translatable(
                        Lang.BUY_SUCCESS_DROPPED, outcome.count(), outcome.itemId(),
                        MoneyText.shortForm(outcome.total()),
                        outcome.droppedOnGround(),
                        MoneyText.shortForm(outcome.balanceAfter())), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.translatable(Lang.BUY_SUCCESS,
                        outcome.count(), outcome.itemId(),
                        MoneyText.shortForm(outcome.total()),
                        MoneyText.shortForm(outcome.balanceAfter())), false);
            }
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable(Lang.SELL_SUCCESS,
                    outcome.count(), outcome.itemId(),
                    MoneyText.shortForm(outcome.total()),
                    MoneyText.shortForm(outcome.balanceAfter())), false);
        }
        return 1;
    }

    private static Component errorFor(TradeOutcome outcome) {
        switch (outcome.reason()) {
            case NOT_READY:
                return Component.translatable(Lang.ERROR_NOT_READY);
            case NOT_TRADEABLE:
                return Component.translatable(Lang.ERROR_NOT_TRADEABLE, outcome.itemId());
            case UNKNOWN_ITEM:
                return Component.translatable(Lang.ERROR_UNKNOWN_ITEM, outcome.itemId());
            case INSUFFICIENT_FUNDS:
                return Component.translatable(Lang.ERROR_INSUFFICIENT_FUNDS,
                        MoneyText.shortForm(outcome.total()));
            case INSUFFICIENT_ITEMS:
                return Component.translatable(Lang.ERROR_INSUFFICIENT_ITEMS);
            case NON_DEFAULT_COMPONENTS:
                return Component.translatable(Lang.ERROR_NON_DEFAULT_COMPONENTS);
            case RATE_LIMITED:
                return Component.translatable(Lang.ERROR_RATE_LIMITED);
            default:
                return Component.translatable(Lang.ERROR_INVALID_AMOUNT);
        }
    }

    // ---- prices -------------------------------------------------------------------------

    private static int showPrice(CommandContext<CommandSourceStack> ctx,
                                 EconomyServer economy) {
        String itemId = ResourceLocationArgument.getId(ctx, "item").toString();
        if (!economy.prices().isReady()) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_NOT_READY));
            return 0;
        }
        PriceEntry entry = economy.prices().price(itemId);
        if (entry == null) {
            boolean blacklisted = economy.prices().table().blacklisted().contains(itemId);
            ctx.getSource().sendFailure(Component.translatable(
                    blacklisted ? Lang.PRICE_BLACKLISTED : Lang.ERROR_NOT_TRADEABLE, itemId));
            return 0;
        }
        long buy = entry.buyPrice();
        long sell = entry.sellPrice(economy.config().server().sellMultiplier());
        ctx.getSource().sendSuccess(() -> Component.translatable(Lang.PRICE_INFO,
                itemId, MoneyText.fullForm(buy), MoneyText.fullForm(sell)), false);
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> ctx,
                                EconomyServer economy) {
        String itemId = ResourceLocationArgument.getId(ctx, "item").toString();
        long price = LongArgumentType.getLong(ctx, "price");
        String setBy = ctx.getSource().getTextName();

        if (!economy.setManualPrice(itemId, price, setBy)) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_UNKNOWN_ITEM, itemId));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(Lang.PRICE_SET,
                itemId, MoneyText.fullForm(price)), true);
        return rebuild(ctx, economy);
    }

    private static int debugPrice(CommandContext<CommandSourceStack> ctx,
                                  EconomyServer economy) {
        String itemId = ResourceLocationArgument.getId(ctx, "item").toString();
        if (!economy.prices().isReady()) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_NOT_READY));
            return 0;
        }
        PriceEntry entry = economy.prices().price(itemId);
        if (entry == null) {
            ctx.getSource().sendFailure(
                    Component.translatable(Lang.ERROR_NOT_TRADEABLE, itemId));
            return 0;
        }

        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable(Lang.DEBUG_HEADER, itemId)
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable(Lang.DEBUG_SOURCE,
                economy.prices().derivation(itemId)), false);
        source.sendSuccess(() -> Component.translatable(Lang.DEBUG_VALUE,
                MoneyText.fullForm(entry.buyPrice()),
                MoneyText.fullForm(entry.sellPrice(
                        economy.config().server().sellMultiplier())),
                String.valueOf(entry.buyMicros())), false);
        return 1;
    }

    // ---- maintenance ---------------------------------------------------------------------

    private static int rebuild(CommandContext<CommandSourceStack> ctx,
                               EconomyServer economy) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable(Lang.REBUILD_STARTED), true);
        economy.rebuildPrices(source.getServer(), () ->
                source.sendSuccess(() -> Component.translatable(Lang.REBUILD_DONE,
                        economy.prices().isReady() ? economy.prices().table().size() : 0), true));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, EconomyServer economy) {
        economy.reloadConfig();
        ctx.getSource().sendSuccess(() -> Component.translatable(Lang.RELOAD_DONE), true);
        return 1;
    }

    // ---- leaderboard -----------------------------------------------------------------------

    private static int leaderboard(CommandContext<CommandSourceStack> ctx,
                                   EconomyServer economy) {
        List<Map.Entry<UUID, Long>> top = economy.economy().ledger().leaderboard(10);
        CommandSourceStack source = ctx.getSource();

        if (top.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(Lang.TOP_EMPTY), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable(Lang.TOP_HEADER)
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < top.size(); i++) {
            int rank = i + 1;
            Map.Entry<UUID, Long> entry = top.get(i);
            String name = nameOf(source.getServer(), entry.getKey());
            source.sendSuccess(() -> Component.translatable(Lang.TOP_ENTRY, rank, name,
                    MoneyText.shortForm(entry.getValue().longValue())), false);
        }
        return 1;
    }

    // ---- player resolution ------------------------------------------------------------------

    /**
     * Resolves a name to a UUID, falling back to the server's profile cache.
     *
     * <p>The cache lookup is what makes admin commands work on offline players, which is
     * most of the time you need them: investigating a report usually happens after the
     * player has logged off.
     */
    private static UUID resolvePlayer(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        Optional<GameProfile> cached = server.getProfileCache() == null
                ? Optional.empty()
                : server.getProfileCache().get(name);
        return cached.map(GameProfile::getId).orElse(null);
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        Optional<GameProfile> cached = server.getProfileCache() == null
                ? Optional.empty()
                : server.getProfileCache().get(uuid);
        return cached.map(GameProfile::getName).orElse(uuid.toString().substring(0, 8));
    }

    /** Writes the whole price table to generated/prices.csv for spreadsheet balancing. */
    private static int exportCsv(CommandContext<CommandSourceStack> ctx,
                                 EconomyServer economy) {
        java.nio.file.Path written = economy.exportCsv();
        if (written == null) {
            ctx.getSource().sendFailure(Component.translatable(Lang.ERROR_NOT_READY));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(Lang.EXPORT_DONE,
                economy.prices().table().size(), written.toString()), true);
        return 1;
    }
}
