package com.cosmiclogic.stashradar.legacy;

import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The search for 1.12.2 (same rules as every other version). Runs on the server thread and reads the
 * real container contents in chunks that are already loaded; it never loads chunks.
 */
public final class ContainerSearch112 {

    /** One container that holds the item. */
    public static final class Hit {
        public final BlockPos pos;
        public final String blockId;
        public final int count;
        public final double distance;

        Hit(BlockPos pos, String blockId, int count, double distance) {
            this.pos = pos;
            this.blockId = blockId;
            this.count = count;
            this.distance = distance;
        }
    }

    private ContainerSearch112() {
    }

    public static Predicate<ItemStack> of(final Item item) {
        return stack -> stack.getItem() == item;
    }

    /** Items whose shown name (a custom name too) or id contains the text, ignoring case. */
    public static Predicate<ItemStack> named(String text) {
        final String q = text.trim().toLowerCase(Locale.ROOT);
        return stack -> !q.isEmpty() && (stack.getDisplayName().toLowerCase(Locale.ROOT).contains(q)
                || itemId(stack.getItem()).contains(q.replace(' ', '_')));
    }

    /** Old call, used by the first self-test checks: one item, no item frames. */
    public static List<Hit> search(WorldServer world, Vec3d center, Item item, int range, List<String> containers, boolean insideItems) {
        return search(world, center, of(item), range, containers, insideItems, false);
    }

    /** Containers (and item frames) within {@code range} blocks of {@code center} with matching items, nearest first. */
    public static List<Hit> search(WorldServer world, Vec3d center, Predicate<ItemStack> item, int range, List<String> containers,
                                   boolean insideItems, boolean itemFrames) {
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
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                if (chunk == null) {
                    continue;   // not loaded: never load chunks just to search them
                }
                for (Map.Entry<BlockPos, TileEntity> entry : new ArrayList<>(chunk.getTileEntityMap().entrySet())) {
                    BlockPos pos = entry.getKey();
                    TileEntity te = entry.getValue();
                    double distSq = center.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSq > rangeSq || !(te instanceof IInventory) || te.isInvalid()) {
                        continue;
                    }
                    Block block = world.getBlockState(pos).getBlock();
                    String blockId = blockId(block);
                    if (!isSearched(blockId, block instanceof BlockShulkerBox, containers)) {
                        continue;
                    }
                    int count = count((IInventory) te, item, insideItems);
                    if (count > 0) {
                        hits.add(new Hit(pos.toImmutable(), blockId, count, Math.sqrt(distSq)));
                    }
                }
            }
        }
        if (itemFrames) {
            AxisAlignedBB box = new AxisAlignedBB(center.x - range, center.y - range, center.z - range,
                    center.x + range, center.y + range, center.z + range);
            for (EntityItemFrame frame : world.getEntitiesWithinAABB(EntityItemFrame.class, box)) {
                double distSq = center.squareDistanceTo(frame.posX, frame.posY, frame.posZ);
                int count = count(frame.getDisplayedItem(), item, insideItems, 0);
                if (count > 0 && distSq <= rangeSq && !frame.isDead) {
                    hits.add(new Hit(new BlockPos(frame.posX, frame.posY, frame.posZ), "item_frame", count, Math.sqrt(distSq)));
                }
            }
        }
        Collections.sort(hits, new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                return Double.compare(a.distance, b.distance);
            }
        });
        return hits;
    }

    static String blockId(Block block) {
        return block.getRegistryName() == null ? "" : block.getRegistryName().toString();
    }

    static String itemId(Item item) {
        return item.getRegistryName() == null ? "" : item.getRegistryName().toString();
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

    public static int count(IInventory inv, Predicate<ItemStack> item, boolean insideItems) {
        int total = 0;
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            total += count(inv.getStackInSlot(slot), item, insideItems, 0);
        }
        return total;
    }

    /** This stack, plus (one level deep) what's inside it if it's a shulker box item. */
    static int count(ItemStack stack, Predicate<ItemStack> item, boolean insideItems, int depth) {
        if (stack.isEmpty()) {
            return 0;
        }
        int total = item.test(stack) ? stack.getCount() : 0;
        if (insideItems && depth == 0) {
            int inside = 0;
            for (ItemStack inner : contentsOf(stack)) {
                inside += count(inner, item, true, depth + 1);
            }
            total += inside * stack.getCount();
        }
        return total;
    }

    /** Items stored inside a shulker box item ("BlockEntityTag" → "Items"). Empty for other items. */
    static List<ItemStack> contentsOf(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
        if (tag == null || !tag.hasKey("Items", 9)) {
            return out;
        }
        NBTTagList list = tag.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack inner = new ItemStack(list.getCompoundTagAt(i));
            if (!inner.isEmpty()) {
                out.add(inner);
            }
        }
        return out;
    }

    /** "north-east", "below you", ... from a point to a container. */
    public static String direction(Vec3d from, BlockPos to) {
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
