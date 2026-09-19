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
 * <p>Shaped like a vanilla settings screen rather than like a panel of this mod's own
 * invention: the version's own background behind it, a centred title, one scrolling column
 * of settings, and Done at the bottom. Players already know how this works, and a mod
 * screen that looks like the game's is one less thing to learn.
 *
 * <p><b>One list, two sections.</b> Client settings first, then Server, each under its own
 * heading. Client is this player's own {@code client.toml}. Server edits {@code
 * server.toml}, which is the rules of whatever world is running, so its controls appear
 * only when this player owns that file — the title screen, where it is the file their next
 * single-player world will use, and single player. Connected to a remote server the
 * heading stays, with a line saying the settings live on the server, and no controls: the
 * file is on the server's disk and editing a local copy of it would change nothing.
 *
 * <p>Still a plain {@link Screen} rather than a vanilla {@code OptionsSubScreen}. That
 * class and its options list changed shape repeatedly across the versions this mod
 * targets, while {@link Screen}, {@link Button} and {@link EditBox} did not, so one source
 * file covers every loader and both Minecraft versions. The single call that does differ
 * is the background, which lives in {@link ScreenBackground}.
 */
public final class ConfigScreen extends Screen {

    /** Content column width, matching the 310-wide block vanilla's options screens use. */
    private static final int CONTENT_WIDTH = 310;
    private static final int CONTROL_WIDTH = 100;
    // Every line is exactly one row tall, headings included, and the view is snapped to a
    // whole number of rows below. That makes every scroll position land on a row boundary,
    // so a row is always entirely present or entirely absent - never a label with its
    // control clipped away, which is what a free-scrolling list did here.
    private static final int ROW_HEIGHT = 24;
    private static final int HEADING_HEIGHT = ROW_HEIGHT;
    private static final int CONTROL_HEIGHT = 20;

    /** Where the scrolling region starts and stops, leaving room for title and Done. */
    private static final int LIST_TOP = 32;
    private static final int LIST_BOTTOM_MARGIN = 40;

    private static final int COLOUR_LABEL = 0xFFFFFF;
    private static final int COLOUR_HEADING = 0xFFD98B;
    private static final int COLOUR_NOTE = 0xA0A0A0;
    private static final int COLOUR_SAVED = 0x60E060;
    private static final int COLOUR_CLAMPED = 0xFFD060;
    private static final int SCROLLBAR_WIDTH = 4;

    /** How long the "saved" line stays up, in client ticks. */
    private static final int SAVED_TICKS = 60;

    private final Screen parent;
    private final List<AbstractWidget> widgets = new ArrayList<AbstractWidget>();
    private final List<Entry> entries = new ArrayList<Entry>();

    private boolean clientDirty;
    private boolean serverDirty;
    private int savedFor;
    private int scroll;

    /**
     * True when a save had to be corrected on the way back in, so the screen can say so
     * rather than silently showing a different number than the player typed.
     */
    private boolean clamped;

    /**
     * The Save button, kept so {@link #tick} can re-enable it.
     *
     * <p>Needed because a value changes through an EditBox responder long after init()
     * decided whether the button was active. Without this the button stays greyed while
     * the field beside it plainly holds a new number, the same stale-widget bug the Sell
     * button in {@link ShopScreen} had, fixed the same way.
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

    private ClientConfig clientConfig() {
        ConfigManager manager = config();
        return manager == null ? null : manager.client();
    }

    private ServerConfig serverConfig() {
        ConfigManager manager = config();
        return manager == null ? null : manager.server();
    }

    // ---- the list --------------------------------------------------------------------

    /**
     * One line of the list: a heading, a plain note, or a setting with a control.
     *
     * <p>{@code contentY} is the line's position within the scrolling content, not on
     * screen. {@link #layoutScroll} turns one into the other, so scrolling moves every row
     * by changing a single number.
     */
    private static final class Entry {
        private final Component text;
        private final AbstractWidget control;
        private final int height;
        private final int colour;
        // Carried as its own flag rather than inferred from the height or a null control:
        // headings are now exactly one row tall, and the "settings live on the server"
        // note is a control-less row that is not a heading. Both tests would be wrong.
        private final boolean heading;
        private int contentY;

        Entry(Component text, AbstractWidget control, int height, int colour,
              boolean heading) {
            this.text = text;
            this.control = control;
            this.height = height;
            this.colour = colour;
            this.heading = heading;
        }
    }

    private int contentLeft() {
        return (width - CONTENT_WIDTH) / 2;
    }

    private int viewHeight() {
        int available = height - LIST_BOTTOM_MARGIN - LIST_TOP;
        // Rounded down to whole rows. See ROW_HEIGHT: this is what keeps every scroll
        // position on a row boundary.
        return Math.max(ROW_HEIGHT, available / ROW_HEIGHT * ROW_HEIGHT);
    }

    private int listBottom() {
        return LIST_TOP + viewHeight();
    }

    private int contentHeight() {
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            total += entries.get(i).height;
        }
        return total;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    // ---- lifecycle -------------------------------------------------------------------

    @Override
    protected void init() {
        // Cleared for the same reason ShopScreen clears its list: init runs again on every
        // window resize, and without this the screen draws two of everything afterwards.
        widgets.clear();
        entries.clear();
        saveButton = null;

        int x = contentLeft();

        buildClientSection(x);
        buildServerSection(x);

        // Assigned once the list is complete, because a row's place depends on the height
        // of everything above it.
        int y = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            entry.contentY = y;
            y += entry.height;
        }
        scroll = Math.min(scroll, maxScroll());

        int footerY = height - 28;
        if (serverEditable()) {
            // Save earns its own button here because saving has a visible answer: the
            // values are written, reloaded and read back, and a multiplier that broke the
            // arbitrage ceiling comes back corrected. Closing saves too, but then there is
            // nothing left to show the correction on.
            saveButton = Button.builder(Component.translatable(Lang.CONFIG_SAVE), button -> {
                save();
                refreshLayout();
            }).bounds(width / 2 - 154, footerY, 150, CONTROL_HEIGHT).build();
            saveButton.active = serverDirty;
            track(saveButton);
            track(Button.builder(Component.translatable(Lang.CONFIG_DONE), button -> onClose())
                    .bounds(width / 2 + 4, footerY, 150, CONTROL_HEIGHT).build());
        } else {
            track(Button.builder(Component.translatable(Lang.CONFIG_DONE), button -> onClose())
                    .bounds(width / 2 - 100, footerY, 200, CONTROL_HEIGHT).build());
        }

        layoutScroll();
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
        clearWidgets();
        init();
    }

    private void buildClientSection(int x) {
        heading(Lang.CONFIG_TAB_CLIENT);

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
        }).bounds(controlX(x), 0, CONTROL_WIDTH, CONTROL_HEIGHT).build();
        row(Lang.CONFIG_HUD_ANCHOR, anchor);

        row(Lang.CONFIG_HUD_OFFSET_X, intBox(x, c.offsetX(),
                value -> c.setOffset(value, c.offsetY())));
        row(Lang.CONFIG_HUD_OFFSET_Y, intBox(x, c.offsetY(),
                value -> c.setOffset(c.offsetX(), value)));

        row(Lang.CONFIG_HUD_VISIBLE, toggle(x, c.hudVisible(), value -> {
            c.setHudVisible(value);
            clientDirty = true;
        }));
        row(Lang.CONFIG_HUD_ICON, toggle(x, c.showCurrencyIcon(), value -> {
            c.setShowCurrencyIcon(value);
            clientDirty = true;
        }));
        row(Lang.CONFIG_SHOP_TOOLTIPS, toggle(x, c.tooltipPrices(), value -> {
            c.setTooltipPrices(value);
            clientDirty = true;
        }));
    }

    private void buildServerSection(int x) {
        heading(Lang.CONFIG_TAB_SERVER);

        if (!serverEditable()) {
            // The heading stays so the section is not simply missing, but there is nothing
            // to edit: this file lives on the server.
            entries.add(new Entry(Component.translatable(Lang.CONFIG_SERVER_REMOTE),
                    null, ROW_HEIGHT, COLOUR_NOTE, false));
            return;
        }

        ServerConfig s = serverConfig();
        if (s == null) {
            return;
        }

        row(Lang.CONFIG_SELL_MULTIPLIER, doubleBox(x, s.sellMultiplier(),
                s::setSellMultiplier));
        row(Lang.CONFIG_RECIPE_MULTIPLIER, doubleBox(x, s.defaultRecipeMultiplier(),
                s::setDefaultRecipeMultiplier));
        row(Lang.CONFIG_STARTING_BALANCE, longBox(x, s.startingBalance(),
                s::setStartingBalance));
        row(Lang.CONFIG_DEATH_PENALTY, longBox(x, s.deathPenalty(), s::setDeathPenalty));
        row(Lang.CONFIG_ADVANCEMENT_BASE, longBox(x, s.advancementBase(),
                s::setAdvancementBase));
        row(Lang.CONFIG_ADVANCEMENT_EXPONENT, doubleBox(x, s.advancementExponent(),
                s::setAdvancementExponent));
        row(Lang.CONFIG_TRANSACTION_LOG, toggle(x, s.transactionLog(), value -> {
            s.setTransactionLog(value);
            serverDirty = true;
        }));
        row(Lang.CONFIG_LEADERBOARD_PUBLIC, toggle(x, s.leaderboardPublic(), value -> {
            s.setLeaderboardPublic(value);
            serverDirty = true;
        }));
    }

    private void heading(String key) {
        entries.add(new Entry(Component.translatable(key), null, HEADING_HEIGHT,
                COLOUR_HEADING, true));
    }

    private void row(String labelKey, AbstractWidget control) {
        entries.add(new Entry(Component.translatable(labelKey), control, ROW_HEIGHT,
                COLOUR_LABEL, false));
        track(control);
    }

    private <T extends AbstractWidget> T track(T widget) {
        widgets.add(widget);
        return addWidget(widget);
    }

    // ---- scrolling -------------------------------------------------------------------

    /**
     * Puts every control where the current scroll position says it goes, and hides the
     * ones that have left the view.
     *
     * <p>Hiding matters as much as moving: an invisible widget takes no clicks, so a field
     * scrolled up behind the title cannot be typed into by clicking where it used to be.
     */
    private void layoutScroll() {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.control == null) {
                continue;
            }
            int y = LIST_TOP + entry.contentY - scroll;
            entry.control.setY(y + (ROW_HEIGHT - CONTROL_HEIGHT) / 2);
            entry.control.visible = y >= LIST_TOP - 2
                    && y + ROW_HEIGHT <= listBottom() + 2;
        }
    }

    private void scrollBy(int amount) {
        int before = scroll;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + amount));
        if (scroll != before) {
            layoutScroll();
        }
    }

    // Declared in both the 1.20.1 and the 1.20.5+ shapes, without @Override on either, so
    // one source file compiles against both. The version that is not the real override is
    // simply never called.
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollBy((int) (-delta * ROW_HEIGHT));
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
                                 double scrollY) {
        scrollBy((int) (-scrollY * ROW_HEIGHT));
        return true;
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

    private int controlX(int x) {
        return x + CONTENT_WIDTH - CONTROL_WIDTH;
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
        EditBox box = new EditBox(font, controlX(x), 0, CONTROL_WIDTH, CONTROL_HEIGHT,
                Component.empty());
        box.setMaxLength(20);
        // Set before the responder is attached, so loading a value does not mark the
        // screen dirty and light up Save on a screen nobody has edited.
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
        }).bounds(controlX(x), 0, CONTROL_WIDTH, CONTROL_HEIGHT).build();
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
            // what was typed. MultiplierTable enforces the arbitrage ceiling when the
            // table is built, not on the stored number — so a markup of 2.0 stays 2.0 in
            // the config while the economy quietly runs on 1.333.
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

        // Only when something was actually written. Saying "Saved" for merely opening the
        // screen would teach the player to ignore the one line that tells them their edit
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
        // This version's own settings-screen background, rather than a panel of this mod's
        // invention. Drawn here rather than delegated to Screen#render, which only calls it
        // from 1.20.5 onwards and would also draw widgets this screen positions itself.
        ScreenBackground.draw(this, graphics, mouseX, mouseY, partialTick);

        layoutScroll();

        graphics.drawCenteredString(font, title, width / 2, 14, COLOUR_LABEL);

        int left = contentLeft();
        // Clipped to the list area so a half-scrolled row is cut off cleanly instead of
        // spilling over the title and the buttons.
        graphics.enableScissor(left - 4, LIST_TOP, left + CONTENT_WIDTH + 4, listBottom());
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int y = LIST_TOP + entry.contentY - scroll;
            if (y + entry.height < LIST_TOP || y > listBottom()) {
                continue;
            }

            if (entry.heading) {
                graphics.drawString(font, entry.text, left, y + 8, entry.colour);
                // A rule under the heading, the way vanilla separates a settings group.
                graphics.fill(left, y + 20, left + CONTENT_WIDTH, y + 21, 0x40FFFFFF);
            } else {
                graphics.drawString(font, entry.text, left,
                        y + (ROW_HEIGHT - font.lineHeight) / 2, entry.colour);
            }

            if (entry.control != null && entry.control.visible) {
                entry.control.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.disableScissor();

        renderScrollbar(graphics, left);
        renderFooterNote(graphics);

        // The footer buttons sit outside the list, so they are drawn after the scissor is
        // lifted and are never clipped by it.
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget widget = widgets.get(i);
            if (!isInList(widget)) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private boolean isInList(AbstractWidget widget) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).control == widget) {
                return true;
            }
        }
        return false;
    }

    private void renderScrollbar(GuiGraphics graphics, int left) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = left + CONTENT_WIDTH + 6;
        int trackHeight = viewHeight();
        int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight());
        int thumbY = LIST_TOP + (trackHeight - thumbHeight) * scroll / max;

        graphics.fill(trackX, LIST_TOP, trackX + SCROLLBAR_WIDTH, listBottom(), 0x40000000);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight,
                0xA0FFFFFF);
    }

    private void renderFooterNote(GuiGraphics graphics) {
        int noteY = height - 40;
        if (clamped) {
            graphics.drawCenteredString(font, Component.translatable(Lang.CONFIG_CLAMPED),
                    width / 2, noteY, COLOUR_CLAMPED);
        } else if (savedFor > 0) {
            graphics.drawCenteredString(font, Component.translatable(Lang.CONFIG_SAVED),
                    width / 2, noteY, COLOUR_SAVED);
        }
    }
}
