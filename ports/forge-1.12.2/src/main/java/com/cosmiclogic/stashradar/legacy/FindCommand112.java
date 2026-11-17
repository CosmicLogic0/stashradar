package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** {@code /find <item> [range]}: everyone (config permissionLevel), vanilla clients included. */
public class FindCommand112 extends CommandBase {

    private static final Map<UUID, Long> LAST_SEARCH = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/find <item> [range]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return StashRadar.config().permissionLevel;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        int level = StashRadar.config().permissionLevel;
        // 1.12.2 refuses level-0 commands for non-ops unless we say yes ourselves.
        return level <= 0 || sender.canUseCommand(level, getName());
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new CommandException(getUsage(sender));
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        Item item = getItemByText(sender, args[0]);
        StashRadarConfig config = StashRadar.config();
        int maxRange = Math.max(1, config.maxRange);
        int requested = args.length >= 2 ? parseInt(args[1], 1, 256) : -1;
        int range = Math.max(1, Math.min(requested > 0 ? requested : config.range, maxRange));

        if (!sender.canUseCommand(2, "stashradar")) {
            long cooldown = Math.max(0, config.cooldownSeconds) * 1000L;
            long now = System.currentTimeMillis();
            Long last = LAST_SEARCH.get(player.getUniqueID());
            if (last != null && now - last < cooldown) {
                long left = (cooldown - (now - last) + 999) / 1000;
                sender.sendMessage(Text112.prefix().appendSibling(Text112.of("Wait " + left + "s before searching again.", TextFormatting.GRAY)));
                return;
            }
            LAST_SEARCH.put(player.getUniqueID(), now);
        }

        Vec3d eye = player.getPositionEyes(1.0f);
        List<ContainerSearch112.Hit> hits = ContainerSearch112.search(player.getServerWorld(), eye, item, range,
                config.containers, config.searchInsideItems);
        String name = Text112.itemName(item);
        if (hits.isEmpty()) {
            Highlighter112.stop(player.getUniqueID());
            sender.sendMessage(Text112.prefix()
                    .appendSibling(Text112.of("No ", TextFormatting.GRAY))
                    .appendSibling(Text112.of(name, TextFormatting.WHITE))
                    .appendSibling(Text112.of(" in containers within " + range + " blocks.", TextFormatting.GRAY)));
            return;
        }
        int total = 0;
        for (ContainerSearch112.Hit hit : hits) {
            total += hit.count;
        }
        ContainerSearch112.Hit nearest = hits.get(0);
        ITextComponent msg = Text112.prefix()
                .appendSibling(Text112.of("Found ", TextFormatting.GREEN))
                .appendSibling(Text112.of(total + " " + name, TextFormatting.WHITE))
                .appendSibling(Text112.of(" in ", TextFormatting.GREEN))
                .appendSibling(Text112.of(String.valueOf(hits.size()), TextFormatting.WHITE))
                .appendSibling(Text112.of(hits.size() == 1 ? " container. " : " containers. ", TextFormatting.GREEN))
                .appendSibling(Text112.of("Nearest: ", TextFormatting.GREEN))
                .appendSibling(Text112.of(Math.round(nearest.distance) + " blocks " + ContainerSearch112.direction(eye, nearest.pos), TextFormatting.WHITE));
        sender.sendMessage(msg);

        List<BlockPos> shown = new ArrayList<>();
        for (ContainerSearch112.Hit hit : hits) {
            if (shown.size() >= Math.max(1, config.maxHighlights)) {
                break;
            }
            shown.add(hit.pos);
        }
        Highlighter112.show(player, shown, Math.max(1, config.highlightSeconds), Highlighter112.parseColor(config.highlightColor, 0x3CFF7A));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys());
        }
        return Collections.emptyList();
    }
}
