package com.cosmiclogic.stashradar.search;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** ServerPlayer helpers whose names changed between versions. */
public final class Players {

    private Players() {
    }

    public static ServerLevel level(ServerPlayer player) {



        return player.serverLevel();



    }

    public static MinecraftServer server(ServerPlayer player) {
        return level(player).getServer();
    }
}
