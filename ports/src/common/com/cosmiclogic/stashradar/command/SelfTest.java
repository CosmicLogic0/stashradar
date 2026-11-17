package com.cosmiclogic.stashradar.command;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.search.ContainerSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
//#if MC >= 1.20.5
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
//#else
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
//#endif

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Console-only "/stashradar selftest", run by CI on a real dedicated server: places real containers
 * in the middle of the spawn chunk, fills them, runs the real search, checks the answers and removes
 * everything again.
 */
final class SelfTest {

    private SelfTest() {
    }

    static int run(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        List<BlockPos> placed = new ArrayList<>();
        ServerLevel level = server.overworld();
        int checks = 0;
        try {
            //#if MC >= 1.21.9
            BlockPos spawn = BlockPos.ZERO;   // the spawn API changed in 1.21.9; any spot works, it's only a test area
            //#else
            BlockPos spawn = level.getSharedSpawnPos();
            //#endif
            // Middle of the spawn chunk: every test block stays inside this one chunk (nothing to load or generate).
            int x = (spawn.getX() >> 4) * 16 + 8;
            int z = (spawn.getZ() >> 4) * 16 + 8;
            int y = Math.min(maxY(level) - 10, 200);
            level.getChunk(x >> 4, z >> 4);
            level.getChunk((x + 20) >> 4, z >> 4);

            // Chest: 7 diamonds + a shulker box ITEM holding 3 emeralds.
            Container chest = place(level, new BlockPos(x, y, z), Blocks.CHEST.defaultBlockState(), placed);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            chest.setItem(1, shulkerItemWith(new ItemStack(Items.EMERALD, 3)));
            // Shulker box block: 4 diamonds.
            place(level, new BlockPos(x - 2, y, z), Blocks.SHULKER_BOX.defaultBlockState(), placed).setItem(0, new ItemStack(Items.DIAMOND, 4));
            // Barrel: 2 diamonds.
            place(level, new BlockPos(x + 2, y, z), Blocks.BARREL.defaultBlockState(), placed).setItem(0, new ItemStack(Items.DIAMOND, 2));
            // A dropper with diamonds: not searched by default.
            place(level, new BlockPos(x, y, z + 2), Blocks.DROPPER.defaultBlockState(), placed).setItem(0, new ItemStack(Items.DIAMOND, 5));
            // A chest 20 blocks away: outside the normal range.
            place(level, new BlockPos(x + 20, y, z), Blocks.CHEST.defaultBlockState(), placed).setItem(0, new ItemStack(Items.DIAMOND, 64));

            Vec3 from = new Vec3(x + 0.5, y + 0.5, z - 3.5);
            ContainerSearch.Options options = new ContainerSearch.Options(StashRadar.config().containers, true);

            List<ContainerSearch.Hit> diamonds = ContainerSearch.search(level, from, Items.DIAMOND, 8, options);
            checks++;
            expect(problems, "diamond containers (chest, barrel, shulker box; not the dropper)", 3, diamonds.size());
            checks++;
            expect(problems, "diamond count", 13, total(diamonds));
            checks++;
            expect(problems, "nearest container is the chest", 1, diamonds.isEmpty() || !diamonds.get(0).blockId().equals("minecraft:chest") ? 0 : 1);
            checks++;
            expect(problems, "emeralds inside a shulker box item", 3, total(ContainerSearch.search(level, from, Items.EMERALD, 8, options)));
            checks++;
            expect(problems, "emeralds with item search off", 0,
                    ContainerSearch.search(level, from, Items.EMERALD, 8, new ContainerSearch.Options(options.containers(), false)).size());
            checks++;
            expect(problems, "gold (not stored)", 0, ContainerSearch.search(level, from, Items.GOLD_INGOT, 8, options).size());
            checks++;
            expect(problems, "only chests searched", 1,
                    ContainerSearch.search(level, from, Items.DIAMOND, 8, new ContainerSearch.Options(Collections.singletonList("minecraft:chest"), true)).size());
            checks++;
            expect(problems, "dropper added to the list", 4,
                    ContainerSearch.search(level, from, Items.DIAMOND, 8,
                            new ContainerSearch.Options(Arrays.asList("minecraft:chest", "minecraft:barrel", "shulker_boxes", "minecraft:dropper"), true)).size());
            checks++;
            expect(problems, "far chest with a 24 block range", 4, ContainerSearch.search(level, from, Items.DIAMOND, 24, options).size());
            checks++;
            expect(problems, "direction to the chest", 1, ContainerSearch.direction(from, new BlockPos(x, y, z)).equals("south") ? 1 : 0);
        } catch (Throwable t) {
            problems.add("exception " + t);
        } finally {
            for (BlockPos pos : placed) {
                try {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof Container) {
                        ((Container) be).clearContent();
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                } catch (Throwable ignored) {
                    // best effort cleanup
                }
            }
        }
        if (problems.isEmpty()) {
            StashRadar.LOG.info("Stash Radar self-test PASSED ({} checks)", checks);
            return 1;
        }
        for (String p : problems) {
            StashRadar.LOG.warn("Stash Radar self-test check FAILED: {}", p);
        }
        StashRadar.LOG.warn("Stash Radar self-test FAILED ({} of {})", problems.size(), checks);
        return 0;
    }

    private static int maxY(ServerLevel level) {
        //#if MC >= 1.21.2
        return level.getMaxY();
        //#else
        return level.getMaxBuildHeight();
        //#endif
    }

    private static ItemStack shulkerItemWith(ItemStack content) {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        //#if MC >= 1.20.5
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(Collections.singletonList(content)));
        //#else
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        items.set(0, content);
        CompoundTag blockEntity = new CompoundTag();
        ContainerHelper.saveAllItems(blockEntity, items);
        shulker.getOrCreateTag().put("BlockEntityTag", blockEntity);
        //#endif
        return shulker;
    }

    private static Container place(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> placed) {
        level.setBlock(pos, state, 3);
        placed.add(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container) {
            return (Container) be;
        }
        throw new IllegalStateException("no container at " + pos);
    }

    private static int total(List<ContainerSearch.Hit> hits) {
        int t = 0;
        for (ContainerSearch.Hit h : hits) {
            t += h.count();
        }
        return t;
    }

    private static void expect(List<String> problems, String what, int expected, int actual) {
        if (expected != actual) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }
}
