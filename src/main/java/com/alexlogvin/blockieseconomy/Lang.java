package com.alexlogvin.blockieseconomy;

/**
 * Translation keys.
 *
 * <p>Every string a player sees goes through here rather than being written inline, so a
 * translator can cover the mod without touching code and nothing ships hardcoded English.
 */
public final class Lang {

    private static final String PREFIX = "blockies_economy.";
    private static final String CMD = PREFIX + "command.";
    private static final String ERR = PREFIX + "error.";

    private Lang() {
    }

    // ---- currency formatting ----
    public static final String CURRENCY_SYMBOL = PREFIX + "currency.symbol";
    public static final String SUFFIX_THOUSAND = PREFIX + "currency.suffix.thousand";
    public static final String SUFFIX_MILLION = PREFIX + "currency.suffix.million";
    public static final String SUFFIX_BILLION = PREFIX + "currency.suffix.billion";
    public static final String SUFFIX_TRILLION = PREFIX + "currency.suffix.trillion";
    public static final String GROUP_SEPARATOR = PREFIX + "currency.separator.group";
    public static final String DECIMAL_SEPARATOR = PREFIX + "currency.separator.decimal";

    // ---- /shop ----
    public static final String VERSION = CMD + "version";
    public static final String HELP_HEADER = CMD + "help.header";
    public static final String HELP_UI = CMD + "help.ui";
    public static final String HELP_BALANCE = CMD + "help.balance";
    public static final String HELP_BUY = CMD + "help.buy";
    public static final String HELP_SELL = CMD + "help.sell";
    public static final String HELP_PRICE = CMD + "help.price";
    public static final String HELP_TOP = CMD + "help.top";
    public static final String HELP_ADMIN_HEADER = CMD + "help.admin.header";
    public static final String HELP_ADMIN_BALANCE = CMD + "help.admin.balance";
    public static final String HELP_ADMIN_PRICE = CMD + "help.admin.price";
    public static final String HELP_ADMIN_REBUILD = CMD + "help.admin.rebuild";
    public static final String HELP_ADMIN_RELOAD = CMD + "help.admin.reload";
    public static final String HELP_ADMIN_DEBUG = CMD + "help.admin.debug";
    public static final String HELP_ADMIN_EXPORT = CMD + "help.admin.export";

    // ---- balance ----
    public static final String BALANCE_SELF = CMD + "balance.self";
    public static final String BALANCE_OTHER = CMD + "balance.other";
    public static final String BALANCE_SET = CMD + "balance.set";
    public static final String BALANCE_ADDED = CMD + "balance.added";
    public static final String BALANCE_REMOVED = CMD + "balance.removed";
    public static final String BALANCE_CONFIRM = CMD + "balance.confirm";

    // ---- trading ----
    public static final String BUY_SUCCESS = CMD + "buy.success";
    public static final String BUY_SUCCESS_DROPPED = CMD + "buy.success.dropped";
    public static final String SELL_SUCCESS = CMD + "sell.success";

    // ---- prices ----
    public static final String PRICE_INFO = CMD + "price.info";
    public static final String PRICE_SET = CMD + "price.set";
    public static final String PRICE_BLACKLISTED = CMD + "price.blacklisted";
    public static final String DEBUG_HEADER = CMD + "debug.header";
    public static final String DEBUG_SOURCE = CMD + "debug.source";
    public static final String DEBUG_VALUE = CMD + "debug.value";

    // ---- maintenance ----
    public static final String REBUILD_STARTED = CMD + "rebuild.started";
    public static final String REBUILD_DONE = CMD + "rebuild.done";
    public static final String RELOAD_DONE = CMD + "reload.done";
    public static final String EXPORT_DONE = CMD + "export.done";

    // ---- leaderboard ----
    public static final String TOP_HEADER = CMD + "top.header";
    public static final String TOP_ENTRY = CMD + "top.entry";
    public static final String TOP_EMPTY = CMD + "top.empty";

    // ---- advancements ----
    public static final String ADVANCEMENT_REWARD = PREFIX + "advancement.reward";

    // ---- errors ----
    public static final String ERROR_NOT_READY = ERR + "not_ready";
    public static final String ERROR_NOT_TRADEABLE = ERR + "not_tradeable";
    public static final String ERROR_UNKNOWN_ITEM = ERR + "unknown_item";
    public static final String ERROR_INSUFFICIENT_FUNDS = ERR + "insufficient_funds";
    public static final String ERROR_INSUFFICIENT_ITEMS = ERR + "insufficient_items";
    public static final String ERROR_NON_DEFAULT_COMPONENTS = ERR + "non_default_components";
    public static final String ERROR_RATE_LIMITED = ERR + "rate_limited";
    public static final String ERROR_INVALID_AMOUNT = ERR + "invalid_amount";
    public static final String ERROR_UNKNOWN_PLAYER = ERR + "unknown_player";
    public static final String ERROR_PLAYERS_ONLY = ERR + "players_only";
    public static final String ERROR_LEADERBOARD_DISABLED = ERR + "leaderboard_disabled";
    public static final String ERROR_CLIENT_REQUIRED = ERR + "client_required";
}
