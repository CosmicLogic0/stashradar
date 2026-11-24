package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;
import com.cosmiclogic.stashradar.StashRadarRules;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.util.EnumFacing;
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
import java.util.UUID;

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
        List<BlockPos> placedNether = new ArrayList<>();
        WorldServer world = server.getWorld(0);
        int checks = 0;
        try {
            BlockPos spawn = world.getSpawnPoint();
            // Middle of the spawn chunk: the test blocks stay in loaded spawn chunks (nothing to generate).
            int x = (spawn.getX() >> 4) * 16 + 8;
            int z = (spawn.getZ() >> 4) * 16 + 8;
            int y = 200;
            List<String> defaults = new StashRadarConfig().containers;   // not the server's own list

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

            // 1.1.0: names, item frames, ender chest, other dimensions, glow, rules, help.
            ItemStack oreBox = shulkerItemWith(new ItemStack(Items.IRON_INGOT, 5));
            oreBox.setStackDisplayName("Ore Box");
            chest.setInventorySlotContents(2, oreBox);
            checks++;
            expect(problems, "name search finds the renamed \"Ore Box\"", 1,
                    total(ContainerSearch112.search(world, from, ContainerSearch112.named("ore box"), 8, defaults, true, true)));
            checks++;
            expect(problems, "name search \"iron\" finds the iron inside it", 5,
                    total(ContainerSearch112.search(world, from, ContainerSearch112.named("iron"), 8, defaults, true, true)));

            EntityItemFrame frame = new EntityItemFrame(world, new BlockPos(x - 2, y + 2, z), EnumFacing.NORTH);
            frame.setDisplayedItem(new ItemStack(Items.CLOCK));
            world.spawnEntity(frame);
            try {
                List<ContainerSearch112.Hit> clocks = ContainerSearch112.search(world, from, ContainerSearch112.of(Items.CLOCK), 8, defaults, true, true);
                checks++;
                expect(problems, "clock in an item frame", 1, clocks.size() == 1 && clocks.get(0).blockId.equals("item_frame") ? 1 : 0);
                checks++;
                expect(problems, "item frames off", 0, ContainerSearch112.search(world, from, ContainerSearch112.of(Items.CLOCK), 8, defaults, true, false).size());
            } finally {
                frame.setDead();
            }

            InventoryBasic enderLike = new InventoryBasic("ender", false, 27);
            enderLike.setInventorySlotContents(3, new ItemStack(Items.DIAMOND, 9));
            enderLike.setInventorySlotContents(4, shulkerItemWith(new ItemStack(Items.DIAMOND, 2)));
            checks++;
            expect(problems, "ender chest count (with a shulker box item)", 11, ContainerSearch112.count(enderLike, ContainerSearch112.of(Items.DIAMOND), true));

            WorldServer nether = server.getWorld(-1);
            if (nether != null) {
                nether.getChunkProvider().provideChunk(0, 0);
                place(nether, new BlockPos(8, 100, 8), Blocks.CHEST.getDefaultState(), placedNether).setInventorySlotContents(0, new ItemStack(Items.GOLD_INGOT, 9));
                checks++;
                expect(problems, "gold in a Nether chest, searched in the Nether", 9,
                        total(ContainerSearch112.search(nether, new Vec3d(8.5, 100.5, 5.5), ContainerSearch112.of(Items.GOLD_INGOT), 8, defaults, true, true)));
                checks++;
                expect(problems, "the Nether chest is not found from the overworld", 0,
                        ContainerSearch112.search(world, new Vec3d(8.5, 100.5, 5.5), ContainerSearch112.of(Items.GOLD_INGOT), 8, defaults, true, true).size());
            }

            Glow112.Ghost ghost = Glow112.build(world, new BlockPos(x, y, z));
            checks++;
            expect(problems, "glow packets", 1, ghost.spawn.size() == 2 && ghost.id != 0 ? 1 : 0);
            checks++;
            expect(problems, "outline points (1 result / 16 results)", 1,
                    Highlighter112.outline(BlockPos.ORIGIN, 6).size() == 80 && Highlighter112.outline(BlockPos.ORIGIN, 1).size() == 20 ? 1 : 0);

            StashRadarConfig c = new StashRadarConfig();
            checks++;
            expect(problems, "range rules", 1, StashRadarRules.range(-1, c) == 16 && StashRadarRules.range(64, c) == 32
                    && StashRadarRules.rangeWasLimited(64, c) && StashRadarRules.range(8, c) == 8 ? 1 : 0);
            StashRadarRules.Cooldowns cd = new StashRadarRules.Cooldowns();
            UUID a = new UUID(1, 1), b = new UUID(2, 2), d = new UUID(3, 3);
            checks++;
            expect(problems, "cooldowns are per player", 1, cd.tryUse(a, 1000, 5000) == 0 && cd.tryUse(a, 2000, 5000) == 4000
                    && cd.tryUse(b, 2000, 5000) == 0 && cd.tryUse(a, 6000, 5000) == 0 ? 1 : 0);
            cd.tryUse(d, 60000, 5000);
            checks++;
            expect(problems, "old cooldowns are dropped (no leak)", 1, cd.size());
            checks++;
            expect(problems, "the loaded config is upgraded", 1, StashRadar.config().configVersion == StashRadarRules.CONFIG_VERSION ? 1 : 0);
            checks++;
            expect(problems, "/find help runs", 1, server.getCommandManager().executeCommand(server, "find help") > 0 ? 1 : 0);
            checks++;
            expect(problems, "/stashradar help runs", 1, server.getCommandManager().executeCommand(server, "stashradar help") > 0 ? 1 : 0);
        } catch (Throwable t) {
            problems.add("exception " + t);
        } finally {
            if (server.getWorld(-1) != null) {
                clear(server.getWorld(-1), placedNether);
            }
            clear(world, placed);
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

    private static void clear(WorldServer world, List<BlockPos> placed) {
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
