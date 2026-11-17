package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.stashradar.StashRadar;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/** {@code /stashradar reload} (operators) and {@code /stashradar selftest} (server console only, used by CI). */
public class StashRadarCommand112 extends CommandBase {

    @Override
    public String getName() {
        return "stashradar";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/stashradar reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length > 0 ? args[0] : "";
        if (sub.equals("reload")) {
            StashRadar.reloadConfig();
            sender.sendMessage(Text112.prefix().appendSibling(Text112.of("Config reloaded.", TextFormatting.GREEN)));
            return;
        }
        if (sub.equals("selftest") && sender instanceof MinecraftServer) {
            SelfTest112.run(server);
            return;
        }
        throw new CommandException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload");
        }
        return Collections.emptyList();
    }
}
