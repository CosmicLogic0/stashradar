package com.cosmiclogic.stashradar.client;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.search.Ids;
import net.minecraft.ChatFormatting;
//#if MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
//#endif
//#if MC < 1.19
import net.minecraft.network.chat.TranslatableComponent;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
//#if MC >= 1.21.9
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//#endif

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The search screen: type part of an item name, pick it from the suggestions (with icons), and the
 * server is asked via /find. The server does the real search, so this screen can't be used to cheat.
 */
public class SearchScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 260;
    private static final int[] RANGES = {8, 16, 24, 32, 48, 64};

    /** Remembered between openings. */
    private static String lastQuery = "";
    private static int lastRange = -1;

    private static final class Entry {
        final ItemStack stack;
        final String name;
        final String lowerName;
        final String id;

        Entry(ItemStack stack, String name, String id) {
            this.stack = stack;
            this.name = name;
            this.lowerName = name.toLowerCase(Locale.ROOT);
            this.id = id;
        }
    }

    private static List<Entry> allItems;

    private EditBox searchBox;
    private Button rangeButton;
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
                allItems.add(new Entry(stack, stack.getHoverName().getString(), Ids.item(item)));
            }
        }
        if (lastRange < 0) {
            lastRange = StashRadar.config().range;
        }
        range = lastRange;

        int left = (width - LIST_WIDTH) / 2;
        searchBox = new EditBox(font, left, 32, LIST_WIDTH - 84, 20, tr("screen.stashradar.search"));
        //#if MC >= 1.19.4
        searchBox.setHint(tr("screen.stashradar.hint").withStyle(ChatFormatting.DARK_GRAY));
        //#endif
        searchBox.setMaxLength(64);
        searchBox.setValue(lastQuery);
        searchBox.setResponder(text -> refresh());
        //#if MC >= 1.19.3
        rangeButton = Button.builder(rangeLabel(), b -> cycleRange()).bounds(left + LIST_WIDTH - 80, 32, 80, 20).build();
        //#else
        rangeButton = new Button(left + LIST_WIDTH - 80, 32, 80, 20, rangeLabel(), b -> cycleRange());
        //#endif
        //#if MC >= 1.17
        addRenderableWidget(searchBox);
        addRenderableWidget(rangeButton);
        //#else
        addButton(searchBox);
        addButton(rangeButton);
        //#endif
        setInitialFocus(searchBox);
        refresh();
    }

    private Component rangeLabel() {
        return tr("screen.stashradar.range", range);
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        //#if MC >= 1.19
        return Component.translatable(key, args);
        //#else
        return new TranslatableComponent(key, args);
        //#endif
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
        if (q.isEmpty()) {
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

    private int listTop() {
        return 60;
    }

    private int visibleRows() {
        return Math.max(1, (height - listTop() - 24) / ROW_HEIGHT);
    }

    @Override
    //#if MC >= 1.20
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    //#else
    public void render(PoseStack g, int mouseX, int mouseY, float partialTick) {
    //#endif
        //#if MC < 1.20.2
        renderBackground(g);
        //#endif
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - LIST_WIDTH) / 2;
        centered(g, title, width / 2, 14, 0xFFFFFF);
        //#if MC < 1.19.4
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            text(g, tr("screen.stashradar.hint").getString(), searchBox.x + 4, searchBox.y + 6, 0x555555);
        }
        //#endif

        if (results.isEmpty()) {
            Component msg = searchBox.getValue().trim().isEmpty()
                    ? tr("screen.stashradar.empty")
                    : tr("screen.stashradar.none");
            centered(g, msg, width / 2, listTop() + 8, 0x808080);
            return;
        }
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < results.size(); i++) {
            Entry e = results.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            boolean hover = mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
            rect(g, left, y, left + LIST_WIDTH, y + ROW_HEIGHT - 1, hover ? 0x8040A060 : (i % 2 == 0 ? 0x40000000 : 0x20000000));
            item(g, e.stack, left + 2, y + 1);
            text(g, e.name, left + 22, y + 6, 0xFFFFFF);
            String id = e.id.startsWith("minecraft:") ? e.id.substring(10) : e.id;
            text(g, id, left + LIST_WIDTH - 4 - font.width(id), y + 6, 0x707070);
        }
        if (results.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(results.size(), scroll + rows) + " / " + results.size();
            centered(g, Component.nullToEmpty(more), width / 2, listTop() + rows * ROW_HEIGHT + 4, 0x808080);
        }
    }

    // ---- drawing helpers: one render() body for every version ----
    //#if MC >= 1.20
    // Text colours get a full alpha byte: 1.21.6+ treats a missing alpha as invisible.
    private void centered(GuiGraphics g, Component c, int x, int y, int color) { g.drawCenteredString(font, c, x, y, 0xFF000000 | color); }
    private void text(GuiGraphics g, String s, int x, int y, int color) { g.drawString(font, s, x, y, 0xFF000000 | color); }
    private void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) { g.fill(x0, y0, x1, y1, color); }
    private void item(GuiGraphics g, ItemStack stack, int x, int y) { g.renderItem(stack, x, y); }
    //#else
    private void centered(PoseStack g, Component c, int x, int y, int color) { GuiComponent.drawCenteredString(g, font, c, x, y, color); }
    private void text(PoseStack g, String s, int x, int y, int color) { GuiComponent.drawString(g, font, s, x, y, color); }
    private void rect(PoseStack g, int x0, int y0, int x1, int y1, int color) { GuiComponent.fill(g, x0, y0, x1, y1, color); }
    private void item(PoseStack g, ItemStack stack, int x, int y) { itemRenderer.renderAndDecorateItem(stack, x, y); }
    //#endif

    /** @return true if the click picked an item */
    private boolean clickList(double mouseX, double mouseY, int button) {
        int left = (width - LIST_WIDTH) / 2;
        if (button == 0 && mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= listTop()) {
            int row = (int) ((mouseY - listTop()) / ROW_HEIGHT);
            if (row < visibleRows() && scroll + row < results.size()) {
                pick(results.get(scroll + row));
                return true;
            }
        }
        return false;
    }

    //#if MC >= 1.21.9
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return clickList(e.x(), e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }
    //#else
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickList(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    //#endif

    @Override
    //#if MC >= 1.20.2
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    //#else
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
    //#endif
        int max = Math.max(0, results.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    /** Enter picks the first match. */
    private boolean enter(int keyCode) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !results.isEmpty()) {
            pick(results.get(0));
            return true;
        }
        return false;
    }

    //#if MC >= 1.21.9
    @Override
    public boolean keyPressed(KeyEvent e) {
        return enter(e.key()) || super.keyPressed(e);
    }
    //#else
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return enter(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    //#endif

    private void pick(Entry entry) {
        if (minecraft != null && minecraft.player != null) {
            //#if MC >= 1.19.3
            minecraft.player.connection.sendCommand("find " + entry.id + " " + range);
            //#elif MC >= 1.19
            minecraft.player.commandUnsigned("find " + entry.id + " " + range);
            //#else
            minecraft.player.chat("/find " + entry.id + " " + range);
            //#endif
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
