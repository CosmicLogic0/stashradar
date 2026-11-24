package com.cosmiclogic.stashradar.search;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
//#if MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
import net.minecraft.core.Registry;
//#endif

/** Registry ids ("minecraft:chest") on every version. */
public final class Ids {

    private Ids() {
    }

    public static String block(Block block) {
        //#if MC >= 1.19.3
        return BuiltInRegistries.BLOCK.getKey(block).toString();
        //#else
        return Registry.BLOCK.getKey(block).toString();
        //#endif
    }

    public static String item(Item item) {
        //#if MC >= 1.19.3
        return BuiltInRegistries.ITEM.getKey(item).toString();
        //#else
        return Registry.ITEM.getKey(item).toString();
        //#endif
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

    public static Iterable<Item> allItems() {
        //#if MC >= 1.19.3
        return BuiltInRegistries.ITEM;
        //#else
        return Registry.ITEM;
        //#endif
    }
}
