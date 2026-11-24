package com.cosmiclogic.stashradar.command;

import com.cosmiclogic.stashradar.StashRadar;
import com.cosmiclogic.stashradar.StashRadarConfig;
import com.cosmiclogic.stashradar.StashRadarRules;
import com.cosmiclogic.stashradar.search.ContainerSearch;
import com.cosmiclogic.stashradar.search.Glow;
import com.cosmiclogic.stashradar.search.Highlighter;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
//#if MC < 1.19
import net.minecraft.network.chat.TextComponent;
//#else
import net.minecraft.network.chat.Component;
//#endif
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
import java.util.UUID;
import java.util.function.Predicate;

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
        List<BlockPos> placedNether = new ArrayList<>();
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

            // Barrel slot 1: 6 oak logs (for the tag search). Chest slot 2: a shulker box item renamed "Ore Box" holding 5 iron.
            ((Container) level.getBlockEntity(new BlockPos(x + 2, y, z))).setItem(1, new ItemStack(Items.OAK_LOG, 6));
            chest.setItem(2, renamed(shulkerItemWith(new ItemStack(Items.IRON_INGOT, 5)), "Ore Box"));

            Vec3 from = new Vec3(x + 0.5, y + 0.5, z - 3.5);
            // The default container list, not the server's own (an owner may have changed it; the test must not care).
            ContainerSearch.Options options = new ContainerSearch.Options(new StashRadarConfig().containers, true, true);
            Predicate<ItemStack> diamond = ContainerSearch.of(Items.DIAMOND);

            List<ContainerSearch.Hit> diamonds = ContainerSearch.search(level, from, diamond, 8, options);
            checks++;
            expect(problems, "diamond containers (chest, barrel, shulker box; not the dropper)", 3, diamonds.size());
            checks++;
            expect(problems, "diamond count", 13, total(diamonds));
            checks++;
            expect(problems, "nearest container is the chest", 1, diamonds.isEmpty() || !diamonds.get(0).blockId().equals("minecraft:chest") ? 0 : 1);
            checks++;
            expect(problems, "emeralds inside a shulker box item", 3, total(ContainerSearch.search(level, from, ContainerSearch.of(Items.EMERALD), 8, options)));
            checks++;
            expect(problems, "emeralds with item search off", 0,
                    ContainerSearch.search(level, from, ContainerSearch.of(Items.EMERALD), 8, new ContainerSearch.Options(options.containers(), false, true)).size());
            checks++;
            expect(problems, "gold (not stored)", 0, ContainerSearch.search(level, from, ContainerSearch.of(Items.GOLD_INGOT), 8, options).size());
            checks++;
            expect(problems, "only chests searched", 1,
                    ContainerSearch.search(level, from, diamond, 8, new ContainerSearch.Options(Collections.singletonList("minecraft:chest"), true, true)).size());
            checks++;
            expect(problems, "dropper added to the list", 4,
                    ContainerSearch.search(level, from, diamond, 8,
                            new ContainerSearch.Options(Arrays.asList("minecraft:chest", "minecraft:barrel", "shulker_boxes", "minecraft:dropper"), true, true)).size());
            checks++;
            expect(problems, "far chest with a 24 block range", 4, ContainerSearch.search(level, from, diamond, 24, options).size());
            checks++;
            expect(problems, "direction to the chest", 1, ContainerSearch.direction(from, new BlockPos(x, y, z)).equals("south") ? 1 : 0);

            // 1.1.0: tags, names, item frames, ender chest counting.
            Predicate<ItemStack> logs = predicate(server, "find #minecraft:logs 8");
            checks++;
            expect(problems, "/find #minecraft:logs finds the barrel's oak logs", 6, total(ContainerSearch.search(level, from, logs, 8, options)));
            checks++;
            expect(problems, "/find diamond still works with the new item argument", 13,
                    total(ContainerSearch.search(level, from, predicate(server, "find diamond 8"), 8, options)));
            checks++;
            expect(problems, "name search finds the renamed \"Ore Box\"", 1, total(ContainerSearch.search(level, from, ContainerSearch.named("ore box"), 8, options)));
            checks++;
            expect(problems, "name search \"iron\" finds the iron inside it", 5, total(ContainerSearch.search(level, from, ContainerSearch.named("iron"), 8, options)));
            checks++;
            expect(problems, "label for a tag", 1, StashRadarCommands.label("#minecraft:logs").getString().equals("#logs") ? 1 : 0);
            checks++;
            expect(problems, "label for an item id", 1, StashRadarCommands.label("diamond").getString().equals("Diamond") ? 1 : 0);

            ItemFrame frame = new ItemFrame(level, new BlockPos(x - 2, y + 2, z), Direction.UP);
            frame.setItem(new ItemStack(Items.CLOCK));
            level.addFreshEntity(frame);
            try {
                List<ContainerSearch.Hit> clocks = ContainerSearch.search(level, from, ContainerSearch.of(Items.CLOCK), 8, options);
                checks++;
                expect(problems, "clock in an item frame", 1, clocks.size() == 1 && clocks.get(0).blockId().equals("item_frame") ? 1 : 0);
                checks++;
                expect(problems, "item frames off", 0,
                        ContainerSearch.search(level, from, ContainerSearch.of(Items.CLOCK), 8, new ContainerSearch.Options(options.containers(), true, false)).size());
            } finally {
                //#if MC >= 1.17
                frame.discard();
                //#else
                frame.remove();
                //#endif
            }

            SimpleContainer enderLike = new SimpleContainer(27);
            enderLike.setItem(3, new ItemStack(Items.DIAMOND, 9));
            enderLike.setItem(4, shulkerItemWith(new ItemStack(Items.DIAMOND, 2)));
            checks++;
            expect(problems, "ender chest count (with a shulker box item)", 11, ContainerSearch.count(enderLike, diamond, true));

            //#if MC >= 1.21.9
            place(level, new BlockPos(x, y, z - 2), com.cosmiclogic.stashradar.search.Ids.findBlock("minecraft:copper_chest").defaultBlockState(), placed).setItem(0, new ItemStack(Items.LAPIS_LAZULI, 3));
            checks++;
            expect(problems, "copper chests are searched by default", 3, total(ContainerSearch.search(level, from, ContainerSearch.of(Items.LAPIS_LAZULI), 8, options)));
            //#endif

            // Other dimensions: a chest in the Nether is found from the Nether, and never from the overworld.
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether != null) {
                nether.getChunk(0, 0);
                place(nether, new BlockPos(8, 100, 8), Blocks.CHEST.defaultBlockState(), placedNether).setItem(0, new ItemStack(Items.GOLD_INGOT, 9));
                checks++;
                expect(problems, "gold in a Nether chest, searched in the Nether", 9,
                        total(ContainerSearch.search(nether, new Vec3(8.5, 100.5, 5.5), ContainerSearch.of(Items.GOLD_INGOT), 8, options)));
                checks++;
                expect(problems, "the Nether chest is not found from the overworld", 0,
                        ContainerSearch.search(level, new Vec3(8.5, 100.5, 5.5), ContainerSearch.of(Items.GOLD_INGOT), 8, options).size());
            }

            // The glow through walls: the packets for the fake shulker can be built on this version.
            Glow.Ghost ghost = Glow.build(level, new BlockPos(x, y, z));
            checks++;
            expect(problems, "glow packets", 1, ghost.spawn.size() >= 1 && ghost.id != 0 ? 1 : 0);
            checks++;
            expect(problems, "outline points (1 result / 16 results)", 1,
                    Highlighter.outline(BlockPos.ZERO, 6).size() == 80 && Highlighter.outline(BlockPos.ZERO, 1).size() == 20 ? 1 : 0);

            // Commands parse (help, name search) for players and the console.
            for (String cmd : new String[]{"find", "find help", "find name \"ore box\" 8", "find #minecraft:logs", "stashradar help"}) {
                checks++;
                expect(problems, "/" + cmd + " parses", 1, parses(server, cmd) ? 1 : 0);
            }

            // Plain rules: range, cooldown per player (never growing), config upgrade.
            StashRadarConfig c = new StashRadarConfig();
            checks++;
            expect(problems, "range rules", 1, StashRadarRules.range(-1, c) == 16 && StashRadarRules.range(64, c) == 32
                    && StashRadarRules.rangeWasLimited(64, c) && !StashRadarRules.rangeWasLimited(32, c) && StashRadarRules.range(8, c) == 8 ? 1 : 0);
            StashRadarRules.Cooldowns cd = new StashRadarRules.Cooldowns();
            UUID a = new UUID(1, 1), b = new UUID(2, 2), d = new UUID(3, 3);
            checks++;
            expect(problems, "cooldowns are per player", 1, cd.tryUse(a, 1000, 5000) == 0 && cd.tryUse(a, 2000, 5000) == 4000
                    && cd.tryUse(b, 2000, 5000) == 0 && cd.tryUse(a, 6000, 5000) == 0 ? 1 : 0);
            cd.tryUse(d, 60000, 5000);
            checks++;
            expect(problems, "old cooldowns are dropped (no leak)", 1, cd.size());
            StashRadarConfig old = new StashRadarConfig();
            old.containers = new ArrayList<>(Arrays.asList("minecraft:chest", "minecraft:barrel"));
            StashRadarConfig onlyBarrels = new StashRadarConfig();
            onlyBarrels.containers = new ArrayList<>(Collections.singletonList("minecraft:barrel"));
            checks++;
            expect(problems, "config upgrade adds copper chests, keeps the owner's choices", 1,
                    StashRadarRules.upgrade(old) && old.containers.contains("copper_chests") && old.containers.size() == 3
                            && StashRadarRules.upgrade(onlyBarrels) && !onlyBarrels.containers.contains("copper_chests")
                            && !StashRadarRules.upgrade(old) ? 1 : 0);
            checks++;
            expect(problems, "the loaded config is upgraded", 1,
                    StashRadar.config().configVersion == StashRadarRules.CONFIG_VERSION ? 1 : 0);
        } catch (Throwable t) {
            problems.add("exception " + t);
        } finally {
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether != null) {
                clear(nether, placedNether);
            }
            clear(level, placed);
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

    private static void clear(ServerLevel level, List<BlockPos> placed) {
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

    private static boolean parses(MinecraftServer server, String command) {
        ParseResults<CommandSourceStack> pr = server.getCommands().getDispatcher().parse(command, server.createCommandSourceStack());
        return pr.getExceptions().isEmpty() && !pr.getReader().canRead() && pr.getContext().getCommand() != null;
    }

    /** The real /find argument, parsed the way the server parses a player's command. */
    private static Predicate<ItemStack> predicate(MinecraftServer server, String command) throws Exception {
        ParseResults<CommandSourceStack> pr = server.getCommands().getDispatcher().parse(command, server.createCommandSourceStack());
        CommandContext<CommandSourceStack> ctx = pr.getContext().build(command);
        return ItemPredicateArgument.getItemPredicate(ctx, "item");
    }

    private static ItemStack renamed(ItemStack stack, String name) {
        //#if MC >= 1.20.5
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        //#elif MC >= 1.19
        stack.setHoverName(Component.literal(name));
        //#else
        stack.setHoverName(new TextComponent(name));
        //#endif
        return stack;
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
