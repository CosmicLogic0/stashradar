package com.cosmiclogic.stashradar.search;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.19.3 && MC < 1.21.2
import org.joml.Vector3f;
//#endif

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Outlines the found containers with dust particles that only the searching player receives, so it
 * works for players without the mod too. A tiny timer thread asks the server thread to redraw every
 * half second until the highlight time runs out (no loader-specific tick events needed).
 */
public final class Highlighter {

    private static final class Highlight {
        final MinecraftServer server;
        final ServerLevel level;
        final List<BlockPos> positions;
        final DustParticleOptions dust;
        final long endMillis;

        Highlight(MinecraftServer server, ServerLevel level, List<BlockPos> positions, DustParticleOptions dust, long endMillis) {
            this.server = server;
            this.level = level;
            this.positions = positions;
            this.dust = dust;
            this.endMillis = endMillis;
        }
    }

    private static final Map<UUID, Highlight> ACTIVE = new ConcurrentHashMap<>();
    private static ScheduledExecutorService timer;

    private Highlighter() {
    }

    public static void show(ServerPlayer player, List<BlockPos> positions, int seconds, int rgb) {
        if (positions.isEmpty()) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        ACTIVE.put(player.getUUID(), new Highlight(Players.server(player), Players.level(player), new ArrayList<>(positions),
                dust(rgb), System.currentTimeMillis() + seconds * 1000L));
        draw(player.getUUID());
        startTimer();
    }

    private static DustParticleOptions dust(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        //#if MC >= 1.21.2
        return new DustParticleOptions(rgb, 1.0f);
        //#elif MC >= 1.19.3
        return new DustParticleOptions(new Vector3f(r, g, b), 1.0f);
        //#elif MC >= 1.17
        return new DustParticleOptions(new com.mojang.math.Vector3f(r, g, b), 1.0f);
        //#else
        return new DustParticleOptions(r, g, b, 1.0f);
        //#endif
    }

    public static void stop(UUID player) {
        ACTIVE.remove(player);
    }

    private static synchronized void startTimer() {
        if (timer != null) {
            return;
        }
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Stash Radar highlighter");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleAtFixedRate(Highlighter::tick, 500, 500, TimeUnit.MILLISECONDS);
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Highlight> e : ACTIVE.entrySet()) {
            Highlight h = e.getValue();
            if (now > h.endMillis || !h.server.isRunning()) {
                ACTIVE.remove(e.getKey(), h);
                continue;
            }
            final UUID id = e.getKey();
            h.server.execute(() -> draw(id));
        }
    }

    /** Runs on the server thread. */
    private static void draw(UUID id) {
        Highlight h = ACTIVE.get(id);
        if (h == null) {
            return;
        }
        ServerPlayer player = h.server.getPlayerList().getPlayer(id);
        if (player == null || Players.level(player) != h.level) {
            ACTIVE.remove(id, h);
            return;
        }
        for (BlockPos pos : h.positions) {
            box(player, h.level, h.dust, pos);
        }
    }

    /** 8 corners + the middle of the 12 edges: a clear outline without flooding the connection. */
    private static void box(ServerPlayer player, ServerLevel level, DustParticleOptions dust, BlockPos pos) {
        double x0 = pos.getX() - 0.02, y0 = pos.getY() - 0.02, z0 = pos.getZ() - 0.02;
        double x1 = pos.getX() + 1.02, y1 = pos.getY() + 1.02, z1 = pos.getZ() + 1.02;
        double xm = (x0 + x1) / 2, ym = (y0 + y1) / 2, zm = (z0 + z1) / 2;
        double[][] points = {
                {x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1},
                {xm, y0, z0}, {xm, y1, z0}, {xm, y0, z1}, {xm, y1, z1},
                {x0, ym, z0}, {x1, ym, z0}, {x0, ym, z1}, {x1, ym, z1},
                {x0, y0, zm}, {x1, y0, zm}, {x0, y1, zm}, {x1, y1, zm}};
        for (double[] p : points) {
            //#if MC >= 1.21.4
            level.sendParticles(player, dust, true, true, p[0], p[1], p[2], 1, 0, 0, 0, 0);
            //#else
            level.sendParticles(player, dust, true, p[0], p[1], p[2], 1, 0, 0, 0, 0);
            //#endif
        }
    }

    public static int parseColor(String hex, int fallback) {
        try {
            String h = hex.trim();
            if (h.startsWith("#")) {
                h = h.substring(1);
            }
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }
}
