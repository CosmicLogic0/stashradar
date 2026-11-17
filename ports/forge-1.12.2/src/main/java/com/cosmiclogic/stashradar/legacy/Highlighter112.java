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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outlines the found containers with colored redstone dust that only the searching player receives,
 * so it works for players without the mod too. Redrawn every 10 ticks until the time runs out.
 */
public final class Highlighter112 {

    private static final class Highlight {
        final int dimension;
        final List<BlockPos> positions;
        final float r, g, b;
        final long endMillis;

        Highlight(int dimension, List<BlockPos> positions, int rgb, long endMillis) {
            this.dimension = dimension;
            this.positions = positions;
            // Redstone dust uses red 0 as "default red", so never send exactly 0.
            this.r = Math.max(0.001f, ((rgb >> 16) & 0xFF) / 255f);
            this.g = ((rgb >> 8) & 0xFF) / 255f;
            this.b = (rgb & 0xFF) / 255f;
            this.endMillis = endMillis;
        }
    }

    private static final Map<UUID, Highlight> ACTIVE = new ConcurrentHashMap<>();
    private int ticks;

    public static void show(EntityPlayerMP player, List<BlockPos> positions, int seconds, int rgb) {
        if (positions.isEmpty()) {
            ACTIVE.remove(player.getUniqueID());
            return;
        }
        ACTIVE.put(player.getUniqueID(), new Highlight(player.dimension, new ArrayList<>(positions), rgb,
                System.currentTimeMillis() + seconds * 1000L));
        draw(player, ACTIVE.get(player.getUniqueID()));
    }

    public static void stop(UUID player) {
        ACTIVE.remove(player);
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
                continue;
            }
            draw(player, h);
        }
    }

    private static void draw(EntityPlayerMP player, Highlight h) {
        WorldServer world = player.getServerWorld();
        for (BlockPos pos : h.positions) {
            box(player, world, h, pos);
        }
    }

    /** 8 corners + the middle of the 12 edges: a clear outline without flooding the connection. */
    private static void box(EntityPlayerMP player, WorldServer world, Highlight h, BlockPos pos) {
        double x0 = pos.getX() - 0.02, y0 = pos.getY() - 0.02, z0 = pos.getZ() - 0.02;
        double x1 = pos.getX() + 1.02, y1 = pos.getY() + 1.02, z1 = pos.getZ() + 1.02;
        double xm = (x0 + x1) / 2, ym = (y0 + y1) / 2, zm = (z0 + z1) / 2;
        double[][] points = {
                {x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1},
                {xm, y0, z0}, {xm, y1, z0}, {xm, y0, z1}, {xm, y1, z1},
                {x0, ym, z0}, {x1, ym, z0}, {x0, ym, z1}, {x1, ym, z1},
                {x0, y0, zm}, {x1, y0, zm}, {x0, y1, zm}, {x1, y1, zm}};
        for (double[] p : points) {
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
