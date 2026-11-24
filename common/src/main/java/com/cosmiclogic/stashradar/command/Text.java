package com.cosmiclogic.stashradar.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;




/** Chat text helpers that work on every version. */
final class Text {

    private Text() {
    }

    static MutableComponent of(String s, ChatFormatting color) {

        return Component.literal(s).withStyle(color);



    }

    /** An item's display name. Item.getDescription() is gone from 1.21.11. */
    static MutableComponent itemName(net.minecraft.world.item.Item item) {



        return item.getDescription().copy();

    }

    static MutableComponent prefix() {
        return of("[", ChatFormatting.DARK_GRAY)
                .append(of("Stash Radar", ChatFormatting.GREEN))
                .append(of("] ", ChatFormatting.DARK_GRAY));
    }

    /** Permission level check (0 everyone ... 4 console). 1.21.11 replaced levels with permission sets. */
    static boolean op(CommandSourceStack s, int level) {




        return s.hasPermission(level);

    }

    static void ok(CommandSourceStack source, Component message, boolean toOps) {

        source.sendSuccess(() -> message, toOps);



    }
}
