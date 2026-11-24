package com.cosmiclogic.stashradar.legacy;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outlines the found containers with colored redstone dust that only the searching player receives,
 * so it works for players without the mod too. Redrawn every 10 ticks until the time runs out.
 */
public final class Highlighter112 {

    static final double GLOW_MIN_DISTANCE = 5.5;
    static final int POINT_BUDGET = 480;

    private static final class Highlight {
        final int dimension;
        final List<BlockPos> positions;
        final float r, g, b;
        final long endMillis;
        final boolean glow;
        final Map<BlockPos, Glow112.Ghost> ghosts = new HashMap<>();
        final Set<BlockPos> shown = new HashSet<>();

        Highlight(int dimension, List<BlockPos> positions, int rgb, long endMillis, boolean glow) {
            this.dimension = dimension;
            this.positions = positions;
            this.glow = glow;
            // Redstone dust uses red 0 as "default red", so never send exactly 0.
            this.r = Math.max(0.001f, ((rgb >> 16) & 0xFF) / 255f);
            this.g = ((rgb >> 8) & 0xFF) / 255f;
            this.b = (rgb & 0xFF) / 255f;
            this.endMillis = endMillis;
        }
    }

    private static final Map<UUID, Highlight> ACTIVE = new ConcurrentHashMap<>();
    private int ticks;

    public static void show(EntityPlayerMP player, List<BlockPos> positions, int seconds, int rgb, boolean glow) {
        stop(player);
        if (positions.isEmpty()) {
            return;
        }
        ACTIVE.put(player.getUniqueID(), new Highlight(player.dimension, new ArrayList<>(positions), rgb,
                System.currentTimeMillis() + seconds * 1000L, glow));
        draw(player, ACTIVE.get(player.getUniqueID()));
    }

    /** Ends the player's highlight and takes the glow away. */
    public static void stop(EntityPlayerMP player) {
        Highlight h = ACTIVE.remove(player.getUniqueID());
        if (h != null) {
            hideAll(player, h);
        }
    }

    private static void hideAll(EntityPlayerMP player, Highlight h) {
        int[] ids = new int[h.shown.size()];
        int i = 0;
        for (BlockPos pos : h.shown) {
            ids[i++] = h.ghosts.get(pos).id;
        }
        h.shown.clear();
        try {
            Glow112.hide(player, ids);
        } catch (Throwable ignored) {
            // the player just left
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty() || ++ticks % 10 != 0) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Highlight>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Highlight> e = it.next();
            Highlight h = e.getValue();
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(e.getKey());
            if (now > h.endMillis || player == null || player.dimension != h.dimension) {
                it.remove();
                if (player != null && player.dimension == h.dimension) {
                    hideAll(player, h);
                }
                continue;
            }
            draw(player, h);
        }
    }

    private static void draw(EntityPlayerMP player, Highlight h) {
        WorldServer world = player.getServerWorld();
        int perEdge = Math.max(1, Math.min(6, (POINT_BUDGET / Math.max(1, h.positions.size()) - 8) / 12));
        for (BlockPos pos : h.positions) {
            box(player, world, h, pos, perEdge);
            if (h.glow) {
                glow(player, world, h, pos);
            }
        }
    }

    private static void glow(EntityPlayerMP player, WorldServer world, Highlight h, BlockPos pos) {
        boolean wanted = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > GLOW_MIN_DISTANCE * GLOW_MIN_DISTANCE;
        try {
            if (wanted && !h.shown.contains(pos)) {
                Glow112.Ghost ghost = h.ghosts.get(pos);
                if (ghost == null) {
                    ghost = Glow112.build(world, pos);
                    h.ghosts.put(pos, ghost);
                }
                Glow112.show(player, ghost);
                h.shown.add(pos);
            } else if (!wanted && h.shown.remove(pos)) {
                Glow112.hide(player, h.ghosts.get(pos).id);
            }
        } catch (Throwable ignored) {
            // the particle outline still works
        }
    }

    /** The 8 corners plus {@code perEdge} points on each of the 12 edges (same as the other versions). */
    static List<double[]> outline(BlockPos pos, int perEdge) {
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

    private static void box(EntityPlayerMP player, WorldServer world, Highlight h, BlockPos pos, int perEdge) {
        for (double[] p : outline(pos, perEdge)) {
            // count 0 + speed 1: the offsets are the dust color (r, g, b).
            world.spawnParticle(player, EnumParticleTypes.REDSTONE, true, p[0], p[1], p[2], 0, h.r, h.g, h.b, 1.0);
        }
    }

    public static int parseColor(String hex, int fallback) {
        try {
            String s = hex.trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }
}
