package com.cosmiclogic.stashradar.command;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;
import com.cosmiclogic.stashradar.StashRadarRules;
import com.cosmiclogic.stashradar.search.ContainerSearch;
import com.cosmiclogic.stashradar.search.Highlighter;
import com.cosmiclogic.stashradar.search.Ids;
import com.cosmiclogic.stashradar.search.Players;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.minecraft.commands.CommandBuildContext;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Commands:
 * <ul>
 *   <li>{@code /find <item or #tag> [range]}: everyone (config permissionLevel), vanilla clients included</li>
 *   <li>{@code /find name <text> [range]}: items whose name contains the text (renamed boxes too)</li>
 *   <li>{@code /find help}, {@code /stashradar help}</li>
 *   <li>{@code /stashradar reload}: operators</li>
 *   <li>{@code /stashradar selftest}: server console only (used by CI)</li>
 * </ul>
 */
public final class StashRadarCommands {

    /** How many containers are listed by name and position in the chat answer. */
    static final int LISTED = 3;

    static final StashRadarRules.Cooldowns COOLDOWNS = new StashRadarRules.Cooldowns();

    private StashRadarCommands() {
    }


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {



        dispatcher.register(Commands.literal("find")
                .requires(src -> Text.op(src, StashRadar.config().permissionLevel))
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("name")
                        .then(Commands.argument("text", StringArgumentType.string())
                                .executes(ctx -> findName(ctx, -1))
                                .then(Commands.argument("range", IntegerArgumentType.integer(1, 256))
                                        .executes(ctx -> findName(ctx, IntegerArgumentType.getInteger(ctx, "range"))))))

                .then(Commands.argument("item", ItemPredicateArgument.itemPredicate(context))



                        .executes(ctx -> findItem(ctx, -1))
                        .then(Commands.argument("range", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> findItem(ctx, IntegerArgumentType.getInteger(ctx, "range"))))));

        dispatcher.register(Commands.literal("stashradar")
                .executes(ctx -> adminHelp(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> adminHelp(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(src -> Text.op(src, 2))
                        .executes(ctx -> {
                            StashRadar.reloadConfig();
                            Text.ok(ctx.getSource(), Text.prefix().append(Text.of("Config reloaded.", ChatFormatting.GREEN)), true);
                            return 1;
                        }))
                .then(Commands.literal("selftest")
                        .requires(src -> Text.op(src, 4) && src.getEntity() == null)
                        .executes(ctx -> SelfTest.run(ctx.getSource().getServer()))));
    }

    // ---------------------------------------------------------------- help

    static int help(CommandSourceStack source) {
        StashRadarConfig c = StashRadar.config();
        Text.ok(source, Text.prefix().append(Text.of("finds items in nearby chests, barrels and shulker boxes.", ChatFormatting.GRAY)), false);
        line(source, "/find <item> [range]", "e.g. /find diamond or /find diamond 32");
        line(source, "/find #<tag> [range]", "a group of items, e.g. /find #logs");
        line(source, "/find name <text> [range]", "names contain the text, e.g. /find name sword or a renamed box");
        line(source, "Search screen", "press K (needs the mod on your game; change it in Controls)");
        Text.ok(source, Text.of("Range " + c.range + " by default, up to " + Math.max(1, c.maxRange) + " blocks. "
                + (c.cooldownSeconds > 0 ? "Wait " + c.cooldownSeconds + "s between searches. " : "")
                + "Results glow for " + c.highlightSeconds + "s.", ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    static int adminHelp(CommandSourceStack source) {
        Text.ok(source, Text.prefix().append(Text.of("server commands:", ChatFormatting.GRAY)), false);
        line(source, "/find help", "how players search");
        line(source, "/stashradar reload", "reload config/stashradar.json (operators)");
        line(source, "/stashradar help", "this list");
        return 1;
    }

    private static void line(CommandSourceStack source, String cmd, String what) {
        Text.ok(source, Text.of(" " + cmd, ChatFormatting.WHITE).append(Text.of("  " + what, ChatFormatting.GRAY)), false);
    }

    // ---------------------------------------------------------------- searching

    private static int findItem(CommandContext<CommandSourceStack> ctx, int requestedRange) throws CommandSyntaxException {
        Predicate<ItemStack> match = ItemPredicateArgument.getItemPredicate(ctx, "item");
        return find(ctx.getSource(), match, label(argText(ctx, "item")), requestedRange);
    }

    private static int findName(CommandContext<CommandSourceStack> ctx, int requestedRange) throws CommandSyntaxException {
        String text = StringArgumentType.getString(ctx, "text");
        return find(ctx.getSource(), ContainerSearch.named(text),
                Text.of("items named \"" + text + "\"", ChatFormatting.WHITE), requestedRange);
    }

    /** The text the player typed for an argument ("#minecraft:logs", "diamond"). */
    static String argText(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) {
                return node.getRange().get(ctx.getInput());
            }
        }
        return name;
    }

    /** "diamond" shows as the item's name, "#minecraft:logs" as "#logs". */
    static MutableComponent label(String typed) {
        String t = typed.trim();
        if (t.startsWith("#")) {
            return Text.of(t.startsWith("#minecraft:") ? "#" + t.substring(11) : t, ChatFormatting.WHITE);
        }
        int cut = t.length();
        for (char c : new char[]{'[', '{'}) {
            int i = t.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        Item item = Ids.findItem(t.substring(0, cut));
        return item != null ? Text.itemName(item).withStyle(ChatFormatting.WHITE) : Text.of(t, ChatFormatting.WHITE);
    }

    private static int find(CommandSourceStack source, Predicate<ItemStack> match, MutableComponent what, int requestedRange)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StashRadarConfig config = StashRadar.config();
        int range = StashRadarRules.range(requestedRange, config);

        if (!Text.op(source, 2)) {
            long left = COOLDOWNS.tryUse(player.getUUID(), System.currentTimeMillis(), Math.max(0, config.cooldownSeconds) * 1000L);
            if (left > 0) {
                source.sendFailure(Text.prefix().append(Text.of("Wait " + ((left + 999) / 1000) + "s before searching again.", ChatFormatting.GRAY)));
                return 0;
            }
        }


        Vec3 eye = player.getEyePosition();



        List<ContainerSearch.Hit> hits = ContainerSearch.search(Players.level(player), eye, match, range,
                new ContainerSearch.Options(config.containers, config.searchInsideItems, config.searchItemFrames));
        int ender = config.searchEnderChest ? ContainerSearch.count(player.getEnderChestInventory(), match, config.searchInsideItems) : 0;

        if (StashRadarRules.rangeWasLimited(requestedRange, config)) {
            Text.ok(source, Text.prefix().append(Text.of("This server searches at most " + range + " blocks, so that's what I used.", ChatFormatting.GRAY)), false);
        }

        if (hits.isEmpty()) {
            Highlighter.stop(player);
            MutableComponent msg = Text.prefix()
                    .append(Text.of("No ", ChatFormatting.GRAY))
                    .append(what.copy())
                    .append(Text.of(" in containers within " + range + " blocks.", ChatFormatting.GRAY));
            if (range < Math.max(1, config.maxRange)) {
                msg.append(Text.of(" Try a bigger range (up to " + config.maxRange + ").", ChatFormatting.DARK_GRAY));
            }
            Text.ok(source, msg, false);
            if (ender > 0) {
                Text.ok(source, Text.of(" You have " + ender + " in your ender chest.", ChatFormatting.LIGHT_PURPLE), false);
            }
            return 0;
        }

        int total = 0;
        for (ContainerSearch.Hit hit : hits) {
            total += hit.count();
        }
        ContainerSearch.Hit nearest = hits.get(0);
        MutableComponent msg = Text.prefix()
                .append(Text.of("Found ", ChatFormatting.GREEN))
                .append(Text.of(total + " ", ChatFormatting.WHITE))
                .append(what.copy())
                .append(Text.of(" in ", ChatFormatting.GREEN))
                .append(Text.of(String.valueOf(hits.size()), ChatFormatting.WHITE))
                .append(Text.of(hits.size() == 1 ? " container. " : " containers. ", ChatFormatting.GREEN))
                .append(Text.of("Nearest: ", ChatFormatting.GREEN))
                .append(Text.of(Math.round(nearest.distance()) + " blocks " + ContainerSearch.direction(eye, nearest.pos()), ChatFormatting.WHITE));
        Text.ok(source, msg, false);
        for (int i = 0; i < Math.min(LISTED, hits.size()); i++) {
            ContainerSearch.Hit hit = hits.get(i);
            BlockPos p = hit.pos();
            Text.ok(source, Text.of(" " + hit.count() + " in " + StashRadarRules.pretty(hit.blockId()), ChatFormatting.WHITE)
                    .append(Text.of(" at " + p.getX() + ", " + p.getY() + ", " + p.getZ()
                            + " (" + Math.round(hit.distance()) + " blocks " + ContainerSearch.direction(eye, p) + ")", ChatFormatting.GRAY)), false);
        }
        if (hits.size() > LISTED) {
            Text.ok(source, Text.of(" ...and " + (hits.size() - LISTED) + " more, all outlined.", ChatFormatting.GRAY), false);
        }
        if (ender > 0) {
            Text.ok(source, Text.of(" Also " + ender + " in your ender chest.", ChatFormatting.LIGHT_PURPLE), false);
        }

        List<BlockPos> shown = new ArrayList<>();
        for (ContainerSearch.Hit hit : hits) {
            if (shown.size() >= Math.max(1, config.maxHighlights)) {
                break;
            }
            shown.add(hit.pos());
        }
        Highlighter.show(player, shown, Math.max(1, config.highlightSeconds),
                Highlighter.parseColor(config.highlightColor, 0x3CFF7A), config.glowThroughWalls);
        return hits.size();
    }
}
