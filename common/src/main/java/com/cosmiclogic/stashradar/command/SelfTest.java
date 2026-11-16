package com.cosmiclogic.stashradar.command;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.search.ContainerSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Console-only "/stashradar selftest", run by CI on a real dedicated server: places real containers
 * next to spawn, fills them, runs the real search, checks the answers and removes everything again.
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
            BlockPos spawn = level.getSharedSpawnPos();
            int x = spawn.getX() + 3;
            int z = spawn.getZ() + 3;
            int y = Math.min(level.getMaxBuildHeight() - 10, 200);
            // Only chunks right next to spawn: they already exist, so nothing has to be generated.
            level.getChunk(x >> 4, z >> 4);
            level.getChunk((x + 20) >> 4, z >> 4);

            // Chest: 7 diamonds + a purple shulker box ITEM holding 3 emeralds.
            Container chest = place(level, new BlockPos(x, y, z), Blocks.CHEST.defaultBlockState(), placed);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            ItemStack shulkerItem = new ItemStack(Items.PURPLE_SHULKER_BOX);
            shulkerItem.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.EMERALD, 3))));
            chest.setItem(1, shulkerItem);
            // Barrel: 2 diamonds. Placed purple shulker box: 4 diamonds.
            place(level, new BlockPos(x + 2, y, z), Blocks.BARREL.defaultBlockState(), placed).setItem(0, new ItemStack(Items.DIAMOND, 2));
            place(level, new BlockPos(x - 2, y, z), Blocks.PURPLE_SHULKER_BOX.defaultBlockState(), placed).setItem(0, new ItemStack(Items.DIAMOND, 4));
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
                    ContainerSearch.search(level, from, Items.DIAMOND, 8, new ContainerSearch.Options(List.of("minecraft:chest"), true)).size());
            checks++;
            expect(problems, "dropper added to the list", 4,
                    ContainerSearch.search(level, from, Items.DIAMOND, 8,
                            new ContainerSearch.Options(List.of("minecraft:chest", "minecraft:barrel", "shulker_boxes", "minecraft:dropper"), true)).size());
            checks++;
            expect(problems, "far chest with a 24 block range", 4, ContainerSearch.search(level, from, Items.DIAMOND, 24, options).size());
            checks++;
            expect(problems, "direction to the chest", 1, ContainerSearch.direction(from, new BlockPos(x, y, z)).equals("south") ? 1 : 0);
        } catch (Throwable t) {
            problems.add("exception " + t);
        } finally {
            for (BlockPos pos : placed) {
                try {
                    if (level.getBlockEntity(pos) instanceof Container c) {
                        c.clearContent();
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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

    private static Container place(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> placed) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        placed.add(pos);
        if (level.getBlockEntity(pos) instanceof Container c) {
            return c;
        }
        throw new IllegalStateException("no container at " + pos);
    }

    private static int total(List<ContainerSearch.Hit> hits) {
        return hits.stream().mapToInt(ContainerSearch.Hit::count).sum();
    }

    private static void expect(List<String> problems, String what, int expected, int actual) {
        if (expected != actual) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }
}
