package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.stashradar.StashRadar;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The search screen for 1.12.2: type part of an item name, pick it from the suggestions (with icons),
 * and the server is asked via /find. The server does the real search, so this screen can't be used to cheat.
 */
public class SearchScreen112 extends GuiScreen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 260;
    private static final int[] RANGES = {8, 16, 24, 32, 48, 64};
    private static final int RANGE_BUTTON = 1;

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

    private GuiTextField searchBox;
    private GuiButton rangeButton;
    private final List<Entry> results = new ArrayList<>();
    private int scroll;
    private int range;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (allItems == null) {
            allItems = new ArrayList<>();
            for (Item item : Item.REGISTRY) {
                if (item == Items.AIR) {
                    continue;
                }
                try {
                    ItemStack stack = new ItemStack(item);
                    allItems.add(new Entry(stack, stack.getDisplayName(), ContainerSearch112.itemId(item)));
                } catch (Throwable ignored) {
                    // an odd modded item: just leave it out of the suggestions
                }
            }
        }
        if (lastRange < 0) {
            lastRange = StashRadar.config().range;
        }
        range = lastRange;
        int left = (width - LIST_WIDTH) / 2;
        searchBox = new GuiTextField(0, fontRenderer, left, 32, LIST_WIDTH - 84, 20);
        searchBox.setMaxStringLength(64);
        searchBox.setText(lastQuery);
        searchBox.setFocused(true);
        buttonList.clear();
        rangeButton = new GuiButton(RANGE_BUTTON, left + LIST_WIDTH - 80, 32, 80, 20, rangeLabel());
        buttonList.add(rangeButton);
        refresh();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private String rangeLabel() {
        return I18n.format("screen.stashradar.range", range);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == RANGE_BUTTON) {
            int next = RANGES[0];
            for (int r : RANGES) {
                if (r > range) {
                    next = r;
                    break;
                }
            }
            range = next;
            lastRange = range;
            rangeButton.displayString = rangeLabel();
        }
    }

    private void refresh() {
        String q = searchBox.getText().trim().toLowerCase(Locale.ROOT);
        lastQuery = searchBox.getText();
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
    public void updateScreen() {
        searchBox.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - LIST_WIDTH) / 2;
        drawCenteredString(fontRenderer, I18n.format("screen.stashradar.title"), width / 2, 14, 0xFFFFFF);
        searchBox.drawTextBox();
        if (searchBox.getText().isEmpty() && !searchBox.isFocused()) {
            drawString(fontRenderer, I18n.format("screen.stashradar.hint"), left + 4, 38, 0x555555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (results.isEmpty()) {
            String msg = I18n.format(searchBox.getText().trim().isEmpty() ? "screen.stashradar.empty" : "screen.stashradar.none");
            drawCenteredString(fontRenderer, msg, width / 2, listTop() + 8, 0x808080);
            return;
        }
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < results.size(); i++) {
            Entry e = results.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            boolean hover = mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
            drawRect(left, y, left + LIST_WIDTH, y + ROW_HEIGHT - 1, hover ? 0x8040A060 : (i % 2 == 0 ? 0x40000000 : 0x20000000));
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(e.stack, left + 2, y + 1);
            RenderHelper.disableStandardItemLighting();
            drawString(fontRenderer, e.name, left + 22, y + 6, 0xFFFFFF);
            String id = e.id.startsWith("minecraft:") ? e.id.substring(10) : e.id;
            drawString(fontRenderer, id, left + LIST_WIDTH - 4 - fontRenderer.getStringWidth(id), y + 6, 0x707070);
        }
        if (results.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(results.size(), scroll + rows) + " / " + results.size();
            drawCenteredString(fontRenderer, more, width / 2, listTop() + rows * ROW_HEIGHT + 4, 0x808080);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        searchBox.mouseClicked(mouseX, mouseY, button);
        int left = (width - LIST_WIDTH) / 2;
        if (button == 0 && mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= listTop()) {
            int row = (mouseY - listTop()) / ROW_HEIGHT;
            if (row < visibleRows() && scroll + row < results.size()) {
                pick(results.get(scroll + row));
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, results.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && !results.isEmpty()) {
            pick(results.get(0));
            return;
        }
        if (searchBox.textboxKeyTyped(typedChar, keyCode)) {
            refresh();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void pick(Entry e) {
        mc.displayGuiScreen(null);
        if (mc.player != null) {
            mc.player.sendChatMessage("/find " + e.id + " " + range);
        }
    }
}
