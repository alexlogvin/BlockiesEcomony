package com.alexlogvin.blockieseconomy.core.config;

import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import com.alexlogvin.blockieseconomy.core.toml.TomlEntry;
import com.alexlogvin.blockieseconomy.core.toml.TomlTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** client.toml — HUD placement and appearance. Purely cosmetic; the server ignores it. */
public final class ClientConfig {

    public static final String TABLE_HUD = "hud";
    public static final String TABLE_SHOP = "shop";

    private HudAnchor anchor = HudAnchor.BOTTOM_RIGHT;
    private int offsetX = 0;
    private int offsetY = 0;
    private boolean hudVisible = true;
    private boolean showCurrencyIcon = true;
    private boolean tooltipPrices = true;
    private final List<String> favorites = new ArrayList<String>();

    public HudAnchor anchor() {
        return anchor;
    }

    public void setAnchor(HudAnchor anchor) {
        this.anchor = anchor;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public void setOffset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public boolean hudVisible() {
        return hudVisible;
    }

    public void setHudVisible(boolean visible) {
        this.hudVisible = visible;
    }

    /** True to draw the mod's coin icon as the currency symbol, false for a plain B. */
    public boolean showCurrencyIcon() {
        return showCurrencyIcon;
    }

    public void setShowCurrencyIcon(boolean show) {
        this.showCurrencyIcon = show;
    }

    /**
     * True to add a buy/sell line to item tooltips.
     *
     * <p>Covers every recipe viewer at once, because JEI, REI and EMI all render the
     * vanilla tooltip. That makes it the one integration that needs no dependency on any
     * of them and cannot break when one of them changes its API.
     */
    public boolean tooltipPrices() {
        return tooltipPrices;
    }

    public void setTooltipPrices(boolean show) {
        this.tooltipPrices = show;
    }

    /** Item ids the player pinned in the shop. */
    public List<String> favorites() {
        return favorites;
    }

    public static ClientConfig fromToml(TomlDocument doc) {
        ClientConfig c = new ClientConfig();

        c.anchor = HudAnchor.parse(
                doc.getString(TABLE_HUD, "anchor", c.anchor.name()), HudAnchor.BOTTOM_RIGHT);
        c.offsetX = (int) doc.getLong(TABLE_HUD, "offset_x", c.offsetX);
        c.offsetY = (int) doc.getLong(TABLE_HUD, "offset_y", c.offsetY);
        c.hudVisible = doc.getBoolean(TABLE_HUD, "visible", c.hudVisible);
        c.showCurrencyIcon = doc.getBoolean(TABLE_HUD, "show_currency_icon", c.showCurrencyIcon);
        c.tooltipPrices = doc.getBoolean(TABLE_SHOP, "tooltip_prices", c.tooltipPrices);

        TomlValue favorites = doc.value(TABLE_SHOP, "favorites");
        if (favorites != null && favorites.kind() == TomlValue.Kind.ARRAY) {
            List<TomlValue> items = favorites.asArray();
            for (int i = 0; i < items.size(); i++) {
                c.favorites.add(items.get(i).asString());
            }
        }

        return c;
    }

    public TomlDocument toToml() {
        TomlDocument doc = new TomlDocument();
        doc.headerComments().addAll(Arrays.asList(
                "Blockies Economy - client configuration.",
                "",
                "Affects only what you see. Prices and balances come from the server."));

        TomlTable hud = doc.table(TABLE_HUD);
        hud.comments().add("The balance readout drawn over the game world.");

        put(hud, "anchor", TomlValue.of(anchor.name()),
                "Where the balance sits. One of:",
                "  TOP_LEFT     TOP_CENTER     TOP_RIGHT",
                "  MIDDLE_LEFT  MIDDLE_CENTER  MIDDLE_RIGHT",
                "  BOTTOM_LEFT  BOTTOM_CENTER  BOTTOM_RIGHT");
        put(hud, "offset_x", TomlValue.of(offsetX),
                "Nudge from the anchor, in scaled pixels. Positive moves right.");
        put(hud, "offset_y", TomlValue.of(offsetY),
                "Nudge from the anchor, in scaled pixels. Positive moves down.");
        put(hud, "visible", TomlValue.of(hudVisible),
                "Show the balance at all. It hides itself automatically whenever a",
                "screen is open or the HUD is hidden with F1.");
        put(hud, "show_currency_icon", TomlValue.of(showCurrencyIcon),
                "Draw the coin icon as the currency symbol. False uses the letter B.");

        TomlTable shop = doc.table(TABLE_SHOP);
        shop.comments().add("Shop screen preferences.");

        put(shop, "tooltip_prices", TomlValue.of(tooltipPrices),
                "Add a buy/sell line to item tooltips, in your inventory and in any recipe",
                "viewer. JEI, REI and EMI all draw the vanilla tooltip, so this works in all",
                "three without needing any of them installed.");

        List<TomlValue> favoriteValues = new ArrayList<TomlValue>();
        for (int i = 0; i < favorites.size(); i++) {
            favoriteValues.add(TomlValue.of(favorites.get(i)));
        }
        put(shop, "favorites", TomlValue.ofArray(favoriteValues),
                "Items pinned to the top of the grid. Managed by right-clicking in the",
                "shop; editing by hand works too.");

        return doc;
    }

    private static void put(TomlTable table, String key, TomlValue value, String... comments) {
        TomlEntry entry = table.put(key, value);
        for (int i = 0; i < comments.length; i++) {
            entry.comments().add(comments[i]);
        }
    }
}
