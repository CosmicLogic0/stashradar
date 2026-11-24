package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;
import com.cosmiclogic.stashradar.StashRadarRules;
import net.minecraft.item.ItemStack;
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
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.Collections;
import java.util.List;

/**
 * {@code /find <item> [range]}, {@code /find name <text> [range]}, {@code /find help}: everyone (config
 * permissionLevel), vanilla clients included. Item tags (#logs) need 1.13+, so 1.12.2 explains that.
 */
public class FindCommand112 extends CommandBase {

    static final StashRadarRules.Cooldowns COOLDOWNS = new StashRadarRules.Cooldowns();

    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/find <item> [range] | /find name <text> [range] | /find help";
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
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return;
        }
        if (args[0].startsWith("#")) {
            sender.sendMessage(Text112.prefix().appendSibling(Text112.of("Item tags need Minecraft 1.13 or newer. Try /find name <text>.", TextFormatting.GRAY)));
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        StashRadarConfig config = StashRadar.config();
        Predicate<ItemStack> match;
        String name;
        int requested = -1;
        if (args[0].equalsIgnoreCase("name") && args.length >= 2) {
            int end = args.length;
            if (args.length >= 3 && args[args.length - 1].matches("\\d+")) {
                requested = parseInt(args[args.length - 1], 1, 256);
                end--;
            }
            String text = String.join(" ", Arrays.copyOfRange(args, 1, end)).replace("\"", "");
            match = ContainerSearch112.named(text);
            name = "items named \"" + text + "\"";
        } else {
            Item item = getItemByText(sender, args[0]);
            requested = args.length >= 2 ? parseInt(args[1], 1, 256) : -1;
            match = ContainerSearch112.of(item);
            name = Text112.itemName(item);
        }
        int range = StashRadarRules.range(requested, config);

        if (!sender.canUseCommand(2, "stashradar")) {
            long left = COOLDOWNS.tryUse(player.getUniqueID(), System.currentTimeMillis(), Math.max(0, config.cooldownSeconds) * 1000L);
            if (left > 0) {
                sender.sendMessage(Text112.prefix().appendSibling(Text112.of("Wait " + ((left + 999) / 1000) + "s before searching again.", TextFormatting.GRAY)));
                return;
            }
        }
        if (StashRadarRules.rangeWasLimited(requested, config)) {
            sender.sendMessage(Text112.prefix().appendSibling(Text112.of("This server searches at most " + range + " blocks, so that's what I used.", TextFormatting.GRAY)));
        }

        Vec3d eye = player.getPositionEyes(1.0f);
        List<ContainerSearch112.Hit> hits = ContainerSearch112.search(player.getServerWorld(), eye, match, range,
                config.containers, config.searchInsideItems, config.searchItemFrames);
        int ender = config.searchEnderChest ? ContainerSearch112.count(player.getInventoryEnderChest(), match, config.searchInsideItems) : 0;
        if (hits.isEmpty()) {
            Highlighter112.stop(player);
            ITextComponent msg = Text112.prefix()
                    .appendSibling(Text112.of("No ", TextFormatting.GRAY))
                    .appendSibling(Text112.of(name, TextFormatting.WHITE))
                    .appendSibling(Text112.of(" in containers within " + range + " blocks.", TextFormatting.GRAY));
            if (range < Math.max(1, config.maxRange)) {
                msg.appendSibling(Text112.of(" Try a bigger range (up to " + config.maxRange + ").", TextFormatting.DARK_GRAY));
            }
            sender.sendMessage(msg);
            if (ender > 0) {
                sender.sendMessage(Text112.of(" You have " + ender + " in your ender chest.", TextFormatting.LIGHT_PURPLE));
            }
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
        for (int i = 0; i < Math.min(3, hits.size()); i++) {
            ContainerSearch112.Hit hit = hits.get(i);
            BlockPos p = hit.pos;
            sender.sendMessage(Text112.of(" " + hit.count + " in " + StashRadarRules.pretty(hit.blockId), TextFormatting.WHITE)
                    .appendSibling(Text112.of(" at " + p.getX() + ", " + p.getY() + ", " + p.getZ()
                            + " (" + Math.round(hit.distance) + " blocks " + ContainerSearch112.direction(eye, p) + ")", TextFormatting.GRAY)));
        }
        if (hits.size() > 3) {
            sender.sendMessage(Text112.of(" ...and " + (hits.size() - 3) + " more, all outlined.", TextFormatting.GRAY));
        }
        if (ender > 0) {
            sender.sendMessage(Text112.of(" Also " + ender + " in your ender chest.", TextFormatting.LIGHT_PURPLE));
        }

        List<BlockPos> shown = new ArrayList<>();
        for (ContainerSearch112.Hit hit : hits) {
            if (shown.size() >= Math.max(1, config.maxHighlights)) {
                break;
            }
            shown.add(hit.pos);
        }
        Highlighter112.show(player, shown, Math.max(1, config.highlightSeconds),
                Highlighter112.parseColor(config.highlightColor, 0x3CFF7A), config.glowThroughWalls);
    }

    static void help(ICommandSender sender) {
        StashRadarConfig c = StashRadar.config();
        sender.sendMessage(Text112.prefix().appendSibling(Text112.of("finds items in nearby chests and shulker boxes.", TextFormatting.GRAY)));
        line(sender, "/find <item> [range]", "e.g. /find diamond or /find diamond 32");
        line(sender, "/find name <text> [range]", "names contain the text, e.g. /find name sword or a renamed box");
        line(sender, "Search screen", "press K (needs the mod on your game; change it in Controls)");
        sender.sendMessage(Text112.of("Range " + c.range + " by default, up to " + Math.max(1, c.maxRange) + " blocks. "
                + (c.cooldownSeconds > 0 ? "Wait " + c.cooldownSeconds + "s between searches. " : "")
                + "Results glow for " + c.highlightSeconds + "s.", TextFormatting.DARK_GRAY));
    }

    static void line(ICommandSender sender, String cmd, String what) {
        sender.sendMessage(Text112.of(" " + cmd, TextFormatting.WHITE).appendSibling(Text112.of("  " + what, TextFormatting.GRAY)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(getListOfStringsMatchingLastWord(args, "help", "name"));
            out.addAll(getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()));
            return out;
        }
        return Collections.emptyList();
    }
}
