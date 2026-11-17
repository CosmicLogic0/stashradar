package com.cosmiclogic.stashradar.command;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;
import com.cosmiclogic.stashradar.search.ContainerSearch;
import com.cosmiclogic.stashradar.search.Highlighter;
import com.cosmiclogic.stashradar.search.Players;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
//#if MC >= 1.19
import net.minecraft.commands.CommandBuildContext;
//#endif

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commands:
 * <ul>
 *   <li>{@code /find <item> [range]}: everyone (config permissionLevel), vanilla clients included</li>
 *   <li>{@code /stashradar reload}: operators</li>
 *   <li>{@code /stashradar selftest}: server console only (used by CI)</li>
 * </ul>
 */
public final class StashRadarCommands {

    private static final Map<UUID, Long> LAST_SEARCH = new ConcurrentHashMap<>();

    private StashRadarCommands() {
    }

    //#if MC >= 1.19
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
    //#else
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    //#endif
        dispatcher.register(Commands.literal("find")
                .requires(src -> src.hasPermission(StashRadar.config().permissionLevel))
                //#if MC >= 1.19
                .then(Commands.argument("item", ItemArgument.item(context))
                //#else
                .then(Commands.argument("item", ItemArgument.item())
                //#endif
                        .executes(ctx -> find(ctx, -1))
                        .then(Commands.argument("range", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> find(ctx, IntegerArgumentType.getInteger(ctx, "range"))))));

        dispatcher.register(Commands.literal("stashradar")
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            StashRadar.reloadConfig();
                            Text.ok(ctx.getSource(), Text.prefix().append(Text.of("Config reloaded.", ChatFormatting.GREEN)), true);
                            return 1;
                        }))
                .then(Commands.literal("selftest")
                        .requires(src -> src.hasPermission(4) && src.getEntity() == null)
                        .executes(ctx -> SelfTest.run(ctx.getSource().getServer()))));
    }

    private static int find(CommandContext<CommandSourceStack> ctx, int requestedRange) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        StashRadarConfig config = StashRadar.config();

        int maxRange = Math.max(1, config.maxRange);
        int range = Math.max(1, Math.min(requestedRange > 0 ? requestedRange : config.range, maxRange));

        if (!player.hasPermissions(2)) {
            long cooldown = Math.max(0, config.cooldownSeconds) * 1000L;
            long now = System.currentTimeMillis();
            Long last = LAST_SEARCH.get(player.getUUID());
            if (last != null && now - last < cooldown) {
                long left = (cooldown - (now - last) + 999) / 1000;
                source.sendFailure(Text.prefix().append(Text.of("Wait " + left + "s before searching again.", ChatFormatting.GRAY)));
                return 0;
            }
            LAST_SEARCH.put(player.getUUID(), now);
        }

        Vec3 eye = player.getEyePosition();
        List<ContainerSearch.Hit> hits = ContainerSearch.search(Players.level(player), eye, item, range,
                new ContainerSearch.Options(config.containers, config.searchInsideItems));

        if (hits.isEmpty()) {
            Highlighter.stop(player.getUUID());
            Text.ok(source, Text.prefix()
                    .append(Text.of("No ", ChatFormatting.GRAY))
                    .append(item.getDescription().copy().withStyle(ChatFormatting.WHITE))
                    .append(Text.of(" in containers within " + range + " blocks.", ChatFormatting.GRAY)), false);
            return 0;
        }

        int total = 0;
        for (ContainerSearch.Hit hit : hits) {
            total += hit.count();
        }
        ContainerSearch.Hit nearest = hits.get(0);
        String where = ContainerSearch.direction(eye, nearest.pos());
        MutableComponent msg = Text.prefix()
                .append(Text.of("Found ", ChatFormatting.GREEN))
                .append(Text.of(total + " ", ChatFormatting.WHITE))
                .append(item.getDescription().copy().withStyle(ChatFormatting.WHITE))
                .append(Text.of(" in ", ChatFormatting.GREEN))
                .append(Text.of(String.valueOf(hits.size()), ChatFormatting.WHITE))
                .append(Text.of(hits.size() == 1 ? " container. " : " containers. ", ChatFormatting.GREEN))
                .append(Text.of("Nearest: ", ChatFormatting.GREEN))
                .append(Text.of(Math.round(nearest.distance()) + " blocks " + where, ChatFormatting.WHITE));
        Text.ok(source, msg, false);

        List<BlockPos> shown = new ArrayList<>();
        for (ContainerSearch.Hit hit : hits) {
            if (shown.size() >= Math.max(1, config.maxHighlights)) {
                break;
            }
            shown.add(hit.pos());
        }
        Highlighter.show(player, shown, Math.max(1, config.highlightSeconds), Highlighter.parseColor(config.highlightColor, 0x3CFF7A));
        return hits.size();
    }
}
