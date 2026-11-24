package com.cosmiclogic.stashradar.client;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.search.Ids;
import net.minecraft.ChatFormatting;



import net.minecraft.client.gui.GuiGraphics;







import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;





import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The search screen: type part of an item name, pick it from the suggestions (with icons), and the
 * server is asked via /find. The server does the real search, so this screen can't be used to cheat.
 * With an empty box it lists recent searches; the button under the box searches item names (or a #tag).
 */
public class SearchScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 260;
    private static final int[] RANGES = {8, 16, 24, 32, 48, 64};

    /** Remembered between openings. */
    private static String lastQuery = "";
    private static int lastRange = -1;

    /** One row: an item to search for, or a recent search. {@code query} is what follows "/find ". */
    private static final class Entry {
        final ItemStack stack;
        final String name;
        final String lowerName;
        final String id;
        final String query;

        Entry(ItemStack stack, String name, String id, String query) {
            this.stack = stack;
            this.name = name;
            this.lowerName = name.toLowerCase(Locale.ROOT);
            this.id = id;
            this.query = query;
        }
    }

    private static List<Entry> allItems;

    private EditBox searchBox;
    private Button rangeButton;
    private Button textButton;
    private Button settingsButton;
    /** False when the server has no /find for this player (no Stash Radar there, or not allowed). */
    private boolean available = true;
    /** True while the list shows recent searches (the box is empty). */
    private boolean showingRecent;
    private final List<Entry> results = new ArrayList<>();
    private int scroll;
    private int range;

    public SearchScreen() {
        super(tr("screen.stashradar.title"));
    }

    @Override
    protected void init() {
        if (allItems == null) {
            allItems = new ArrayList<>();
            for (Item item : Ids.allItems()) {
                if (item == Items.AIR) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                allItems.add(new Entry(stack, stack.getHoverName().getString(), Ids.item(item), Ids.item(item)));
            }
        }
        if (lastRange < 0) {
            lastRange = StashRadar.config().range;
        }
        range = lastRange;

        int left = (width - LIST_WIDTH) / 2;
        searchBox = new EditBox(font, left, 32, LIST_WIDTH - 84, 20, tr("screen.stashradar.search"));

        searchBox.setHint(tr("screen.stashradar.hint").withStyle(ChatFormatting.DARK_GRAY));

        searchBox.setMaxLength(64);
        searchBox.setValue(lastQuery);
        searchBox.setResponder(text -> refresh());
        available = StashRadarClient.serverHasFind(minecraft);
        rangeButton = button(left + LIST_WIDTH - 80, 32, 80, 20, rangeLabel(), this::cycleRange, "screen.stashradar.range.tip");
        textButton = button(left, 56, LIST_WIDTH, 20, Component.nullToEmpty(""), this::searchText, "screen.stashradar.text.tip");
        settingsButton = button(width - 64, 6, 58, 20, tr("screen.stashradar.settings"),
                () -> StashRadarClient.open(minecraft, new ConfigScreen(this)), "screen.stashradar.settings.tip");

        addRenderableWidget(searchBox);
        addRenderableWidget(rangeButton);
        addRenderableWidget(textButton);
        addRenderableWidget(settingsButton);






        setInitialFocus(searchBox);
        refresh();
    }

    /** A button with a hover tooltip (1.19.3+; older versions show the footer line instead). */
    static Button button(int x, int y, int w, int h, Component label, Runnable action, String tooltipKey) {

        Button b = Button.builder(label, btn -> action.run()).bounds(x, y, w, h).build();
        if (tooltipKey != null) {
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr(tooltipKey)));
        }
        return b;



    }

    private Component rangeLabel() {
        return tr("screen.stashradar.range", range);
    }

    static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {

        return Component.translatable(key, args);



    }

    private void cycleRange() {
        int next = RANGES[0];
        for (int r : RANGES) {
            if (r > range) {
                next = r;
                break;
            }
        }
        range = next;
        lastRange = range;
        rangeButton.setMessage(rangeLabel());
    }

    private void refresh() {
        String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        lastQuery = searchBox.getValue();
        results.clear();
        scroll = 0;
        String typed = searchBox.getValue().trim();
        textButton.visible = !typed.isEmpty() && !typed.contains("\"");
        textButton.setMessage(typed.startsWith("#")
                ? tr("screen.stashradar.tag", typed)
                : tr("screen.stashradar.text", typed));
        showingRecent = q.isEmpty();
        if (q.isEmpty()) {
            for (String query : StashRadarClient.recent()) {
                results.add(recentEntry(query));
            }
            return;
        }
        List<Entry> contains = new ArrayList<>();
        for (Entry e : allItems) {
            if (e.lowerName.startsWith(q) || e.id.startsWith(q) || e.id.startsWith("minecraft:" + q)) {
                results.add(e);
            } else if (e.lowerName.contains(q) || e.id.contains(q)) {
                contains.add(e);
            }
        }
        results.addAll(contains);
    }

    /** A recent search as a row: an item with its icon, a #tag, or a name search. */
    private static Entry recentEntry(String query) {
        if (query.startsWith("#")) {
            return new Entry(new ItemStack(Items.CHEST), query, "tag", query);
        }
        if (query.startsWith("name ")) {
            String text = query.substring(5).replace("\"", "");
            return new Entry(new ItemStack(Items.NAME_TAG), "\"" + text + "\"", "name", query);
        }
        for (Entry e : allItems) {
            if (e.id.equals(query)) {
                return e;
            }
        }
        return new Entry(new ItemStack(Items.BARRIER), query, "?", query);
    }

    private void searchText() {
        String typed = searchBox.getValue().trim();
        if (typed.isEmpty()) {
            return;
        }
        if (typed.startsWith("#")) {
            String tag = typed.substring(1).toLowerCase(Locale.ROOT).replace(' ', '_');
            go(tag.indexOf(':') >= 0 ? "#" + tag : "#minecraft:" + tag);
        } else {
            go("name \"" + typed.replace("\"", "") + "\"");
        }
    }

    private int listTop() {
        return 82;
    }

    private int visibleRows() {
        return Math.max(1, (height - listTop() - 28) / ROW_HEIGHT);
    }

    @Override




    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {



        super.render(g, mouseX, mouseY, partialTick);





        int left = (width - LIST_WIDTH) / 2;
        centered(g, title, width / 2, 14, 0xFFFFFF);
        centered(g, tr("screen.stashradar.footer"), width / 2, height - 14, 0x808080);
        if (!available) {
            centered(g, tr("screen.stashradar.unavailable"), width / 2, listTop() - 2, 0xFF6060);
            return;
        }






        if (results.isEmpty()) {
            Component msg = searchBox.getValue().trim().isEmpty()
                    ? tr("screen.stashradar.empty")
                    : tr("screen.stashradar.none");
            centered(g, msg, width / 2, listTop() + 8, 0x808080);
            return;
        }
        if (showingRecent) {
            text(g, tr("screen.stashradar.recent").getString(), left + 2, listTop() - 11, 0xA0A0A0);
        }
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < results.size(); i++) {
            Entry e = results.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            boolean hover = mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
            rect(g, left, y, left + LIST_WIDTH, y + ROW_HEIGHT - 1, hover ? 0x8040A060 : (i % 2 == 0 ? 0x40000000 : 0x20000000));
            item(g, e.stack, left + 2, y + 1);
            text(g, e.name, left + 22, y + 6, 0xFFFFFF);
            String id = showingRecent ? tr("screen.stashradar.recent.remove").getString()
                    : e.id.startsWith("minecraft:") ? e.id.substring(10) : e.id;
            text(g, id, left + LIST_WIDTH - 4 - font.width(id), y + 6, 0x707070);
        }
        if (results.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(results.size(), scroll + rows) + " / " + results.size();
            centered(g, Component.nullToEmpty(more), width / 2, listTop() + rows * ROW_HEIGHT + 4, 0x808080);
        }
    }

    // ---- drawing helpers: one render() body for every version ----






    // Text colours get a full alpha byte: 1.21.6+ treats a missing alpha as invisible.
    private void centered(GuiGraphics g, Component c, int x, int y, int color) { g.drawCenteredString(font, c, x, y, 0xFF000000 | color); }
    private void text(GuiGraphics g, String s, int x, int y, int color) { g.drawString(font, s, x, y, 0xFF000000 | color); }
    private void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) { g.fill(x0, y0, x1, y1, color); }
    private void item(GuiGraphics g, ItemStack stack, int x, int y) { g.renderItem(stack, x, y); }







    /** @return true if the click picked an item */
    private boolean clickList(double mouseX, double mouseY, int button) {
        int left = (width - LIST_WIDTH) / 2;
        if ((button == 0 || button == 1) && available && mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= listTop()) {
            int row = (int) ((mouseY - listTop()) / ROW_HEIGHT);
            if (row < visibleRows() && scroll + row < results.size()) {
                Entry e = results.get(scroll + row);
                if (button == 1 && showingRecent) {
                    StashRadarClient.forgetRecent(e.query);   // right-click forgets a recent search
                    refresh();
                } else if (button == 0) {
                    pick(e);
                }
                return true;
            }
        }
        return false;
    }







    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickList(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }


    @Override

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {



        int max = Math.max(0, results.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    /** Enter picks the first match. */
    private boolean enter(int keyCode) {
        if (keyCode == 257 || keyCode == 335) {   // GLFW Enter, keypad Enter
            if (!results.isEmpty()) {
                pick(results.get(0));
            } else if (textButton.visible) {
                searchText();
            }
            return true;
        }
        return false;
    }







    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return enter(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }


    private void pick(Entry entry) {
        go(entry.query);
    }

    private void go(String query) {
        if (!available) {
            return;
        }
        if (minecraft != null && minecraft.player != null) {
            StashRadarClient.search(minecraft, query, range);
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
