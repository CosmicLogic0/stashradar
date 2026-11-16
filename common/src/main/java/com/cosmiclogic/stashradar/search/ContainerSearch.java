package com.cosmiclogic.stashradar.search;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The search itself. Runs on the server thread and reads the real container contents in chunks
 * that are already loaded (it never loads chunks). Nothing comes from the player's client.
 */
public final class ContainerSearch {

    /** One container that holds the item. */
    public record Hit(BlockPos pos, String blockId, int count, double distance) {
    }

    /** Which containers to search and whether to look inside shulker box / bundle items. */
    public record Options(List<String> containers, boolean insideItems) {
    }

    private ContainerSearch() {
    }

    /** Containers within {@code range} blocks of {@code center} that hold {@code item}, nearest first. */
    public static List<Hit> search(ServerLevel level, Vec3 center, Item item, int range, Options options) {
        List<Hit> hits = new ArrayList<>();
        if (range < 1) {
            return hits;
        }
        double rangeSq = (double) range * range;
        int minX = ((int) Math.floor(center.x - range)) >> 4;
        int maxX = ((int) Math.floor(center.x + range)) >> 4;
        int minZ = ((int) Math.floor(center.z - range)) >> 4;
        int maxZ = ((int) Math.floor(center.z + range)) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;   // not loaded: never load chunks just to search them
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockEntity be = entry.getValue();
                    double distSq = center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSq > rangeSq || !(be instanceof Container container) || be.isRemoved()) {
                        continue;
                    }
                    String blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                    if (!isSearched(blockId, be.getBlockState().getBlock() instanceof ShulkerBoxBlock, options.containers())) {
                        continue;
                    }
                    int count = count(container, item, options.insideItems());
                    if (count > 0) {
                        hits.add(new Hit(pos.immutable(), blockId, count, Math.sqrt(distSq)));
                    }
                }
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::distance));
        return hits;
    }

    static boolean isSearched(String blockId, boolean isShulkerBox, List<String> containers) {
        for (String c : containers) {
            String entry = c.trim();
            if (entry.equals(blockId)) {
                return true;
            }
            if (isShulkerBox && (entry.equals("shulker_boxes") || entry.equals("#shulker_boxes"))) {
                return true;
            }
        }
        return false;
    }

    static int count(Container container, Item item, boolean insideItems) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += count(container.getItem(slot), item, insideItems, 0);
        }
        return total;
    }

    /** This stack, plus (one level deep) what's inside it if it's a shulker box or bundle item. */
    static int count(ItemStack stack, Item item, boolean insideItems, int depth) {
        if (stack.isEmpty()) {
            return 0;
        }
        int total = stack.is(item) ? stack.getCount() : 0;
        if (insideItems && depth == 0) {
            int inside = 0;
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                for (ItemStack inner : contents.nonEmptyItems()) {
                    inside += count(inner, item, true, depth + 1);
                }
            }
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) {
                for (ItemStack inner : bundle.items()) {
                    inside += count(inner, item, true, depth + 1);
                }
            }
            total += inside * stack.getCount();
        }
        return total;
    }

    /** "north-east", "below you", ... from a point to a container. */
    public static String direction(Vec3 from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.x;
        double dy = to.getY() + 0.5 - from.y;
        double dz = to.getZ() + 0.5 - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.5) {
            return dy > 0 ? "above you" : "below you";
        }
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));   // +z is south, +x is east
        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        String dir = names[index];
        if (dy > 3) {
            dir += ", up";
        } else if (dy < -3) {
            dir += ", down";
        }
        return dir;
    }
}
