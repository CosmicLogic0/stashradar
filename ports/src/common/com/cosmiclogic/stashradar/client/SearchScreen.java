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
import org.lwjgl.glfw.GLFW;

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
        super(Component.translatable("screen.stashradar.title"));
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
        searchBox = new EditBox(font, left, 32, LIST_WIDTH - 84, 20, Component.translatable("screen.stashradar.search"));
        searchBox.setHint(Component.translatable("screen.stashradar.hint").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(64);
        searchBox.setValue(lastQuery);
        searchBox.setResponder(text -> refresh());
        addRenderableWidget(searchBox);
        rangeButton = addRenderableWidget(Button.builder(rangeLabel(), b -> cycleRange())
                .bounds(left + LIST_WIDTH - 80, 32, 80, 20).build());
        setInitialFocus(searchBox);
        refresh();
    }

    private Component rangeLabel() {
        return Component.translatable("screen.stashradar.range", range);
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //#if MC < 1.20.2
        renderBackground(g);
        //#endif
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - LIST_WIDTH) / 2;
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);

        if (results.isEmpty()) {
            Component msg = searchBox.getValue().trim().isEmpty()
                    ? Component.translatable("screen.stashradar.empty")
                    : Component.translatable("screen.stashradar.none");
            g.drawCenteredString(font, msg, width / 2, listTop() + 8, 0x808080);
            return;
        }
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < results.size(); i++) {
            Entry e = results.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            boolean hover = mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
            g.fill(left, y, left + LIST_WIDTH, y + ROW_HEIGHT - 1, hover ? 0x8040A060 : (i % 2 == 0 ? 0x40000000 : 0x20000000));
            g.renderItem(e.stack, left + 2, y + 1);
            g.drawString(font, e.name, left + 22, y + 6, 0xFFFFFF);
            String id = e.id.startsWith("minecraft:") ? e.id.substring(10) : e.id;
            g.drawString(font, id, left + LIST_WIDTH - 4 - font.width(id), y + 6, 0x707070);
        }
        if (results.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(results.size(), scroll + rows) + " / " + results.size();
            g.drawCenteredString(font, more, width / 2, listTop() + rows * ROW_HEIGHT + 4, 0x808080);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - LIST_WIDTH) / 2;
        if (button == 0 && mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= listTop()) {
            int row = (int) ((mouseY - listTop()) / ROW_HEIGHT);
            if (row < visibleRows() && scroll + row < results.size()) {
                pick(results.get(scroll + row));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !results.isEmpty()) {
            pick(results.get(0));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void pick(Entry entry) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand("find " + entry.id + " " + range);
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
