package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.logiclib.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Client side for 1.12.2: K opens the search screen, an unbound key repeats the last search, recent searches. */
public final class StashRadarClient112 {

    static KeyBinding key;
    static KeyBinding repeat;
    private static String lastSearch;
    private static List<String> recent;

    static void init() {
        key = new KeyBinding("key.stashradar.search", Keyboard.KEY_K, "key.categories.stashradar");
        repeat = new KeyBinding("key.stashradar.repeat", Keyboard.KEY_NONE, "key.categories.stashradar");
        ClientRegistry.registerKeyBinding(key);
        ClientRegistry.registerKeyBinding(repeat);
        MinecraftForge.EVENT_BUS.register(new StashRadarClient112());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || key == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        while (key.isPressed()) {
            if (mc.currentScreen == null && mc.player != null) {
                mc.displayGuiScreen(new SearchScreen112());
            }
        }
        while (repeat.isPressed()) {
            if (mc.currentScreen == null && mc.player != null) {
                if (lastSearch != null) {
                    mc.player.sendChatMessage("/" + lastSearch);
                } else {
                    mc.displayGuiScreen(new SearchScreen112());
                }
            }
        }
    }

    /** {@code query} is what follows "/find " (an item id or name "text"). */
    static void search(Minecraft mc, String query, int range) {
        List<String> list = recent();
        list.remove(query);
        list.add(0, query);
        while (list.size() > 8) {
            list.remove(list.size() - 1);
        }
        save();
        lastSearch = "find " + query + " " + range;
        if (mc.player != null) {
            mc.player.sendChatMessage("/" + lastSearch);
        }
    }

    static void forget(String query) {
        recent().remove(query);
        save();
    }

    static List<String> recent() {
        if (recent == null) {
            recent = new ArrayList<>();
            try {
                Path file = file();
                if (Files.exists(file)) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (!line.trim().isEmpty() && recent.size() < 8) {
                            recent.add(line.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
                // starts empty
            }
        }
        return recent;
    }

    private static void save() {
        try {
            Files.write(file(), recent(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // not worth bothering the player about
        }
    }

    private static Path file() {
        return Services.PLATFORM.getConfigDir().resolve("stashradar-recent.txt");
    }
}
