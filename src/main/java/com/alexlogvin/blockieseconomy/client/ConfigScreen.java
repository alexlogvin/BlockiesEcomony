package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.EconomyServer;
import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.config.ConfigManager;
import com.alexlogvin.blockieseconomy.core.config.ClientConfig;
import com.alexlogvin.blockieseconomy.core.config.HudAnchor;
import com.alexlogvin.blockieseconomy.core.config.ServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen behind the Config button in the loader's mod list.
 *
 * <p>Built the same way as {@link ShopScreen}, and for the same reasons: a plain
 * {@link Screen} that draws its own background and its own widgets, because
 * {@code Screen#render} gained a background call in 1.20.5 and {@code Screen.renderables}
 * is private on 1.20.1. One source file covers every loader and both Minecraft versions;
 * only the few lines that hand this screen to the mod list differ per loader.
 *
 * <p><b>Two sections, and when each is offered.</b> The client section is always there —
 * it is this player's own {@code client.toml} and nobody else's business. The server
 * section edits {@code server.toml}, which is the rules of whatever world is running, so
 * it is offered only when this player owns that file: on the title screen (where it is
 * the file their next single-player world will use) and in single player. Connected to a
 * remote server the tab is disabled rather than removed, with a line saying why — the file
 * is on the server's disk, unreachable from here, and letting a player edit a copy of it
 * would change settings that silently do nothing.
 */
public final class ConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    // Sized so the tallest tab (eight server rows) comes to 236, which fits inside the
    // 240-line logical screen Minecraft guarantees: its auto GUI scale never picks a
    // factor that leaves less than 320x240 to draw in. Adding a ninth setting means
    // giving this screen a scrolling list, not shaving another two pixels off a row.
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER = 38;
    private static final int FOOTER = 38;
    private static final int VALUE_WIDTH = 96;

    private static final int COLOUR_TITLE = 0xFFFFFF;
    private static final int COLOUR_LABEL = 0xC0C0C0;
    private static final int COLOUR_NOTE = 0xA0A0A0;
    private static final int COLOUR_SAVED = 0x60E060;
    private static final int COLOUR_CLAMPED = 0xFFD060;
    private static final int COLOUR_PANEL = 0xF0100010;
    private static final int COLOUR_BORDER = 0x50FFFFFF;

    /** How long the "saved" line stays up, in client ticks. */
    private static final int SAVED_TICKS = 60;

    private final Screen parent;
    private final List<AbstractWidget> widgets = new ArrayList<AbstractWidget>();
    private final List<Row> rows = new ArrayList<Row>();

    private boolean serverTab;
    private boolean clientDirty;
    private boolean serverDirty;
    private int savedFor;

    /**
     * True when a save had to be corrected on the way back in, so the screen can say so
     * rather than silently showing a different number than the player typed.
     */
    private boolean clamped;

    /**
     * The Save button, kept so { #tick} can re-enable it.
     *
     * <p>Needed because a value changes through an EditBox responder long after init()
     * decided whether the button was active. Without this the button stays greyed while
     * the field beside it plainly holds a new number, the same stale-widget bug the Sell
     * button in { ShopScreen} had, fixed the same way.
     */
    private Button saveButton;

    public ConfigScreen(Screen parent) {
        super(Component.translatable(Lang.CONFIG_TITLE));
        this.parent = parent;
    }

    // ---- what is editable ------------------------------------------------------------

    /**
     * True when {@code server.toml} on this disk is the one that matters.
     *
     * <p>{@code level == null} covers the title screen, where there is no world yet but
     * the file is still the player's own.
     */
    private boolean serverEditable() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.hasSingleplayerServer();
    }

    private static ConfigManager config() {
        EconomyServer economy = BlockiesEconomy.server();
        return economy == null ? null : economy.config();
    }

    // ---- rows ------------------------------------------------------------------------

    /** One setting: a label, a widget that edits it, and how to read the widget back. */
    private static final class Row {
        private final String labelKey;
        private final AbstractWidget widget;

        Row(String labelKey, AbstractWidget widget) {
            this.labelKey = labelKey;
            this.widget = widget;
        }
    }

    private int panelHeight() {
        return HEADER + Math.max(rows.size(), 1) * ROW_HEIGHT + FOOTER;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, (height - panelHeight()) / 2);
    }

    // ---- lifecycle -------------------------------------------------------------------

    @Override
    protected void init() {
        // Cleared for the same reason ShopScreen clears its list: init runs again on every
        // window resize, and without this the screen draws two of everything afterwards.
        widgets.clear();
        rows.clear();
        saveButton = null;

        if (serverTab && !serverEditable()) {
            serverTab = false;
        }

        int x = left();

        // Rows are built before anything is positioned, because panelHeight() — and so
        // top(), and so every y on this screen — is a function of how many rows there are.
        // Reading top() while the list was still empty is what put the labels a hundred
        // pixels above their own fields the first time this screen was drawn: init() laid
        // out against an empty list and render() drew against a full one.
        if (serverTab) {
            buildServerRows(x);
        } else {
            buildClientRows(x);
        }

        int y = top();

        Button clientTab = Button.builder(Component.translatable(Lang.CONFIG_TAB_CLIENT),
                button -> switchTo(false)).bounds(x + 8, y + 18, 100, 18).build();
        clientTab.active = serverTab;
        track(clientTab);

        Button serverTabButton = Button.builder(Component.translatable(Lang.CONFIG_TAB_SERVER),
                button -> switchTo(true)).bounds(x + 112, y + 18, 100, 18).build();
        // Disabled rather than hidden when connected to a server: a player who knows the
        // tab exists should see that it is unavailable here, not wonder where it went.
        serverTabButton.active = serverEditable() && !serverTab;
        track(serverTabButton);

        layoutRows(y);

        // The footer band is FOOTER tall below the last row: a 9px note, then a 20px
        // button row, laid out from the bottom so both stay inside the panel border.
        int footerY = y + panelHeight() - 24;
        if (serverTab) {
            // The server tab gets its own Save, because saving there has a visible answer:
            // the values are written, reloaded, and read back, and a multiplier that broke
            // the arbitrage ceiling comes back corrected. Closing the screen saves too, but
            // then there is nothing left to show the correction on.
            saveButton = Button.builder(Component.translatable(Lang.CONFIG_SAVE), button -> {
                save();
                refreshLayout();
            }).bounds(x + PANEL_WIDTH / 2 - 104, footerY, 100, 20).build();
            saveButton.active = serverDirty;
            track(saveButton);
            track(Button.builder(Component.translatable(Lang.CONFIG_DONE), button -> onClose())
                    .bounds(x + PANEL_WIDTH / 2 + 4, footerY, 100, 20).build());
        } else {
            track(Button.builder(Component.translatable(Lang.CONFIG_DONE), button -> onClose())
                    .bounds(x + PANEL_WIDTH / 2 - 50, footerY, 100, 20).build());
        }
    }

    private void switchTo(boolean server) {
        // Saved before the widgets holding the values are thrown away by init().
        save();
        serverTab = server;
        refreshLayout();
    }

    /**
     * Rebuilds every widget from the config as it currently stands.
     *
     * <p>Needed after a server save, not just for looks: reloading replaces the
     * {@link ServerConfig} instance, so the lambdas the old widgets captured would be
     * writing into an object nothing reads any more.
     *
     * <p>Named for what it does rather than {@code rebuildWidgets}, which is taken by
     * {@link Screen} and would be an accidental override.
     */
    private void refreshLayout() {
        // clearWidgets is the superclass's input registry; `widgets` is the draw list that
        // init() refills. Both have to go, or input and drawing disagree about what is on
        // screen.
        clearWidgets();
        init();
    }

    private void buildClientRows(int x) {
        ClientConfig c = clientConfig();
        if (c == null) {
            return;
        }

        // Shown as the enum name, the same token that appears in client.toml. A player who
        // edits one and then looks at the other sees the same word, which is worth more
        // here than a translated phrase that matches nothing in the file.
        Button anchor = Button.builder(Component.literal(c.anchor().name()), button -> {
            HudAnchor[] all = HudAnchor.values();
            HudAnchor next = all[(c.anchor().ordinal() + 1) % all.length];
            c.setAnchor(next);
            button.setMessage(Component.literal(next.name()));
            clientDirty = true;
        }).bounds(valueX(x), 0, VALUE_WIDTH, 18).build();
        addRow(Lang.CONFIG_HUD_ANCHOR, anchor);

        addRow(Lang.CONFIG_HUD_OFFSET_X, intBox(x, c.offsetX(),
                value -> c.setOffset(value, c.offsetY())));
        addRow(Lang.CONFIG_HUD_OFFSET_Y, intBox(x, c.offsetY(),
                value -> c.setOffset(c.offsetX(), value)));

        addRow(Lang.CONFIG_HUD_VISIBLE, toggle(x, c.hudVisible(), value -> {
            c.setHudVisible(value);
            clientDirty = true;
        }));
        addRow(Lang.CONFIG_HUD_ICON, toggle(x, c.showCurrencyIcon(), value -> {
            c.setShowCurrencyIcon(value);
            clientDirty = true;
        }));
        addRow(Lang.CONFIG_SHOP_TOOLTIPS, toggle(x, c.tooltipPrices(), value -> {
            c.setTooltipPrices(value);
            clientDirty = true;
        }));

    }

    private void buildServerRows(int x) {
        ServerConfig s = serverConfig();
        if (s == null) {
            return;
        }

        addRow(Lang.CONFIG_SELL_MULTIPLIER, doubleBox(x, s.sellMultiplier(),
                s::setSellMultiplier));
        addRow(Lang.CONFIG_RECIPE_MULTIPLIER, doubleBox(x, s.defaultRecipeMultiplier(),
                s::setDefaultRecipeMultiplier));
        addRow(Lang.CONFIG_STARTING_BALANCE, longBox(x, s.startingBalance(),
                s::setStartingBalance));
        addRow(Lang.CONFIG_DEATH_PENALTY, longBox(x, s.deathPenalty(), s::setDeathPenalty));
        addRow(Lang.CONFIG_ADVANCEMENT_BASE, longBox(x, s.advancementBase(),
                s::setAdvancementBase));
        addRow(Lang.CONFIG_ADVANCEMENT_EXPONENT, doubleBox(x, s.advancementExponent(),
                s::setAdvancementExponent));
        addRow(Lang.CONFIG_TRANSACTION_LOG, toggle(x, s.transactionLog(), value -> {
            s.setTransactionLog(value);
            serverDirty = true;
        }));
        addRow(Lang.CONFIG_LEADERBOARD_PUBLIC, toggle(x, s.leaderboardPublic(), value -> {
            s.setLeaderboardPublic(value);
            serverDirty = true;
        }));

    }

    // ---- widget factories ------------------------------------------------------------

    private interface IntSetter {
        void set(int value);
    }

    private interface LongSetter {
        void set(long value);
    }

    private interface DoubleSetter {
        void set(double value);
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private int valueX(int x) {
        return x + PANEL_WIDTH - 8 - VALUE_WIDTH;
    }

    /**
     * A number field that writes through on every keystroke, and ignores anything that is
     * not a number.
     *
     * <p>Ignoring rather than reverting: a player clearing the field to retype it passes
     * through the empty string, and snapping it back to the old value mid-edit would make
     * the field unusable. The last parseable thing they typed is what gets saved.
     */
    private EditBox numberBox(int x, String initial, java.util.function.Consumer<String> onEdit) {
        EditBox box = new EditBox(font, valueX(x), 0, VALUE_WIDTH, 18, Component.empty());
        box.setMaxLength(20);
        box.setValue(initial);
        box.setResponder(onEdit);
        return box;
    }

    private EditBox intBox(int x, int initial, IntSetter setter) {
        return numberBox(x, Integer.toString(initial), text -> {
            try {
                setter.set(Integer.parseInt(text.trim()));
                clientDirty = true;
            } catch (NumberFormatException ignored) {
                // Mid-edit, not a value yet.
            }
        });
    }

    private EditBox longBox(int x, long initial, LongSetter setter) {
        return numberBox(x, Long.toString(initial), text -> {
            try {
                setter.set(Long.parseLong(text.trim()));
                serverDirty = true;
            } catch (NumberFormatException ignored) {
                // Mid-edit, not a value yet.
            }
        });
    }

    private EditBox doubleBox(int x, double initial, DoubleSetter setter) {
        return numberBox(x, trim(initial), text -> {
            try {
                double value = Double.parseDouble(text.trim());
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    setter.set(value);
                    serverDirty = true;
                }
            } catch (NumberFormatException ignored) {
                // Mid-edit, not a value yet.
            }
        });
    }

    private Button toggle(int x, boolean initial, BoolSetter setter) {
        final boolean[] state = {initial};
        return Button.builder(onOff(initial), button -> {
            state[0] = !state[0];
            button.setMessage(onOff(state[0]));
            setter.set(state[0]);
        }).bounds(valueX(x), 0, VALUE_WIDTH, 18).build();
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? Lang.CONFIG_ON : Lang.CONFIG_OFF);
    }

    /** {@code 0.75} rather than {@code 0.75000000000000004}, without a locale's comma. */
    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        while (text.endsWith("0") && !text.endsWith(".0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void addRow(String labelKey, AbstractWidget widget) {
        rows.add(new Row(labelKey, widget));
    }

    private void layoutRows(int y) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.widget.setY(y + HEADER + i * ROW_HEIGHT);
            track(row.widget);
        }
    }

    private <T extends AbstractWidget> T track(T widget) {
        widgets.add(widget);
        return addWidget(widget);
    }

    private ClientConfig clientConfig() {
        ConfigManager manager = config();
        return manager == null ? null : manager.client();
    }

    private ServerConfig serverConfig() {
        ConfigManager manager = config();
        return manager == null ? null : manager.server();
    }

    // ---- saving ----------------------------------------------------------------------

    /**
     * Writes whichever files actually changed.
     *
     * <p>Only the changed ones, because saving {@code server.toml} regenerates it and
     * drops an admin's own comments — see {@link ConfigManager#saveServer}. A player who
     * opened the screen, looked, and closed it should not lose anything.
     */
    private void save() {
        ConfigManager manager = config();
        if (manager == null) {
            return;
        }

        boolean wrote = false;

        if (clientDirty) {
            manager.saveClient();
            clientDirty = false;
            wrote = true;
        }

        if (serverDirty && serverEditable()) {
            wrote = true;
            manager.saveServer();
            serverDirty = false;
            BlockiesEconomy.server().reloadConfig();

            // Ask the authority what the value actually came out as, rather than trusting
            // what was typed. MultiplierTable owns the arbitrage ceiling, and it enforces
            // it when the table is built, not on the stored number — so a markup of 2.0
            // stays 2.0 in the config while the economy quietly runs on 1.333.
            //
            // Writing the effective value back is the point. A config screen showing a
            // figure the economy is not using is the same failure as a shop quoting a
            // price it will not pay, and this mod has had that bug once already.
            ServerConfig saved = manager.server();
            double effective = saved.multiplierTable().defaultMultiplier();
            clamped = effective != saved.defaultRecipeMultiplier();
            if (clamped) {
                saved.setDefaultRecipeMultiplier(effective);
                manager.saveServer();
            }

            // Prices are derived from these, and clients hold a synced copy. Without a
            // rebuild the shop would keep quoting the old economy until a restart.
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSingleplayerServer() != null) {
                BlockiesEconomy.server().rebuildPrices(mc.getSingleplayerServer(), null);
            }
        }

        // Only when something was actually written. Saying "Saved" for merely switching
        // tabs would teach the player to ignore the one line that tells them their edit
        // landed.
        if (wrote) {
            savedFor = SAVED_TICKS;
        }
    }

    @Override
    public void tick() {
        if (savedFor > 0) {
            savedFor--;
        }
        if (saveButton != null) {
            saveButton.active = serverDirty;
        }
    }

    @Override
    public void onClose() {
        save();
        Minecraft.getInstance().setScreen(parent);
    }

    // ---- rendering -------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Drawn by hand for the same reason as ShopScreen: Screen#render gained a
        // background call in 1.20.5 that would cover the panel, and renderBackground's
        // signature changed with it.
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int x = left();
        int y = top();
        panel(graphics, x, y, PANEL_WIDTH, panelHeight());

        graphics.drawString(font, title, x + 8, y + 8, COLOUR_TITLE);

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            graphics.drawString(font, Component.translatable(row.labelKey),
                    x + 8, y + HEADER + i * ROW_HEIGHT + 5, COLOUR_LABEL);
        }

        for (int i = 0; i < widgets.size(); i++) {
            widgets.get(i).render(graphics, mouseX, mouseY, partialTick);
        }

        renderFooterNote(graphics, x, y);
    }

    private void renderFooterNote(GuiGraphics graphics, int x, int y) {
        int noteY = y + panelHeight() - 36;

        if (!serverEditable()) {
            Component note = Component.translatable(Lang.CONFIG_SERVER_REMOTE);
            graphics.drawString(font, note,
                    x + PANEL_WIDTH / 2 - font.width(note) / 2, noteY, COLOUR_NOTE);
            return;
        }
        if (clamped) {
            Component note = Component.translatable(Lang.CONFIG_CLAMPED);
            graphics.drawString(font, note,
                    x + PANEL_WIDTH / 2 - font.width(note) / 2, noteY, COLOUR_CLAMPED);
            return;
        }
        if (savedFor > 0) {
            Component note = Component.translatable(Lang.CONFIG_SAVED);
            graphics.drawString(font, note,
                    x + PANEL_WIDTH / 2 - font.width(note) / 2, noteY, COLOUR_SAVED);
        }
    }

    private static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOUR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOUR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOUR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOUR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOUR_BORDER);
    }
}
