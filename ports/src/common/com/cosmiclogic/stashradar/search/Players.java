package com.cosmiclogic.stashradar.search;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** ServerPlayer helpers whose names changed between versions. */
public final class Players {

    private Players() {
    }

    public static ServerLevel level(ServerPlayer player) {
        //#if MC >= 1.21.6
        return player.level();
        //#elif MC >= 1.20
        return player.serverLevel();
        //#else
        return player.getLevel();
        //#endif
    }

    public static MinecraftServer server(ServerPlayer player) {
        //#if MC >= 1.21.9
        return player.level().getServer();
        //#else
        return player.server;
        //#endif
    }
}
