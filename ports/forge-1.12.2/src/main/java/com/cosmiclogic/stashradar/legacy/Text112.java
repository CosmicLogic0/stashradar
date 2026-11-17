package com.cosmiclogic.stashradar.legacy;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/** Chat text helpers for 1.12.2 (same look as the other versions). */
final class Text112 {

    private Text112() {
    }

    static ITextComponent of(String s, TextFormatting color) {
        return new TextComponentString(s).setStyle(new Style().setColor(color));
    }

    static ITextComponent prefix() {
        return of("[", TextFormatting.DARK_GRAY)
                .appendSibling(of("Stash Radar", TextFormatting.GREEN))
                .appendSibling(of("] ", TextFormatting.DARK_GRAY));
    }

    static String itemName(Item item) {
        try {
            return new ItemStack(item).getDisplayName();
        } catch (Throwable t) {
            return ContainerSearch112.itemId(item);
        }
    }
}
