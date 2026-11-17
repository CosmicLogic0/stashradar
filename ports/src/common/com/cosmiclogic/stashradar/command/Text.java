package com.cosmiclogic.stashradar.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//#if MC < 1.19
import net.minecraft.network.chat.TextComponent;
//#endif

/** Chat text helpers that work on every version. */
final class Text {

    private Text() {
    }

    static MutableComponent of(String s, ChatFormatting color) {
        //#if MC >= 1.19
        return Component.literal(s).withStyle(color);
        //#else
        return new TextComponent(s).withStyle(color);
        //#endif
    }

    static MutableComponent prefix() {
        return of("[", ChatFormatting.DARK_GRAY)
                .append(of("Stash Radar", ChatFormatting.GREEN))
                .append(of("] ", ChatFormatting.DARK_GRAY));
    }

    /** Permission level check (0 everyone ... 4 console). 1.21.11 replaced levels with permission sets. */
    static boolean op(CommandSourceStack s, int level) {
        //#if MC >= 1.21.11
        return s.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level)));
        //#else
        return s.hasPermission(level);
        //#endif
    }

    static void ok(CommandSourceStack source, Component message, boolean toOps) {
        //#if MC >= 1.20
        source.sendSuccess(() -> message, toOps);
        //#else
        source.sendSuccess(message, toOps);
        //#endif
    }
}
