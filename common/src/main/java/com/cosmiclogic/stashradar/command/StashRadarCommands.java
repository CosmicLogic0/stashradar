package com.cosmiclogic.stashradar.command;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;
import com.cosmiclogic.stashradar.search.ContainerSearch;
import com.cosmiclogic.stashradar.search.Highlighter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /find <item> [range]   - everyone (config permissionLevel), vanilla clients included
 * /stashradar reload     - operators
 * /stashradar selftest   - server console only (used by CI)
 */
public final class StashRadarCommands {

    private static final Map<UUID, Long> LAST_SEARCH = new ConcurrentHashMap<>();

    private StashRadarCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("find")
                .requires(src -> src.hasPermission(StashRadar.config().permissionLevel))
                .then(Commands.argument("item", ItemArgument.item(context))
                        .executes(ctx -> find(ctx, -1))
                        .then(Commands.argument("range", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> find(ctx, IntegerArgumentType.getInteger(ctx, "range"))))));

        dispatcher.register(Commands.literal("stashradar")
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            StashRadar.reloadConfig();
                            ctx.getSource().sendSuccess(() -> prefix().append(Component.literal("Config reloaded.").withStyle(ChatFormatting.GREEN)), true);
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
                source.sendFailure(prefix().append(Component.literal("Wait " + left + "s before searching again.").withStyle(ChatFormatting.GRAY)));
                return 0;
            }
            LAST_SEARCH.put(player.getUUID(), now);
        }

        Vec3 eye = player.getEyePosition();
        List<ContainerSearch.Hit> hits = ContainerSearch.search(player.serverLevel(), eye, item, range,
                new ContainerSearch.Options(config.containers, config.searchInsideItems));

        if (hits.isEmpty()) {
            Highlighter.stop(player.getUUID());
            source.sendSuccess(() -> prefix()
                    .append(Component.literal("No ").withStyle(ChatFormatting.GRAY))
                    .append(item.getDescription().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" in containers within " + range + " blocks.").withStyle(ChatFormatting.GRAY)), false);
            return 0;
        }

        int total = hits.stream().mapToInt(ContainerSearch.Hit::count).sum();
        ContainerSearch.Hit nearest = hits.get(0);
        String where = ContainerSearch.direction(eye, nearest.pos());
        source.sendSuccess(() -> prefix()
                .append(Component.literal("Found ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(total + " ").withStyle(ChatFormatting.WHITE))
                .append(item.getDescription().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" in ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(String.valueOf(hits.size())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(hits.size() == 1 ? " container. " : " containers. ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Nearest: ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(Math.round(nearest.distance()) + " blocks " + where).withStyle(ChatFormatting.WHITE)), false);

        List<BlockPos> shown = hits.stream().limit(Math.max(1, config.maxHighlights)).map(ContainerSearch.Hit::pos).toList();
        Highlighter.show(player, shown, Math.max(1, config.highlightSeconds), Highlighter.parseColor(config.highlightColor, 0x3CFF7A));
        return hits.size();
    }

    static MutableComponent prefix() {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Stash Radar").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }
}
