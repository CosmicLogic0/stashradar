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

    /** HUD on (only for shots about the chat or the HUD) or off (F1). The hand is never drawn. */
    private void hud(boolean on) {
        then(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.hideGui = !on;
            mc.gameRenderer.setRenderHand(false);
        });
    }

    private void gui(int scale) {
        then(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
        });
    }

    /** Eye at (ex, ey, ez), looking at the point (tx, ty, tz). */
    private void look(double ex, double ey, double ez, double tx, double ty, double tz) {
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        cmd(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", ex, ey - 1.62, ez, yaw, pitch));
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

    /** World shots are taken in spectator mode: no gravity, no hand, the camera stays exactly where it is put. */
    private void spectator(boolean on) {
        cmd(on ? "gamemode spectator" : "gamemode creative");
        if (!on) {
            then(2, () -> {
                Minecraft m = Minecraft.getInstance();
                m.player.getAbilities().flying = true;
                m.player.onUpdateAbilities();
                m.player.getInventory().selected = 8;
            });
        }
    }

    private void noToasts() {
        then(1, () -> Minecraft.getInstance().getToasts().clear());
    }

    private void closeScreen() {
        then(1, () -> Minecraft.getInstance().setScreen(null));
    }

    private void clearChat() {
        then(2, () -> Minecraft.getInstance().gui.getChat().clearMessages(false));
    }

    // ------------------------------------------------------------------ the tour

    private void plan() {
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
        boolean plugin = MODE.equals("plugin");
        String p = plugin ? "plugin-" : "mod-";
        String diamond = plugin ? "diamond" : "minecraft:diamond";
        String emerald = plugin ? "emerald" : "minecraft:emerald";
        String iron = plugin ? "iron_ingot" : "minecraft:iron_ingot";
        double yx = Scene.YX + 0.5, yz = Scene.YZ + 0.5;

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
            m.player.getInventory().selected = 8;   // empty slot: nothing in the hand
        });
        gui(4);
        hud(false);
        spectator(true);

        // 1. The storage wall: /find diamond outlines the 4 containers that hold diamonds
        look(7.5, -58.9, 5.0, 7.5, -59.0, 10.5);
        then(40, () -> LOG.info("SRSHOT chunks settled"));
        cmd("find " + diamond);
        shot(45, p + "01-storage-wall");

        // 2. Close-up at chest height: the outlined chest and purple shulker box, plain neighbours around them
        look(5.9, -59.0, 7.9, 5.9, -59.0, 10.5);
        cmd("find " + diamond);
        shot(45, p + "02-closeup");

        // 3. Emeralds: only the chest holding a shulker box ITEM full of emeralds lights up
        look(2.5, -59.0, 7.6, 2.5, -59.4, 10.5);
        cmd("find " + emerald);
        shot(45, p + "03-inside-shulker-item");

        // 4./5. The yard: same search with range 8 and range 24, searched from the middle of the field
        for (int r : new int[]{8, 24}) {
            look(yx, -58.4, yz, yx, -58.4, yz + 5);
            cmd("find " + iron + " " + r);
            look(yx, -49.5, yz - 13, yx, -60, yz + 1);
            shot(40, p + (r == 8 ? "04-yard-range-8" : "05-yard-range-24"));
        }

        if (!plugin) {
            // 6./7. The search screen (key K): suggestions with icons, range button, mouse over a row
            then(5, () -> ModOnly.openSearch("diamond", 16));
            then(10, () -> ModOnly.hover("Diamond"));
            shot(30, "mod-06-search-screen");
            closeScreen();
            then(5, () -> ModOnly.openSearch("shulker box", 24));
            then(10, () -> ModOnly.hover("Lime Shulker Box"));
            shot(30, "mod-07-search-shulker");
            closeScreen();
        }

        // 8. Chat close-up (HUD on, big GUI): the summary line, outlines behind it
        spectator(false);
        hud(true);
        look(7.5, -58.38, 4.5, 7.5, -59.0, 10.5);
        noToasts();
        clearChat();
        if (!plugin) {
            // picked in the search screen with a click on the "Diamond" row, exactly like a player does
            then(5, () -> ModOnly.openSearch("diamond", 16));
            then(10, () -> ModOnly.hover("Diamond"));
            then(10, ModOnly::click);
        } else {
            cmd("find " + diamond);
        }
        noToasts();
        shot(40, p + "08-chat-result");

        // 9. /find tab-complete (Tab shows the matching items)
        clearChat();
        then(5, () -> Minecraft.getInstance().setScreen(new ChatScreen(plugin ? "/find dia" : "/find minecraft:dia")));
        then(20, () -> {
            Minecraft m = Minecraft.getInstance();
            if (m.screen != null) {
                m.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            }
        });
        shot(30, p + "09-tab-complete");
        closeScreen();

        // 10. Nothing found: the plain answer, no outlines
        clearChat();
        cmd("find " + (plugin ? "netherite_ingot" : "minecraft:netherite_ingot"));
        shot(30, p + "10-chat-nothing");

        if (plugin) {
            // 11. Cooldown for normal players (operators skip it): deop, then search twice
            cmd("deop Radar");
            clearChat();
            then(10, () -> Minecraft.getInstance().player.connection.sendCommand("find " + emerald));
            then(10, () -> Minecraft.getInstance().player.connection.sendCommand("find " + emerald));
            shot(20, "plugin-11-cooldown");
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
