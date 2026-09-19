package com.alexlogvin.blockieseconomy.client;

import com.alexlogvin.blockieseconomy.BlockiesEconomy;
import com.alexlogvin.blockieseconomy.Lang;
import com.alexlogvin.blockieseconomy.core.net.ShopSnapshot;
import com.alexlogvin.blockieseconomy.economy.TradeOutcome;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The shop.
 *
 * <p>A plain {@link Screen} rather than an {@code AbstractContainerMenu}, deliberately.
 * Nothing here moves items through slots — a purchase is a request the server validates and
 * fulfils — so a menu would buy nothing but the registration divergence between Fabric and
 * NeoForge, plus a synced container the client could be tempted to trust.
 *
 * <p>Everything shown is display state from {@link ClientShopState}. The totals under the
 * buttons are what the player should expect to pay; the server recomputes all of it before
 * a single Blockie moves.
 */
public final class ShopScreen extends Screen {

    // ---- layout ----------------------------------------------------------------------

    private static final int PANEL_WIDTH = 340;
    private static final int CELL_WIDTH = 35;
    private static final int CELL_HEIGHT = 30;
    private static final int COLUMNS = 9;
    private static final int MIN_ROWS = 2;

    /** Above this the grid stops growing; a taller window gets whitespace, not 40 rows. */
    private static final int MAX_ROWS = 6;
    private static final int DETAIL_HEIGHT = 92;
    private static final int GRID_TOP_OFFSET = 42;
    private static final int SCROLLBAR_WIDTH = 6;

    private static final int COLOUR_TITLE = 0xFFFFFF;
    private static final int COLOUR_LABEL = 0xA0A0A0;
    private static final int COLOUR_BUY = 0xFF6060;
    private static final int COLOUR_SELL = 0x60E060;
    private static final int COLOUR_PANEL = 0xF0100010;
    private static final int COLOUR_BORDER = 0x50FFFFFF;

    /** The coin beside the balance in the header. Sized to sit on one line of text. */
    private static final int BALANCE_ICON_SIZE = 10;
    private static final int BALANCE_ICON_GAP = 3;

    /**
     * How far below a line's top the visible middle of its text is.
     *
     * <p>Minecraft's glyphs occupy the first seven rows of a nine-pixel line, leaving the
     * last two for descenders. Anything centred against the line box therefore sits low
     * beside the text; centring against this instead lines it up with what the eye sees.
     */
    private static final int TEXT_INK_CENTRE = 3;

    /** Matches the server's own cap, so the screen cannot ask for what it would refuse. */
    private static final int MAX_TRADE = 10_000;

    // ---- sorting ---------------------------------------------------------------------

    /**
     * Sort orders, starting at id order.
     *
     * <p>Id order is first because it is what a recipe viewer shows: items of one mod stay
     * together, and a player who already knows roughly where something sits in JEI finds
     * it in the same neighbourhood here.
     */
    private enum Sort {
        ID(Lang.SHOP_SORT_ID),
        NAME(Lang.SHOP_SORT_NAME),
        PRICE_ASC(Lang.SHOP_SORT_PRICE_ASC),
        PRICE_DESC(Lang.SHOP_SORT_PRICE_DESC);

        private final String key;

        Sort(String key) {
            this.key = key;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // ---- state -----------------------------------------------------------------------

    private final List<Entry> visible = new ArrayList<Entry>();

    /**
     * The widgets, in draw order.
     *
     * <p>Kept here rather than read back from {@code Screen.renderables}, which is private
     * on 1.20.1 and protected later. This screen draws its widgets itself — see
     * {@link #render} for why it cannot let the superclass do it.
     */
    private final List<AbstractWidget> widgets = new ArrayList<AbstractWidget>();
    private final List<String> namespaces = new ArrayList<String>();

    private Sort sort = Sort.ID;
    private int namespaceIndex;
    private String search = "";
    private String selected;
    private int amount = 1;
    private int scrollRow;
    private int rows = 4;
    /** The last trade outcome, or null when nothing has happened since the last click. */
    private Component status;

    private EditBox searchBox;
    private EditBox amountBox;
    private Button sortButton;
    private Button namespaceButton;
    private Button buyButton;
    private Button sellButton;

    public ShopScreen() {
        super(Component.translatable(Lang.SHOP_TITLE));
    }

    /** One row of the filtered list: an item id resolved to a stack once, not per frame. */
    private static final class Entry {
        private final String itemId;
        private final ItemStack stack;
        private final String displayName;
        private final long buy;
        private final long sell;

        Entry(String itemId, ItemStack stack, long buy, long sell) {
            this.itemId = itemId;
            this.stack = stack;
            this.displayName = stack.getHoverName().getString();
            this.buy = buy;
            this.sell = sell;
        }
    }

    // ---- geometry --------------------------------------------------------------------

    private int panelHeight() {
        return GRID_TOP_OFFSET + rows * CELL_HEIGHT + 6 + DETAIL_HEIGHT;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int gridTop() {
        return top() + GRID_TOP_OFFSET;
    }

    private int detailTop() {
        return gridTop() + rows * CELL_HEIGHT + 6;
    }

    // ---- lifecycle -------------------------------------------------------------------

    @Override
    protected void init() {
        // Rows scale with the window rather than being fixed: a player on a small window or
        // a large GUI scale would otherwise get a panel taller than their screen.
        int available = height - GRID_TOP_OFFSET - DETAIL_HEIGHT - 20;
        rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, available / CELL_HEIGHT));

        // init runs again on every window resize, and the superclass clears its own widget
        // registry before it does. This list has to be cleared with it or the screen draws
        // two of everything after the first resize.
        widgets.clear();

        ClientNetwork.setResultListener(this::onTradeResult);
        rebuildNamespaces();

        int x = left();
        int y = top();

        searchBox = new EditBox(font, x + 8, y + 20, 150, 16,
                Component.translatable(Lang.SHOP_SEARCH));
        searchBox.setHint(Component.translatable(Lang.SHOP_SEARCH));
        searchBox.setMaxLength(64);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value.toLowerCase(Locale.ROOT).trim();
            scrollRow = 0;
            refresh();
        });
        track(searchBox);

        sortButton = Button.builder(Component.empty(), button -> {
            sort = sort.next();
            updateButtonLabels();
            refresh();
        }).bounds(x + 162, y + 20, 80, 16).build();
        track(sortButton);

        namespaceButton = Button.builder(Component.empty(), button -> {
            namespaceIndex = (namespaceIndex + 1) % Math.max(1, namespaces.size());
            scrollRow = 0;
            updateButtonLabels();
            refresh();
        }).bounds(x + 246, y + 20, 86, 16).build();
        track(namespaceButton);

        buildDetailWidgets(x, detailTop());
        updateButtonLabels();
        refresh();
    }

    private void buildDetailWidgets(int x, int y) {
        amountBox = new EditBox(font, x + 214, y + 6, 44, 16,
                Component.translatable(Lang.SHOP_AMOUNT));
        amountBox.setMaxLength(5);
        amountBox.setValue(String.valueOf(amount));
        amountBox.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,5}"));
        amountBox.setResponder(value -> {
            amount = parseAmount(value);
            updateButtonLabels();
        });
        track(amountBox);

        track(Button.builder(Component.literal("-"),
                b -> setAmount(amount - 1)).bounds(x + 196, y + 6, 16, 16).build());
        track(Button.builder(Component.literal("+"),
                b -> setAmount(amount + 1)).bounds(x + 260, y + 6, 16, 16).build());
        track(Button.builder(Component.literal("1x"),
                b -> setAmount(1)).bounds(x + 280, y + 6, 22, 16).build());
        track(Button.builder(Component.literal("64x"),
                b -> setAmount(64)).bounds(x + 304, y + 6, 28, 16).build());

        track(Button.builder(Component.translatable(Lang.SHOP_MAX_BUY),
                b -> setAmount(maxAffordable())).bounds(x + 196, y + 26, 66, 16).build());
        track(Button.builder(Component.translatable(Lang.SHOP_MAX_SELL),
                b -> setAmount(Math.max(1, heldCount()))).bounds(x + 266, y + 26, 66, 16).build());

        buyButton = Button.builder(Component.translatable(Lang.SHOP_BUY), b -> {
            if (selected != null) {
                ClientNetwork.requestBuy(selected, amount);
            }
        }).bounds(x + 196, y + 62, 66, 20).build();
        track(buyButton);

        sellButton = Button.builder(Component.translatable(Lang.SHOP_SELL), b -> {
            if (selected != null) {
                ClientNetwork.requestSell(selected, amount);
            }
        }).bounds(x + 266, y + 62, 66, 20).build();
        track(sellButton);
    }

    /** Registers a widget for input and adds it to this screen's own draw list. */
    private <T extends AbstractWidget> T track(T widget) {
        widgets.add(widget);
        return addWidget(widget);
    }

    @Override
    public void removed() {
        ClientNetwork.setResultListener(null);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        // Single player keeps ticking. A shop that pauses the world would make "sell while
        // the furnace runs" impossible and is not what a player expects from an inventory.
        return false;
    }

    // ---- data ------------------------------------------------------------------------

    private void rebuildNamespaces() {
        namespaces.clear();
        namespaces.add("");
        ShopSnapshot snapshot = ClientShopState.get().snapshot();
        if (snapshot == null) {
            return;
        }
        for (String itemId : snapshot.buyPrices().keySet()) {
            int colon = itemId.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : itemId.substring(0, colon);
            if (!namespaces.contains(namespace)) {
                namespaces.add(namespace);
            }
        }
        if (namespaceIndex >= namespaces.size()) {
            namespaceIndex = 0;
        }
    }

    /**
     * Recomputes the visible list.
     *
     * <p>Done on filter or sort changes rather than per frame. The grid loop runs up to 54
     * times a frame and allocating or re-sorting inside it would be felt on a large modpack.
     */
    private void refresh() {
        visible.clear();
        ShopSnapshot snapshot = ClientShopState.get().snapshot();
        if (snapshot == null) {
            return;
        }

        String namespace = namespaces.isEmpty() ? "" : namespaces.get(namespaceIndex);
        List<String> favorites = BlockiesEconomy.server().config().client().favorites();

        for (Map.Entry<String, Long> e : snapshot.buyPrices().entrySet()) {
            String itemId = e.getKey();
            if (!namespace.isEmpty() && !itemId.startsWith(namespace + ":")) {
                continue;
            }
            Item item = resolve(itemId);
            if (item == null) {
                // Priced by the server but absent here — a mod the server has and we do
                // not. Listing it would show a missing-texture cube nobody can use.
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (!search.isEmpty()
                    && !itemId.toLowerCase(Locale.ROOT).contains(search)
                    && !stack.getHoverName().getString().toLowerCase(Locale.ROOT)
                            .contains(search)) {
                continue;
            }
            visible.add(new Entry(itemId, stack, e.getValue().longValue(),
                    snapshot.sellPrice(itemId)));
        }

        sortVisible(favorites);
        int maxScroll = Math.max(0, (visible.size() + COLUMNS - 1) / COLUMNS - rows);
        scrollRow = Math.min(scrollRow, maxScroll);
        updateButtonLabels();
    }

    private void sortVisible(List<String> favorites) {
        final Comparator<Entry> order;
        switch (sort) {
            case NAME:
                order = (a, b) -> a.displayName.compareToIgnoreCase(b.displayName);
                break;
            case PRICE_ASC:
                order = (a, b) -> Long.compare(a.buy, b.buy);
                break;
            case PRICE_DESC:
                order = (a, b) -> Long.compare(b.buy, a.buy);
                break;
            default:
                order = (a, b) -> a.itemId.compareTo(b.itemId);
                break;
        }

        // Pinned items float to the top of whatever order is chosen, rather than replacing
        // it: a player who pinned twenty things still wants them sorted sensibly.
        visible.sort((a, b) -> {
            boolean aFav = favorites.contains(a.itemId);
            boolean bFav = favorites.contains(b.itemId);
            if (aFav != bFav) {
                return aFav ? -1 : 1;
            }
            return order.compare(a, b);
        });
    }

    private static Item resolve(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(location);
    }

    private Entry selectedEntry() {
        if (selected == null) {
            return null;
        }
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).itemId.equals(selected)) {
                return visible.get(i);
            }
        }
        return null;
    }

    // ---- amounts ---------------------------------------------------------------------

    private static int parseAmount(String value) {
        if (value == null || value.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(MAX_TRADE, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void setAmount(int value) {
        amount = Math.max(1, Math.min(MAX_TRADE, value));
        amountBox.setValue(String.valueOf(amount));
        updateButtonLabels();
    }

    private int maxAffordable() {
        Entry entry = selectedEntry();
        if (entry == null || entry.buy <= 0L) {
            return 1;
        }
        return (int) Math.max(1L, Math.min(MAX_TRADE,
                ClientShopState.get().balance() / entry.buy));
    }

    /**
     * How many of the selected item the player appears to be holding.
     *
     * <p>An estimate, and knowingly so. The server decides what is actually sellable — it
     * refuses anything with non-default components, which the client cannot check the same
     * way across versions. Enchanted stacks are excluded here because they are the common
     * case; anything else it gets wrong comes back as a refusal, not a wrong payment.
     */
    private int heldCount() {
        Entry entry = selectedEntry();
        if (entry == null || minecraft == null || minecraft.player == null) {
            return 0;
        }
        int found = 0;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(entry.stack.getItem()) && !stack.isEnchanted()) {
                found += stack.getCount();
            }
        }
        return Math.min(MAX_TRADE, found);
    }

    private void updateButtonLabels() {
        // Called from the amount box responder, which can fire while init is still building
        // the detail row.
        if (sortButton == null || buyButton == null) {
            return;
        }
        sortButton.setMessage(Component.translatable(sort.key));
        String namespace = namespaces.isEmpty() ? "" : namespaces.get(namespaceIndex);
        namespaceButton.setMessage(namespace.isEmpty()
                ? Component.translatable(Lang.SHOP_FILTER_ALL)
                : Component.literal(namespace));

        Entry entry = selectedEntry();
        boolean ready = entry != null && ClientNetwork.connected();
        buyButton.active = ready && entry.buy > 0L
                && ClientShopState.get().balance() >= entry.buy * (long) amount;
        // Greyed out when the player holds none rather than letting them click into a
        // refusal. The count is the client's estimate; the server still decides.
        sellButton.active = ready && entry.sell > 0L && heldCount() >= amount;
    }

    /**
     * Keeps the buttons in step with things that change while the screen is open.
     *
     * <p>A balance can move without the player touching the shop — an advancement pays out,
     * or an operator adjusts it — and items can arrive in the inventory. Twenty times a
     * second is cheap for a scan of forty-one slots and avoids a Buy button that claims the
     * player cannot afford something they now can.
     */
    @Override
    public void tick() {
        updateButtonLabels();
    }

    // ---- rendering -------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Drawn rather than delegated to renderBackground, whose signature changed in
        // 1.20.5. Widgets are rendered by hand below for the same reason: Screen#render
        // draws its own background on the newer versions and would cover the panel.
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int x = left();
        int y = top();
        panel(graphics, x, y, PANEL_WIDTH, panelHeight());

        graphics.drawString(font, title, x + 8, y + 7, COLOUR_TITLE);
        renderBalance(graphics, x, y);

        renderGrid(graphics, mouseX, mouseY);

        panel(graphics, x + 4, detailTop(), PANEL_WIDTH - 8, DETAIL_HEIGHT - 4);
        renderDetail(graphics);

        for (int i = 0; i < widgets.size(); i++) {
            widgets.get(i).render(graphics, mouseX, mouseY, partialTick);
        }

        renderTotals(graphics);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderBalance(GuiGraphics graphics, int x, int y) {
        // The exact figure, not the short form the HUD uses. This is the screen where a
        // player decides what they can afford, and "4.9K" beside a 4,900-Blockie price is
        // a number they have to do arithmetic on. The HUD is a glance; this is a decision.
        String text = ClientShopState.get().balanceKnown()
                ? ClientMoneyText.fullForm(ClientShopState.get().balance())
                : "—";

        // The coin, the same symbol the HUD shows, rather than the letter B. Honours the
        // same setting: a player who turned the icon off wants the letter everywhere, not
        // one of each.
        boolean icon = BlockiesEconomy.server().config().client().showCurrencyIcon();
        int right = x + PANEL_WIDTH - 8;
        int textTop = y + 7;

        if (icon) {
            int iconX = right - BALANCE_ICON_SIZE;
            graphics.drawString(font, text, iconX - BALANCE_ICON_GAP - font.width(text),
                    textTop, COLOUR_TITLE);
            // Centred on where the digits actually are, not on the line box that contains
            // them. Minecraft's glyphs sit in the upper rows of a nine-pixel line and
            // leave the rest for descenders, so centring on the box puts the coin visibly
            // low. Centring on the ink is also why this is not (lineHeight - size) / 2 —
            // which, with a 10-pixel icon on a 9-pixel line, Java truncates to 0 anyway.
            CoinIcon.draw(graphics, iconX,
                    textTop + TEXT_INK_CENTRE - BALANCE_ICON_SIZE / 2, BALANCE_ICON_SIZE);
        } else {
            String withSymbol = text + " " + ClientMoneyText.currency();
            graphics.drawString(font, withSymbol, right - font.width(withSymbol), textTop,
                    COLOUR_TITLE);
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int gridLeft = left() + 8;
        int gridTop = gridTop();
        int gridBottom = gridTop + rows * CELL_HEIGHT;

        if (visible.isEmpty()) {
            Component message = ClientNetwork.connected()
                    ? Component.translatable(Lang.SHOP_NO_MATCHES)
                    : Component.translatable(Lang.SHOP_NOT_CONNECTED);
            graphics.drawString(font, message, gridLeft + 4, gridTop + 8, COLOUR_LABEL);
            return;
        }

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLUMNS * CELL_WIDTH, gridBottom);
        int first = scrollRow * COLUMNS;
        int last = Math.min(visible.size(), first + rows * COLUMNS);
        for (int i = first; i < last; i++) {
            int index = i - first;
            int cellX = gridLeft + (index % COLUMNS) * CELL_WIDTH;
            int cellY = gridTop + (index / COLUMNS) * CELL_HEIGHT;
            renderCell(graphics, visible.get(i), cellX, cellY, mouseX, mouseY);
        }
        graphics.disableScissor();

        renderScrollbar(graphics, gridTop, gridBottom);
    }

    private void renderCell(GuiGraphics graphics, Entry entry, int cellX, int cellY,
                            int mouseX, int mouseY) {
        boolean hovered = mouseX >= cellX && mouseX < cellX + CELL_WIDTH
                && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT;
        boolean isSelected = entry.itemId.equals(selected);

        if (isSelected) {
            graphics.fill(cellX, cellY, cellX + CELL_WIDTH, cellY + CELL_HEIGHT, 0x60FFFFFF);
        } else if (hovered) {
            graphics.fill(cellX, cellY, cellX + CELL_WIDTH, cellY + CELL_HEIGHT, 0x30FFFFFF);
        }

        graphics.renderItem(entry.stack, cellX + (CELL_WIDTH - 16) / 2, cellY + 2);

        // Dimmed when the player cannot afford one. Hiding it would be worse: knowing what
        // something costs is half the point of a shop.
        boolean affordable = ClientShopState.get().balance() >= entry.buy;
        if (!affordable) {
            graphics.fill(cellX + (CELL_WIDTH - 16) / 2, cellY + 2,
                    cellX + (CELL_WIDTH + 16) / 2, cellY + 18, 0x90101010);
        }

        String prices = ClientMoneyText.shortForm(entry.buy) + "/"
                + ClientMoneyText.shortForm(entry.sell);
        graphics.pose().pushPose();
        graphics.pose().scale(0.7f, 0.7f, 1.0f);
        int textWidth = (int) (font.width(prices) * 0.7f);
        graphics.drawString(font, prices,
                (int) ((cellX + (CELL_WIDTH - textWidth) / 2) / 0.7f),
                (int) ((cellY + 20) / 0.7f),
                affordable ? COLOUR_TITLE : COLOUR_LABEL, false);
        graphics.pose().popPose();
    }

    private void renderScrollbar(GuiGraphics graphics, int gridTop, int gridBottom) {
        int totalRows = (visible.size() + COLUMNS - 1) / COLUMNS;
        if (totalRows <= rows) {
            return;
        }
        int barX = left() + 8 + COLUMNS * CELL_WIDTH + 2;
        int trackHeight = gridBottom - gridTop;
        int thumbHeight = Math.max(12, trackHeight * rows / totalRows);
        int travel = trackHeight - thumbHeight;
        int thumbY = gridTop + travel * scrollRow / Math.max(1, totalRows - rows);

        graphics.fill(barX, gridTop, barX + SCROLLBAR_WIDTH, gridBottom, 0x60000000);
        graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xA0FFFFFF);
    }

    private void renderDetail(GuiGraphics graphics) {
        int x = left() + 12;
        int y = detailTop() + 8;
        Entry entry = selectedEntry();

        if (entry == null) {
            // Two lines in every language, not just the ones that need it. The Ukrainian
            // string ran off the panel on one line, and a message that wraps in some
            // languages and not others makes the panel jump height as the locale changes.
            // Two keys rather than one wrapped automatically, so a translator chooses
            // where the break falls instead of the renderer guessing.
            graphics.drawString(font, Component.translatable(Lang.SHOP_NO_SELECTION),
                    x, y + 2, COLOUR_LABEL);
            graphics.drawString(font, Component.translatable(Lang.SHOP_NO_SELECTION_HINT),
                    x, y + 2 + font.lineHeight + 2, COLOUR_LABEL);
            return;
        }

        graphics.renderItem(entry.stack, x, y);
        // Name only. The item id was here for identification, but the icon and the name
        // already do that, and "minecraft:brown_stained_glass_pane" is a line of noise in
        // the one panel the player reads before spending. It is still in the hover tooltip
        // and in /shop price for anyone who needs it.
        graphics.drawString(font, entry.stack.getHoverName(), x + 22, y + 4, COLOUR_TITLE);

        graphics.drawString(font,
                Component.translatable(Lang.SHOP_UNIT_BUY,
                        ClientMoneyText.fullForm(entry.buy)),
                x, y + 26, COLOUR_BUY);
        graphics.drawString(font,
                Component.translatable(Lang.SHOP_UNIT_SELL,
                        ClientMoneyText.fullForm(entry.sell)),
                x, y + 37, COLOUR_SELL);

        if (status != null) {
            graphics.drawString(font, status, x, y + 54, COLOUR_LABEL);
        }
    }

    /** The two running totals, drawn just above the buttons they apply to. */
    private void renderTotals(GuiGraphics graphics) {
        Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        int y = detailTop() + 52;
        String buyTotal = "-" + ClientMoneyText.shortForm(entry.buy * (long) amount);
        String sellTotal = "+" + ClientMoneyText.shortForm(entry.sell * (long) amount);

        int buyX = left() + 196 + 33 - font.width(buyTotal) / 2;
        int sellX = left() + 266 + 33 - font.width(sellTotal) / 2;
        graphics.drawString(font, buyTotal, buyX, y, COLOUR_BUY);
        graphics.drawString(font, sellTotal, sellX, y, COLOUR_SELL);
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Entry entry = entryAt(mouseX, mouseY);
        if (entry == null) {
            return;
        }
        List<Component> lines = new ArrayList<Component>(4);
        lines.add(entry.stack.getHoverName());
        lines.add(Component.translatable(Lang.SHOP_UNIT_BUY,
                ClientMoneyText.fullForm(entry.buy)));
        lines.add(Component.translatable(Lang.SHOP_UNIT_SELL,
                ClientMoneyText.fullForm(entry.sell)));
        lines.add(Component.translatable(Lang.SHOP_PIN_HINT));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Draws a filled panel with a hairline border, matching the vanilla tooltip look. */
    private static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOUR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOUR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOUR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOUR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOUR_BORDER);
    }

    // ---- input -----------------------------------------------------------------------

    private Entry entryAt(double mouseX, double mouseY) {
        int gridLeft = left() + 8;
        int gridTop = gridTop();
        if (mouseX < gridLeft || mouseX >= gridLeft + COLUMNS * CELL_WIDTH
                || mouseY < gridTop || mouseY >= gridTop + rows * CELL_HEIGHT) {
            return null;
        }
        int column = (int) ((mouseX - gridLeft) / CELL_WIDTH);
        int row = (int) ((mouseY - gridTop) / CELL_HEIGHT);
        int index = (scrollRow + row) * COLUMNS + column;
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Entry entry = entryAt(mouseX, mouseY);
        if (entry != null) {
            if (button == 1) {
                togglePin(entry.itemId);
            } else {
                selected = entry.itemId;
                status = null;
                updateButtonLabels();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void togglePin(String itemId) {
        List<String> favorites = BlockiesEconomy.server().config().client().favorites();
        if (!favorites.remove(itemId)) {
            favorites.add(itemId);
        }
        BlockiesEconomy.server().config().saveClient();
        refresh();
    }

    /**
     * Scrolls the grid, or steps the amount when the pointer is over the detail panel.
     *
     * <p>Two overloads because Minecraft split the scroll delta into x and y in 1.20.5.
     * Only one of them overrides anything on a given version; the other is dead code the
     * compiler is happy to carry, which is cheaper than forking the whole screen.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return handleScroll(mouseX, mouseY, delta);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return handleScroll(mouseX, mouseY, scrollY);
    }

    private boolean handleScroll(double mouseX, double mouseY, double delta) {
        int direction = delta > 0 ? 1 : -1;

        if (mouseY >= detailTop() && selected != null) {
            boolean shift = hasShiftDown();
            setAmount(amount + direction * (shift ? 64 : 1));
            return true;
        }

        int totalRows = (visible.size() + COLUMNS - 1) / COLUMNS;
        int maxScroll = Math.max(0, totalRows - rows);
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow - direction));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The search box swallows every printable key, so Escape has to be handled before
        // it gets the chance, or the screen becomes impossible to leave by keyboard.
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- results ---------------------------------------------------------------------

    private void onTradeResult(boolean wasBuy, TradeOutcome.Reason reason, String itemId,
                               int count, long total, int dropped) {
        if (reason != TradeOutcome.Reason.OK) {
            status = Component.translatable(ClientNetwork.errorKey(reason));
            return;
        }
        if (dropped > 0) {
            status = Component.translatable(Lang.SHOP_RESULT_DROPPED,
                    Integer.valueOf(count), ClientMoneyText.shortForm(total),
                    Integer.valueOf(dropped));
        } else {
            status = Component.translatable(
                    wasBuy ? Lang.SHOP_RESULT_BOUGHT : Lang.SHOP_RESULT_SOLD,
                    Integer.valueOf(count), ClientMoneyText.shortForm(total));
        }
        updateButtonLabels();
    }
}
