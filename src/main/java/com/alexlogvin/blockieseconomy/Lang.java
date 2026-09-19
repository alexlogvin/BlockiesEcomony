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

    // ---- shop screen ----
    public static final String SHOP_TITLE = PREFIX + "shop.title";
    public static final String SHOP_SEARCH = PREFIX + "shop.search";
    public static final String SHOP_AMOUNT = PREFIX + "shop.amount";
    public static final String SHOP_BUY = PREFIX + "shop.buy";
    public static final String SHOP_SELL = PREFIX + "shop.sell";
    public static final String SHOP_MAX_BUY = PREFIX + "shop.max_buy";
    public static final String SHOP_MAX_SELL = PREFIX + "shop.max_sell";
    public static final String SHOP_UNIT_BUY = PREFIX + "shop.unit_buy";
    public static final String SHOP_UNIT_SELL = PREFIX + "shop.unit_sell";
    public static final String SHOP_NO_SELECTION = PREFIX + "shop.no_selection";
    public static final String SHOP_NO_MATCHES = PREFIX + "shop.no_matches";
    public static final String SHOP_NOT_CONNECTED = PREFIX + "shop.not_connected";
    public static final String SHOP_FILTER_ALL = PREFIX + "shop.filter.all";
    public static final String SHOP_SORT_ID = PREFIX + "shop.sort.id";
    public static final String SHOP_SORT_NAME = PREFIX + "shop.sort.name";
    public static final String SHOP_SORT_PRICE_ASC = PREFIX + "shop.sort.price_asc";
    public static final String SHOP_SORT_PRICE_DESC = PREFIX + "shop.sort.price_desc";
    public static final String SHOP_PIN_HINT = PREFIX + "shop.pin_hint";
    public static final String SHOP_RESULT_BOUGHT = PREFIX + "shop.result.bought";
    public static final String SHOP_RESULT_SOLD = PREFIX + "shop.result.sold";
    public static final String SHOP_RESULT_DROPPED = PREFIX + "shop.result.dropped";

    // ---- config screen ----
    public static final String CONFIG_TITLE = PREFIX + "config.title";
    public static final String CONFIG_TAB_CLIENT = PREFIX + "config.tab.client";
    public static final String CONFIG_TAB_SERVER = PREFIX + "config.tab.server";
    public static final String CONFIG_SERVER_REMOTE = PREFIX + "config.server_remote";
    public static final String CONFIG_SAVED = PREFIX + "config.saved";
    public static final String CONFIG_CLAMPED = PREFIX + "config.clamped";
    public static final String CONFIG_DONE = PREFIX + "config.done";
    public static final String CONFIG_SAVE = PREFIX + "config.save";

    public static final String CONFIG_HUD_ANCHOR = PREFIX + "config.hud.anchor";
    public static final String CONFIG_HUD_OFFSET_X = PREFIX + "config.hud.offset_x";
    public static final String CONFIG_HUD_OFFSET_Y = PREFIX + "config.hud.offset_y";
    public static final String CONFIG_HUD_VISIBLE = PREFIX + "config.hud.visible";
    public static final String CONFIG_HUD_ICON = PREFIX + "config.hud.icon";
    public static final String CONFIG_SHOP_TOOLTIPS = PREFIX + "config.shop.tooltips";

    public static final String CONFIG_SELL_MULTIPLIER = PREFIX + "config.server.sell_multiplier";
    public static final String CONFIG_RECIPE_MULTIPLIER =
            PREFIX + "config.server.recipe_multiplier";
    public static final String CONFIG_STARTING_BALANCE = PREFIX + "config.server.starting_balance";
    public static final String CONFIG_DEATH_PENALTY = PREFIX + "config.server.death_penalty";
    public static final String CONFIG_ADVANCEMENT_BASE = PREFIX + "config.server.advancement_base";
    public static final String CONFIG_ADVANCEMENT_EXPONENT =
            PREFIX + "config.server.advancement_exponent";
    public static final String CONFIG_TRANSACTION_LOG = PREFIX + "config.server.transaction_log";
    public static final String CONFIG_LEADERBOARD_PUBLIC =
            PREFIX + "config.server.leaderboard_public";

    public static final String CONFIG_ON = PREFIX + "config.on";
    public static final String CONFIG_OFF = PREFIX + "config.off";

    // ---- tooltips ----
    public static final String TOOLTIP_PRICE = PREFIX + "tooltip.price";

    // ---- keybind ----
    public static final String KEY_OPEN_SHOP = "key.blockies_economy.open_shop";
    public static final String KEY_CATEGORY = "key.categories.blockies_economy";

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
