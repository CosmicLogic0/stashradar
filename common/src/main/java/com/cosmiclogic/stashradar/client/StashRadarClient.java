package com.cosmiclogic.stashradar.client;

import com.cosmiclogic.logiclib.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side: the keys (open the search screen, repeat the last search), sending searches and the list of
 * recent searches. Loader entrypoints register {@link #KEYS} and call {@link #onKeyInput()}.
 */
public final class StashRadarClient {

    /** GLFW_KEY_K. Written as a number: 26.x no longer ships org.lwjgl.glfw on the compile classpath. */
    private static final int KEY_K = 75;
    /** Not bound until the player picks a key in Controls. */
    private static final int UNBOUND = -1;


















    public static final String CATEGORY = "key.categories.stashradar";
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, KEY_K, CATEGORY);
    public static final KeyMapping REPEAT = new KeyMapping("key.stashradar.repeat", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);


    /** Every key to register in Controls. */
    public static final KeyMapping[] KEYS = {KEY, REPEAT};

    private static final int MAX_RECENT = 8;
    private static List<String> recent;
    private static String lastSearch;

    private StashRadarClient() {
    }

    public static void onKeyInput() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (screen(mc) == null && mc.player != null) {
                open(mc, new SearchScreen());
            }
        }
        while (REPEAT.consumeClick()) {
            if (screen(mc) == null && mc.player != null) {
                if (lastSearch != null) {
                    send(mc, lastSearch);
                } else {
                    open(mc, new SearchScreen());   // nothing to repeat yet: let them search first
                }
            }
        }
    }

    static Object screen(Minecraft mc) {



        return mc.screen;

    }

    static void open(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {



        mc.setScreen(screen);

    }

    /** Asks the server to search: {@code query} is what follows "/find " (an item id, "#tag" or name "text"). */
    static void search(Minecraft mc, String query, int range) {
        rememberRecent(query);
        lastSearch = "find " + query + " " + range;
        send(mc, lastSearch);
    }

    private static void send(Minecraft mc, String command) {
        if (mc.player == null) {
            return;
        }

        mc.player.connection.sendCommand(command);





    }

    /** True when the server lets this player use /find (it has Stash Radar and allows it). */
    static boolean serverHasFind(Minecraft mc) {
        try {
            return mc.getConnection() == null || mc.getConnection().getCommands().getRoot().getChild("find") != null;
        } catch (Throwable t) {
            return true;   // can't tell: let the server answer
        }
    }

    // ---- recent searches, kept in config/stashradar-recent.txt ----

    static List<String> recent() {
        if (recent == null) {
            recent = new ArrayList<>();
            try {
                Path file = recentFile();
                if (Files.exists(file)) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (!line.trim().isEmpty() && recent.size() < MAX_RECENT) {
                            recent.add(line.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
                // an unreadable list just starts empty
            }
        }
        return recent;
    }

    static void forgetRecent(String query) {
        recent().remove(query);
        saveRecent();
    }

    private static void rememberRecent(String query) {
        List<String> list = recent();
        list.remove(query);
        list.add(0, query);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        saveRecent();
    }

    private static void saveRecent() {
        try {
            Files.write(recentFile(), recent(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // not worth bothering the player about
        }
    }

    private static Path recentFile() {
        return Services.PLATFORM.getConfigDir().resolve("stashradar-recent.txt");
    }
}
