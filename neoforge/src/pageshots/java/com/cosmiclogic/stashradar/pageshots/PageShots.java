package com.cosmiclogic.stashradar.pageshots;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * CI only, never released: when the game is started with -Dstashradar.pageshots=mod|plugin it joins the local
 * Superflat test server, sets noon + clear weather, builds a small storage scene with ordinary commands, runs real
 * searches and saves 1920x1080 screenshots for the CurseForge gallery. Nothing is drawn or faked: every picture is
 * the real game with the real mod (mode "mod", NeoForge server) or the real plugin (mode "plugin", Paper server).
 */
@Mod(value = "stashradar_pageshots", dist = Dist.CLIENT)
public final class PageShots {

    static final Logger LOG = LogManager.getLogger("SRSHOT");
    private static final String MODE = System.getProperty("stashradar.pageshots", "");
    private static final String SERVER = System.getProperty("stashradar.pageshots.server", "127.0.0.1:25565");

    /** A step: wait this many ticks, then run. */
    private record Step(int delay, Runnable run) {
    }

    private final Deque<Step> steps = new ArrayDeque<>();
    private int waiting;
    private boolean connected;
    private int inWorldTicks;

    public PageShots(IEventBus modBus) {
        if (MODE.isEmpty()) {
            return;
        }
        LOG.info("SRSHOT mode {} server {}", MODE, SERVER);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!connected) {
                if (mc.screen instanceof TitleScreen title) {
                    connected = true;
                    LOG.info("SRSHOT connecting");
                    ConnectScreen.startConnecting(title, mc, ServerAddress.parseString(SERVER),
                            new ServerData("Gallery", SERVER, ServerData.Type.OTHER), false, null);
                }
                return;
            }
            if (mc.player == null || mc.level == null) {
                return;
            }
            if (inWorldTicks++ == 0) {
                LOG.info("SRSHOT in world");
                plan();
            }
            if (waiting > 0) {
                waiting--;
                return;
            }
            Step s = steps.poll();
            if (s == null) {
                return;
            }
            s.run().run();
            Step next = steps.peek();
            waiting = next == null ? 0 : next.delay();
        } catch (Throwable t) {
            LOG.error("SRSHOT failed", t);
            steps.clear();
            LOG.info("SRSHOT done (with an error)");
        }
    }

    // ------------------------------------------------------------------ helpers used by the plan

    private void then(int wait, Runnable r) {
        if (steps.isEmpty()) {
            waiting = wait;
        }
        steps.add(new Step(wait, r));
    }

    private void cmd(String c) {
        then(2, () -> {
            LOG.info("SRSHOT /{}", c);
            Minecraft.getInstance().player.connection.sendCommand(c);
        });
    }

    private void cmds(List<String> list) {
        for (String c : list) {
            cmd(c);
        }
    }

    private void hud(boolean on) {
        then(1, () -> Minecraft.getInstance().options.hideGui = !on);
    }

    private void gui(int scale) {
        then(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
        });
    }

    /** Feet at (x, y, z), looking yaw/pitch (yaw 0 = south). */
    private void camera(double x, double y, double z, float yaw, float pitch) {
        cmd(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    private void shot(int wait, String name) {
        then(wait, () -> {
            Minecraft mc = Minecraft.getInstance();
            Window w = mc.getWindow();
            LOG.info("SRSHOT framebuffer {}x{} for {}", w.getWidth(), w.getHeight(), name);
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                    msg -> LOG.info("SRSHOT saved {}", name));
        });
    }

    private void closeScreen() {
        then(1, () -> Minecraft.getInstance().setScreen(null));
    }

    // ------------------------------------------------------------------ the tour

    private void plan() {
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
        boolean plugin = MODE.equals("plugin");
        String diamond = plugin ? "diamond" : "minecraft:diamond";
        String emerald = plugin ? "emerald" : "minecraft:emerald";
        String iron = plugin ? "iron_ingot" : "minecraft:iron_ingot";

        // world: noon, clear, nothing moving
        then(100, () -> LOG.info("SRSHOT setting up"));
        cmds(List.of("gamemode creative", "gamerule doDaylightCycle false", "time set 6000", "weather clear 1000000",
                "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule doFireTick false",
                "kill @e[type=!player]"));
        cmds(Scene.build());
        cmds(Scene.hotbar());
        then(1, () -> {
            Minecraft m = Minecraft.getInstance();
            m.player.getAbilities().flying = true;
            m.player.onUpdateAbilities();
        });
        gui(3);

        // 1. The storage wall: diamonds found, outlines on the containers that have them
        hud(false);
        camera(7.5, -60, 2.6, 0, 9);
        cmd("find " + diamond);
        shot(45, plugin ? "plugin-01-storage-diamonds" : "mod-01-storage-diamonds");

        // 2. Closer: the outlined chest, barrel and shulker box fill the frame
        camera(6.2, -60, 6.4, -12, 18);
        cmd("find " + diamond);
        shot(45, plugin ? "plugin-02-closeup" : "mod-02-closeup");

        // 3. Emeralds: only the chest holding a shulker box ITEM full of emeralds lights up
        camera(4.2, -60, 5.6, 22, 20);
        cmd("find " + emerald);
        shot(45, plugin ? "plugin-03-inside-shulker" : "mod-03-inside-shulker");

        // 4. The yard: iron in containers spread around, found with a 24 block range
        camera(Scene.YX, -48, Scene.YZ - 13, 0, 38);
        cmd("find " + iron + " 24");
        shot(55, plugin ? "plugin-04-yard-range" : "mod-04-yard-range");

        if (!plugin) {
            // 5./6. The search screen (key K): suggestions with icons, range button
            gui(4);
            then(5, () -> ModOnly.openSearch("diamond", 16));
            shot(40, "mod-05-search-screen");
            closeScreen();
            then(5, () -> ModOnly.openSearch("shulker", 24));
            shot(40, "mod-06-search-shulker");
            closeScreen();
        }

        // 7. Chat summary + outlines, HUD on (this shot is about the chat)
        gui(3);
        hud(true);
        camera(7.5, -60, 2.6, 0, 9);
        then(5, () -> Minecraft.getInstance().gui.getChat().clearMessages(false));
        if (!plugin) {
            // picked from the search screen with Enter, exactly like a player does
            then(5, () -> ModOnly.openSearch("diamond", 16));
            then(30, ModOnly::pressEnter);
        } else {
            cmd("find " + diamond);
        }
        shot(40, plugin ? "plugin-05-chat-result" : "mod-07-chat-result");

        // 8. /find tab-complete
        then(5, () -> Minecraft.getInstance().gui.getChat().clearMessages(false));
        then(5, () -> Minecraft.getInstance().setScreen(new ChatScreen(plugin ? "/find dia" : "/find minecraft:dia")));
        shot(40, plugin ? "plugin-06-tab-complete" : "mod-08-tab-complete");
        closeScreen();

        if (plugin) {
            // 9. Cooldown for normal players (operators skip it): deop, then search twice
            cmd("deop @s");
            then(5, () -> Minecraft.getInstance().gui.getChat().clearMessages(false));
            then(10, () -> Minecraft.getInstance().player.connection.sendCommand("find " + emerald));
            then(10, () -> Minecraft.getInstance().player.connection.sendCommand("find " + emerald));
            shot(20, "plugin-07-cooldown");
        }

        then(10, () -> {
            Minecraft.getInstance().options.hideGui = false;
            LOG.info("SRSHOT done");
        });
    }

    static List<String> list(String... s) {
        return new ArrayList<>(List.of(s));
    }
}
