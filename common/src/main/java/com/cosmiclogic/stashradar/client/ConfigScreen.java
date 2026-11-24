package com.cosmiclogic.stashradar.client;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;



import net.minecraft.client.gui.GuiGraphics;




import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings screen (Mods list → Stash Radar → Config, or "Settings" in the search screen). Edits
 * config/stashradar.json, which single-player and LAN worlds use right away. A server uses its own copy.
 * Numbers are checked and kept within sensible limits; Cancel leaves the file untouched.
 */
public class ConfigScreen extends Screen {

    private static final int COLUMN = 150;

    /** A number (or color) setting: its key for the label/description, the box and its limits. */
    private static final class NumberRow {
        final String key;
        final EditBox box;
        final int min;
        final int max;

        NumberRow(String key, EditBox box, int min, int max) {
            this.key = key;
            this.box = box;
            this.min = min;
            this.max = max;
        }
    }

    /** An on/off setting. */
    private static final class ToggleRow {
        final String key;
        final Button button;
        boolean value;

        ToggleRow(String key, Button button, boolean value) {
            this.key = key;
            this.button = button;
            this.value = value;
        }
    }

    private final Screen parent;
    private final List<NumberRow> numbers = new ArrayList<>();
    private final List<ToggleRow> toggles = new ArrayList<>();
    private EditBox color;
    private String problem;

    public ConfigScreen(Screen parent) {
        super(SearchScreen.tr("screen.stashradar.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        numbers.clear();
        toggles.clear();
        StashRadarConfig c = StashRadar.config();
        int left = width / 2 - COLUMN - 10;
        int right = width / 2 + 10;
        int y = 48;
        number("range", c.range, 1, 256, left, y);
        number("maxRange", c.maxRange, 1, 256, left, y + 24);
        number("cooldownSeconds", c.cooldownSeconds, 0, 3600, left, y + 48);
        number("highlightSeconds", c.highlightSeconds, 1, 120, left, y + 72);
        number("maxHighlights", c.maxHighlights, 1, 64, left, y + 96);
        color = new EditBox(font, left + COLUMN - 70, y + 120, 70, 18, SearchScreen.tr("config.stashradar.highlightColor"));
        color.setMaxLength(7);
        color.setValue(c.highlightColor);
        add(color);

        toggle("searchInsideItems", c.searchInsideItems, right, y);
        toggle("searchItemFrames", c.searchItemFrames, right, y + 24);
        toggle("searchEnderChest", c.searchEnderChest, right, y + 48);
        toggle("glowThroughWalls", c.glowThroughWalls, right, y + 72);
        toggle("addCreatorServer", c.addCreatorServer, right, y + 96);

        add(SearchScreen.button(width / 2 - 154, height - 28, 150, 20, SearchScreen.tr("screen.stashradar.config.save"), this::save, null));
        add(SearchScreen.button(width / 2 + 4, height - 28, 150, 20, SearchScreen.tr("screen.stashradar.config.cancel"), this::onClose, null));
    }

    private void number(String key, int value, int min, int max, int x, int y) {
        EditBox box = new EditBox(font, x + COLUMN - 50, y, 50, 18, SearchScreen.tr("config.stashradar." + key));
        box.setMaxLength(5);
        box.setValue(String.valueOf(value));
        add(box);
        numbers.add(new NumberRow(key, box, min, max));
    }

    private void toggle(String key, boolean value, int x, int y) {
        final ToggleRow[] row = new ToggleRow[1];
        Button b = SearchScreen.button(x, y - 1, COLUMN, 20, Component.nullToEmpty(""), () -> {
            row[0].value = !row[0].value;
            row[0].button.setMessage(toggleLabel(row[0]));
        }, "config.stashradar." + key + ".tip");
        row[0] = new ToggleRow(key, b, value);
        b.setMessage(toggleLabel(row[0]));
        add(b);
        toggles.add(row[0]);
    }

    private static Component toggleLabel(ToggleRow row) {
        return SearchScreen.tr("config.stashradar." + row.key).append(": ")
                .append(SearchScreen.tr(row.value ? "options.on" : "options.off"));
    }

    private void add(net.minecraft.client.gui.components.AbstractWidget w) {

        addRenderableWidget(w);



    }

    /** Checks every box; saves and closes only if all values are valid. */
    private void save() {
        StashRadarConfig c = StashRadar.config();
        int[] values = new int[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            NumberRow row = numbers.get(i);
            try {
                int v = Integer.parseInt(row.box.getValue().trim());
                if (v < row.min || v > row.max) {
                    throw new NumberFormatException();
                }
                values[i] = v;
            } catch (NumberFormatException e) {
                problem = SearchScreen.tr("screen.stashradar.config.bad", SearchScreen.tr("config.stashradar." + row.key), row.min, row.max).getString();
                return;
            }
        }
        String hex = color.getValue().trim();
        if (!hex.matches("#?[0-9a-fA-F]{6}")) {
            problem = SearchScreen.tr("screen.stashradar.config.badColor").getString();
            return;
        }
        c.range = values[0];
        c.maxRange = values[1];
        c.cooldownSeconds = values[2];
        c.highlightSeconds = values[3];
        c.maxHighlights = values[4];
        c.highlightColor = hex.startsWith("#") ? hex.toUpperCase(java.util.Locale.ROOT) : "#" + hex.toUpperCase(java.util.Locale.ROOT);
        c.searchInsideItems = toggles.get(0).value;
        c.searchItemFrames = toggles.get(1).value;
        c.searchEnderChest = toggles.get(2).value;
        c.glowThroughWalls = toggles.get(3).value;
        c.addCreatorServer = toggles.get(4).value;
        StashRadar.saveConfig();
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            StashRadarClient.open(minecraft, parent);
        }
    }

    /** The description of the setting under the mouse, shown at the bottom. */
    private String hovered(int mouseX, int mouseY) {
        for (NumberRow row : numbers) {
            if (over(mouseX, mouseY, wx(row.box) - (COLUMN - 50), wy(row.box), COLUMN, 18)) {
                return row.key;
            }
        }
        if (over(mouseX, mouseY, wx(color) - (COLUMN - 70), wy(color), COLUMN, 18)) {
            return "highlightColor";
        }
        for (ToggleRow row : toggles) {
            if (over(mouseX, mouseY, wx(row.button), wy(row.button), COLUMN, 20)) {
                return row.key;
            }
        }
        return null;
    }

    private static int wx(net.minecraft.client.gui.components.AbstractWidget w) {

        return w.getX();



    }

    private static int wy(net.minecraft.client.gui.components.AbstractWidget w) {

        return w.getY();



    }

    private static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override




    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {



        super.render(g, mouseX, mouseY, partialTick);





        centered(g, title.getString(), width / 2, 12, 0xFFFFFF);
        centered(g, SearchScreen.tr("screen.stashradar.config.where").getString(), width / 2, 26, 0x909090);
        for (NumberRow row : numbers) {
            text(g, SearchScreen.tr("config.stashradar." + row.key).getString(), wx(row.box) - (COLUMN - 50), wy(row.box) + 5, 0xFFFFFF);
        }
        text(g, SearchScreen.tr("config.stashradar.highlightColor").getString(), wx(color) - (COLUMN - 70), wy(color) + 5, 0xFFFFFF);
        String key = hovered(mouseX, mouseY);
        if (problem != null) {
            centered(g, problem, width / 2, height - 44, 0xFF6060);
        } else if (key != null) {
            centered(g, SearchScreen.tr("config.stashradar." + key + ".tip").getString(), width / 2, height - 44, 0xC0C0C0);
        } else {
            centered(g, SearchScreen.tr("screen.stashradar.config.hover").getString(), width / 2, height - 44, 0x808080);
        }
    }





    private void centered(GuiGraphics g, String s, int x, int y, int color) { g.drawCenteredString(font, s, x, y, 0xFF000000 | color); }
    private void text(GuiGraphics g, String s, int x, int y, int color) { g.drawString(font, s, x, y, 0xFF000000 | color); }





    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
