package com.cosmiclogic.stashradar.search;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//#if MC >= 1.20.5
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
//#else
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
//#endif

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The search itself. Runs on the server thread and reads the real container contents in chunks
 * that are already loaded (it never loads chunks). Nothing comes from the player's client.
 */
public final class ContainerSearch {

    /** One container that holds the item. */
    public static final class Hit {
        private final BlockPos pos;
        private final String blockId;
        private final int count;
        private final double distance;

        public Hit(BlockPos pos, String blockId, int count, double distance) {
            this.pos = pos;
            this.blockId = blockId;
            this.count = count;
            this.distance = distance;
        }

        public BlockPos pos() { return pos; }
        public String blockId() { return blockId; }
        public int count() { return count; }
        public double distance() { return distance; }
    }

    /** Which containers to search, whether to look inside shulker box / bundle items, and item frames. */
    public static final class Options {
        private final List<String> containers;
        private final boolean insideItems;
        private final boolean itemFrames;

        public Options(List<String> containers, boolean insideItems, boolean itemFrames) {
            this.containers = containers;
            this.insideItems = insideItems;
            this.itemFrames = itemFrames;
        }

        public List<String> containers() { return containers; }
        public boolean insideItems() { return insideItems; }
        public boolean itemFrames() { return itemFrames; }
    }

    /** What we look for: one item, a tag ("#logs") or a name ("sword"). */
    public static Predicate<ItemStack> of(Item item) {
        return stack -> stack.getItem() == item;
    }

    /** Items whose name (custom name included) or id contains the text, ignoring case. */
    public static Predicate<ItemStack> named(String text) {
        String q = text.trim().toLowerCase(java.util.Locale.ROOT);
        return stack -> !q.isEmpty() && (stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(q)
                || Ids.item(stack.getItem()).contains(q.replace(' ', '_')));
    }

    private ContainerSearch() {
    }

    /** Containers (and item frames) within {@code range} blocks of {@code center} that hold matching items, nearest first. */
    public static List<Hit> search(ServerLevel level, Vec3 center, Predicate<ItemStack> match, int range, Options options) {
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
                    if (distSq > rangeSq || !(be instanceof Container) || be.isRemoved()) {
                        continue;
                    }
                    String blockId = Ids.block(be.getBlockState().getBlock());
                    if (!isSearched(blockId, be.getBlockState().getBlock() instanceof ShulkerBoxBlock, options.containers())) {
                        continue;
                    }
                    int count = count((Container) be, match, options.insideItems());
                    if (count > 0) {
                        hits.add(new Hit(pos.immutable(), blockId, count, Math.sqrt(distSq)));
                    }
                }
            }
        }
        if (options.itemFrames()) {
            AABB box = new AABB(center.x - range, center.y - range, center.z - range, center.x + range, center.y + range, center.z + range);
            for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
                double distSq = center.distanceToSqr(frame.position());
                int count = count(frame.getItem(), match, options.insideItems(), 0);
                if (count > 0 && distSq <= rangeSq && frame.isAlive()) {
                    hits.add(new Hit(frame.blockPosition().immutable(), "item_frame", count, Math.sqrt(distSq)));
                }
            }
        }
        Collections.sort(hits, Comparator.comparingDouble(Hit::distance));
        return hits;
    }

    /** How many matching items are in a container that isn't a block (the player's ender chest). */
    public static int count(Container container, Predicate<ItemStack> match, boolean insideItems) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += count(container.getItem(slot), match, insideItems, 0);
        }
        return total;
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
            if (entry.equals("copper_chests") && blockId.startsWith("minecraft:") && blockId.endsWith("copper_chest")) {
                return true;   // copper_chest, exposed_copper_chest, waxed_oxidized_copper_chest, ...
            }
        }
        return false;
    }

    /** This stack, plus (one level deep) what's inside it if it's a shulker box or bundle item. */
    static int count(ItemStack stack, Predicate<ItemStack> match, boolean insideItems, int depth) {
        if (stack.isEmpty()) {
            return 0;
        }
        int total = match.test(stack) ? stack.getCount() : 0;
        if (insideItems && depth == 0) {
            int inside = 0;
            for (ItemStack inner : contentsOf(stack)) {
                inside += count(inner, match, true, depth + 1);
            }
            total += inside * stack.getCount();
        }
        return total;
    }

    /** Items stored inside a shulker box item (or bundle). Empty for other items. */
    static List<ItemStack> contentsOf(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        //#if MC >= 26.1
        // 26.x stores ItemStackTemplates; ask for ready-made ItemStack copies instead.
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.nonEmptyItemCopyStream().forEach(out::add);
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            //#if MC >= 26.3
            bundle.itemCopies().forEach(out::add);
            //#else
            bundle.itemCopyStream().forEach(out::add);
            //#endif
        }
        //#elif MC >= 1.20.5
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack inner : contents.nonEmptyItems()) {
                out.add(inner);
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.items()) {
                out.add(inner);
            }
        }
        //#else
        CompoundTag blockEntity = stack.getTagElement("BlockEntityTag");   // shulker box items
        addItems(blockEntity, out);
        addItems(stack.getTag(), out);                                     // bundles keep "Items" on the item itself
        //#endif
        return out;
    }

    //#if MC < 1.20.5
    private static void addItems(CompoundTag tag, List<ItemStack> out) {
        if (tag == null || !tag.contains("Items", 9)) {
            return;
        }
        ListTag list = tag.getList("Items", 10);
        for (int i = 0; i < list.size(); i++) {
            ItemStack inner = ItemStack.of(list.getCompound(i));
            if (!inner.isEmpty()) {
                out.add(inner);
            }
        }
    }
    //#endif

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
