package com.cosmiclogic.stashradar.search;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.minecraft.core.registries.BuiltInRegistries;




/** Registry ids ("minecraft:chest") on every version. */
public final class Ids {

    private Ids() {
    }

    public static String block(Block block) {

        return BuiltInRegistries.BLOCK.getKey(block).toString();



    }

    public static String item(Item item) {

        return BuiltInRegistries.ITEM.getKey(item).toString();



    }

    /** The item with this id ("diamond" means "minecraft:diamond"), or null. */
    public static Item findItem(String id) {
        String full = id.indexOf(':') >= 0 ? id : "minecraft:" + id;
        for (Item item : allItems()) {
            if (item(item).equals(full)) {
                return item;
            }
        }
        return null;
    }

    /** The block with this full id ("minecraft:copper_chest"), or null. */
    public static Block findBlock(String id) {

        for (Block block : BuiltInRegistries.BLOCK) {



            if (block(block).equals(id)) {
                return block;
            }
        }
        return null;
    }

    public static Iterable<Item> allItems() {

        return BuiltInRegistries.ITEM;



    }
}
