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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Outlines the found containers with dust particles that only the searching player receives, so it
 * works for players without the mod too. Far-away results also get a glowing outline through walls
 * (see {@link Glow}). A tiny timer thread asks the server thread to redraw every half second until the
 * highlight time runs out (no loader-specific tick events needed).
 */
public final class Highlighter {

    /** Within this distance (blocks) the glow is taken away again, so the player can open the container. */
    static final double GLOW_MIN_DISTANCE = 5.5;
    /** Particles per redraw for all outlines together (keeps the connection light). */
    static final int POINT_BUDGET = 480;

    private static final class Highlight {
        final MinecraftServer server;
        final ServerLevel level;
        final List<BlockPos> positions;
        final DustParticleOptions dust;
        final long endMillis;
        final boolean glow;
        final Map<BlockPos, Glow.Ghost> ghosts = new HashMap<>();
        final Set<BlockPos> shown = new HashSet<>();

        Highlight(MinecraftServer server, ServerLevel level, List<BlockPos> positions, DustParticleOptions dust, long endMillis, boolean glow) {
            this.server = server;
            this.level = level;
            this.positions = positions;
            this.dust = dust;
            this.endMillis = endMillis;
            this.glow = glow;
        }
    }

    private static final Map<UUID, Highlight> ACTIVE = new ConcurrentHashMap<>();
    private static ScheduledExecutorService timer;

    private Highlighter() {
    }

    /** Runs on the server thread. Replaces the player's previous highlight. */
    public static void show(ServerPlayer player, List<BlockPos> positions, int seconds, int rgb, boolean glow) {
        stop(player);
        if (positions.isEmpty()) {
            return;
        }
        ACTIVE.put(player.getUUID(), new Highlight(Players.server(player), Players.level(player), new ArrayList<>(positions),
                dust(rgb), System.currentTimeMillis() + seconds * 1000L, glow));
        draw(player.getUUID());
        startTimer();
    }

    /** How many players have a highlight right now (used by the self-test). */
    public static int activeCount() {
        return ACTIVE.size();
    }

    private static DustParticleOptions dust(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        //#if MC >= 1.21.2
        return new DustParticleOptions(rgb, 1.3f);
        //#elif MC >= 1.19.3
        return new DustParticleOptions(new Vector3f(r, g, b), 1.3f);
        //#elif MC >= 1.17
        return new DustParticleOptions(new com.mojang.math.Vector3f(r, g, b), 1.3f);
        //#else
        return new DustParticleOptions(r, g, b, 1.3f);
        //#endif
    }

    /** Runs on the server thread: ends the player's highlight and takes the glow away. */
    public static void stop(ServerPlayer player) {
        Highlight h = ACTIVE.remove(player.getUUID());
        if (h != null) {
            hideAll(player, h);
        }
    }

    private static void hideAll(ServerPlayer player, Highlight h) {
        int[] ids = new int[h.shown.size()];
        int i = 0;
        for (BlockPos pos : h.shown) {
            ids[i++] = h.ghosts.get(pos).id;
        }
        h.shown.clear();
        try {
            Glow.hide(player, ids);
        } catch (Throwable ignored) {
            // the player just left
        }
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
            final Highlight h = e.getValue();
            final UUID id = e.getKey();
            if (!h.server.isRunning()) {
                ACTIVE.remove(id, h);
                continue;
            }
            if (now > h.endMillis) {
                h.server.execute(() -> {
                    if (ACTIVE.remove(id, h)) {
                        ServerPlayer player = h.server.getPlayerList().getPlayer(id);
                        if (player != null) {
                            hideAll(player, h);
                        }
                    }
                });
                continue;
            }
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
            ACTIVE.remove(id, h);   // left or changed dimension: their game already forgot the glow
            return;
        }
        int perEdge = Math.max(1, Math.min(6, (POINT_BUDGET / Math.max(1, h.positions.size()) - 8) / 12));
        for (BlockPos pos : h.positions) {
            box(player, h.level, h.dust, pos, perEdge);
            if (h.glow) {
                glow(player, h, pos);
            }
        }
    }

    private static void glow(ServerPlayer player, Highlight h, BlockPos pos) {
        double far = GLOW_MIN_DISTANCE * GLOW_MIN_DISTANCE;
        boolean wanted = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > far;
        try {
            if (wanted && !h.shown.contains(pos)) {
                Glow.Ghost ghost = h.ghosts.get(pos);
                if (ghost == null) {
                    ghost = Glow.build(h.level, pos);
                    h.ghosts.put(pos, ghost);
                }
                Glow.show(player, ghost);
                h.shown.add(pos);
            } else if (!wanted && h.shown.remove(pos)) {
                Glow.hide(player, h.ghosts.get(pos).id);
            }
        } catch (Throwable t) {
            // Never let the extra outline break the search; particles still work.
            com.cosmiclogic.stashradar.StashRadar.LOG.debug("Stash Radar glow skipped: {}", t.toString());
        }
    }

    /**
     * The 8 corners plus {@code perEdge} evenly spaced points on each of the 12 edges. Few results get a dense
     * outline; many results share the same particle budget, so a big search never floods the connection.
     */
    public static List<double[]> outline(BlockPos pos, int perEdge) {
        double x0 = pos.getX() - 0.02, y0 = pos.getY() - 0.02, z0 = pos.getZ() - 0.02;
        double x1 = pos.getX() + 1.02, y1 = pos.getY() + 1.02, z1 = pos.getZ() + 1.02;
        List<double[]> points = new ArrayList<>();
        double[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
        for (double x : xs) for (double y : ys) for (double z : zs) points.add(new double[]{x, y, z});
        for (int i = 1; i <= perEdge; i++) {
            double t = i / (double) (perEdge + 1);
            double x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t, z = z0 + (z1 - z0) * t;
            for (double a : ys) for (double b : zs) points.add(new double[]{x, a, b});
            for (double a : xs) for (double b : zs) points.add(new double[]{a, y, b});
            for (double a : xs) for (double b : ys) points.add(new double[]{a, b, z});
        }
        return points;
    }

    private static void box(ServerPlayer player, ServerLevel level, DustParticleOptions dust, BlockPos pos, int perEdge) {
        for (double[] p : outline(pos, perEdge)) {
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
