package com.cosmiclogic.stashradar.pageshots;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The gallery scene, built with ordinary vanilla commands on the Superflat world (Classic Flat: grass on top at
 * y = -61). A small open-air storage deck with a wall of chests, barrels and shulker boxes, and to the west a yard
 * with a few containers spread around a field. Contents are what a normal survival base would hold.
 */
final class Scene {

    /** Middle of the yard. */
    static final int YX = -30, YZ = 8;
    private static final int Y = -60;   // first block above the grass

    private Scene() {
    }

    private static String item(int slot, String id, int count) {
        return String.format(Locale.ROOT, "{Slot:%db,id:\"minecraft:%s\",count:%d}", slot, id, count);
    }

    /** A shulker box ITEM with something inside (counts for "looks inside shulker box items"). */
    private static String shulkerItem(int slot, String color, String id, int count) {
        return String.format(Locale.ROOT,
                "{Slot:%db,id:\"minecraft:%s_shulker_box\",count:1,components:{\"minecraft:container\":[{slot:0,item:{id:\"minecraft:%s\",count:%d}},{slot:1,item:{id:\"minecraft:%s\",count:%d}}]}}",
                slot, color, id, count, id, Math.max(1, count / 2));
    }

    private static String box(String block, int x, int y, int z, String... items) {
        return String.format(Locale.ROOT, "setblock %d %d %d minecraft:%s{Items:[%s]} replace", x, y, z, block, String.join(",", items));
    }

    private static final String CHEST = "chest[facing=south,type=single]";
    private static final String BARREL = "barrel[facing=south]";

    static List<String> build() {
        List<String> c = new ArrayList<>();
        // clear the air around the scene (flat worlds can have animals; they were killed before)
        c.add("fill -2 -60 -2 16 -50 13 air");
        c.add(String.format(Locale.ROOT, "fill %d -60 %d %d -50 %d air", YX - 14, YZ - 16, YX + 14, YZ + 16));

        // ---- storage deck: spruce floor, oak wall with log pillars, torches
        c.add("fill 0 -61 0 14 -61 10 minecraft:spruce_planks");
        c.add("fill 0 -60 11 14 -57 11 minecraft:oak_planks");
        c.add("fill 0 -56 11 14 -56 11 minecraft:stripped_oak_log[axis=x]");
        for (int x : new int[]{0, 7, 14}) {
            c.add(String.format(Locale.ROOT, "fill %d -60 11 %d -56 11 minecraft:oak_log", x, x));
        }
        c.add("setblock 3 -57 10 minecraft:wall_torch[facing=north]");
        c.add("setblock 11 -57 10 minecraft:wall_torch[facing=north]");
        c.add("setblock 0 -60 9 minecraft:crafting_table");
        c.add("setblock 14 -60 9 minecraft:smoker[facing=west]");
        c.add("setblock 14 -59 9 minecraft:potted_fern");

        // ---- the storage wall (row y=-60 and y=-59, x = 1..13, z = 10)
        // bottom row
        c.add(box(CHEST, 1, Y, 10, item(0, "cobblestone", 64), item(1, "cobblestone", 64), item(2, "stone", 40)));
        c.add(box(CHEST, 2, Y, 10, item(0, "bread", 24), item(3, "carrot", 31), shulkerItem(5, "lime", "emerald", 32)));
        c.add(box(BARREL, 3, Y, 10, item(0, "oak_log", 64), item(1, "oak_log", 48)));
        c.add(box(BARREL, 4, Y, 10, item(0, "coal", 52), item(1, "redstone", 30)));
        c.add(box(CHEST, 5, Y, 10, item(0, "diamond", 12), item(1, "gold_ingot", 9), item(2, "lapis_lazuli", 20)));
        c.add(box(CHEST, 6, Y, 10, item(0, "wheat", 64), item(1, "wheat_seeds", 40)));
        c.add(box("blue_shulker_box[facing=up]", 7, Y, 10, item(0, "arrow", 64), item(1, "bow", 1)));
        c.add(box(CHEST, 8, Y, 10, item(0, "sand", 64), item(1, "gravel", 32)));
        c.add(box(CHEST, 9, Y, 10, item(0, "string", 18), item(1, "leather", 11)));
        c.add(box(BARREL, 10, Y, 10, item(0, "diamond", 30), item(4, "obsidian", 14)));
        c.add(box(BARREL, 11, Y, 10, item(0, "spruce_log", 64)));
        c.add(box(CHEST, 12, Y, 10, item(0, "torch", 64), item(1, "ladder", 20)));
        c.add(box(CHEST, 13, Y, 10, item(0, "copper_ingot", 40), item(1, "quartz", 22)));
        // top row
        c.add(box(BARREL, 1, Y + 1, 10, item(0, "andesite", 64)));
        c.add(box(BARREL, 2, Y + 1, 10, item(0, "glass", 48)));
        c.add(box(CHEST, 3, Y + 1, 10, item(0, "bone", 20), item(1, "rotten_flesh", 33)));
        c.add(box(CHEST, 4, Y + 1, 10, item(0, "flint", 9), item(1, "feather", 17)));
        c.add(box("white_shulker_box[facing=up]", 5, Y + 1, 10, item(0, "white_wool", 40)));
        c.add(box("purple_shulker_box[facing=up]", 6, Y + 1, 10, item(0, "diamond", 9), item(1, "ender_pearl", 8)));
        c.add(box("yellow_shulker_box[facing=up]", 7, Y + 1, 10, item(0, "honey_bottle", 6)));
        c.add(box(BARREL, 8, Y + 1, 10, item(0, "pumpkin", 21)));
        c.add(box(BARREL, 9, Y + 1, 10, item(0, "melon_slice", 50)));
        c.add(box(CHEST, 10, Y + 1, 10, item(0, "book", 12), item(1, "paper", 30)));
        c.add(box(CHEST, 11, Y + 1, 10, item(0, "slime_ball", 5), shulkerItem(2, "cyan", "diamond", 20)));
        c.add(box("red_shulker_box[facing=up]", 12, Y + 1, 10, item(0, "tnt", 4)));
        c.add(box("green_shulker_box[facing=up]", 13, Y + 1, 10, item(0, "cactus", 30)));

        // ---- the yard: a little field with water, fences, hay, and containers spread around
        int x = YX, z = YZ;
        c.add(String.format(Locale.ROOT, "fill %d -61 %d %d -61 %d minecraft:farmland[moisture=7]", x - 3, z - 2, x + 3, z + 2));
        c.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 %d minecraft:wheat[age=7]", x - 3, z - 2, x + 3, z + 2));
        c.add(String.format(Locale.ROOT, "setblock %d -61 %d minecraft:water", x, z));
        c.add(String.format(Locale.ROOT, "setblock %d -60 %d minecraft:air", x, z));
        c.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 %d minecraft:oak_fence", x - 4, z - 3, x + 4, z - 3));
        c.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 %d minecraft:oak_fence", x - 4, z + 3, x + 4, z + 3));
        c.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 %d minecraft:oak_fence", x - 4, z - 2, x - 4, z + 2));
        c.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 %d minecraft:oak_fence", x + 4, z - 2, x + 4, z + 2));
        c.add(String.format(Locale.ROOT, "fill %d -61 %d %d -61 %d minecraft:dirt_path", x - 9, z - 5, x + 9, z - 5));
        c.add(String.format(Locale.ROOT, "setblock %d -60 %d minecraft:hay_block", x - 6, z + 5));
        c.add(String.format(Locale.ROOT, "setblock %d -59 %d minecraft:hay_block", x - 6, z + 5));
        c.add(String.format(Locale.ROOT, "setblock %d -60 %d minecraft:hay_block", x - 7, z + 5));
        c.add(String.format(Locale.ROOT, "setblock %d -60 %d minecraft:crafting_table", x + 7, z - 7));
        c.add(String.format(Locale.ROOT, "setblock %d -60 %d minecraft:furnace[facing=north]", x + 8, z - 7));
        // containers with iron (outlined) and without (not outlined)
        c.add(box(BARREL, x - 8, Y, z - 3, item(0, "iron_ingot", 24), item(1, "coal", 16)));
        c.add(box(CHEST, x + 7, Y, z + 2, item(0, "iron_ingot", 9), item(1, "bread", 6)));
        c.add(box(CHEST, x - 2, Y, z + 7, item(0, "iron_ingot", 40)));
        c.add(box(BARREL, x + 9, Y, z - 3, item(0, "iron_ingot", 5), item(1, "wheat_seeds", 30)));
        c.add(box("orange_shulker_box[facing=up]", x + 3, Y, z + 8, item(0, "iron_ingot", 16)));
        c.add(box(CHEST, x - 9, Y, z + 3, item(0, "wheat", 20)));
        c.add(box(CHEST, x + 1, Y, z - 8, item(0, "pumpkin_seeds", 12)));
        return c;
    }

    /** Some ordinary tools and blocks in the hotbar for the shots that show the HUD. */
    static List<String> hotbar() {
        List<String> c = new ArrayList<>();
        String[] items = {"diamond_pickaxe", "chest", "barrel", "purple_shulker_box", "torch 32", "oak_planks 64", "bread 16", "compass", "spyglass"};
        for (int i = 0; i < items.length; i++) {
            c.add("item replace entity @s hotbar." + i + " with minecraft:" + items[i]);
        }
        return c;
    }
}
