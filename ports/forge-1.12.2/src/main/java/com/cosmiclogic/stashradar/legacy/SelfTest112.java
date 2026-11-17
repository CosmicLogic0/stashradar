package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.stashradar.StashRadar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Console-only "/stashradar selftest" for 1.12.2, run by CI on a real dedicated server. Same 10 checks
 * as the other versions; 1.12.2 has no barrels, so a trapped chest takes the barrel's place.
 */
final class SelfTest112 {

    private SelfTest112() {
    }

    static int run(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        List<BlockPos> placed = new ArrayList<>();
        WorldServer world = server.getWorld(0);
        int checks = 0;
        try {
            BlockPos spawn = world.getSpawnPoint();
            // Middle of the spawn chunk: the test blocks stay in loaded spawn chunks (nothing to generate).
            int x = (spawn.getX() >> 4) * 16 + 8;
            int z = (spawn.getZ() >> 4) * 16 + 8;
            int y = 200;
            List<String> defaults = StashRadar.config().containers;

            // Chest: 7 diamonds + a shulker box ITEM holding 3 emeralds.
            IInventory chest = place(world, new BlockPos(x, y, z), Blocks.CHEST.getDefaultState(), placed);
            chest.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
            chest.setInventorySlotContents(1, shulkerItemWith(new ItemStack(Items.EMERALD, 3)));
            // Shulker box block: 4 diamonds.
            place(world, new BlockPos(x - 2, y, z), Blocks.PURPLE_SHULKER_BOX.getDefaultState(), placed)
                    .setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 4));
            // Trapped chest (the barrel's place; 1.12.2 has no barrels): 2 diamonds.
            place(world, new BlockPos(x + 2, y, z), Blocks.TRAPPED_CHEST.getDefaultState(), placed)
                    .setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 2));
            // A dropper with diamonds: not searched by default.
            place(world, new BlockPos(x, y, z + 2), Blocks.DROPPER.getDefaultState(), placed)
                    .setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 5));
            // A chest 20 blocks away: outside the normal range.
            place(world, new BlockPos(x + 20, y, z), Blocks.CHEST.getDefaultState(), placed)
                    .setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 64));

            Vec3d from = new Vec3d(x + 0.5, y + 0.5, z - 3.5);
            List<ContainerSearch112.Hit> diamonds = ContainerSearch112.search(world, from, Items.DIAMOND, 8, defaults, true);
            checks++;
            expect(problems, "diamond containers (chest, trapped chest, shulker box; not the dropper)", 3, diamonds.size());
            checks++;
            expect(problems, "diamond count", 13, total(diamonds));
            checks++;
            expect(problems, "nearest container is the chest", 1, diamonds.isEmpty() || !diamonds.get(0).blockId.equals("minecraft:chest") ? 0 : 1);
            checks++;
            expect(problems, "emeralds inside a shulker box item", 3, total(ContainerSearch112.search(world, from, Items.EMERALD, 8, defaults, true)));
            checks++;
            expect(problems, "emeralds with item search off", 0, ContainerSearch112.search(world, from, Items.EMERALD, 8, defaults, false).size());
            checks++;
            expect(problems, "gold (not stored)", 0, ContainerSearch112.search(world, from, Items.GOLD_INGOT, 8, defaults, true).size());
            checks++;
            expect(problems, "only chests searched", 1,
                    ContainerSearch112.search(world, from, Items.DIAMOND, 8, Collections.singletonList("minecraft:chest"), true).size());
            checks++;
            expect(problems, "dropper added to the list", 4, ContainerSearch112.search(world, from, Items.DIAMOND, 8,
                    Arrays.asList("minecraft:chest", "minecraft:trapped_chest", "shulker_boxes", "minecraft:dropper"), true).size());
            checks++;
            expect(problems, "far chest with a 24 block range", 4, ContainerSearch112.search(world, from, Items.DIAMOND, 24, defaults, true).size());
            checks++;
            expect(problems, "direction to the chest", 1, ContainerSearch112.direction(from, new BlockPos(x, y, z)).equals("south") ? 1 : 0);
        } catch (Throwable t) {
            problems.add("exception " + t);
        } finally {
            for (BlockPos pos : placed) {
                try {
                    TileEntity te = world.getTileEntity(pos);
                    if (te instanceof IInventory) {
                        ((IInventory) te).clear();
                    }
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
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

    private static ItemStack shulkerItemWith(ItemStack content) {
        ItemStack shulker = new ItemStack(Item.getItemFromBlock(Blocks.PURPLE_SHULKER_BOX));
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        items.set(0, content);
        NBTTagCompound blockEntity = new NBTTagCompound();
        ItemStackHelper.saveAllItems(blockEntity, items);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("BlockEntityTag", blockEntity);
        shulker.setTagCompound(tag);
        return shulker;
    }

    private static IInventory place(WorldServer world, BlockPos pos, IBlockState state, List<BlockPos> placed) {
        world.setBlockState(pos, state, 3);
        placed.add(pos);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IInventory) {
            return (IInventory) te;
        }
        throw new IllegalStateException("no container at " + pos);
    }

    private static int total(List<ContainerSearch112.Hit> hits) {
        int t = 0;
        for (ContainerSearch112.Hit h : hits) {
            t += h.count;
        }
        return t;
    }

    private static void expect(List<String> problems, String what, int expected, int actual) {
        if (expected != actual) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }
}
