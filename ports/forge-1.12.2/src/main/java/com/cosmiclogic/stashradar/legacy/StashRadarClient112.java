package com.cosmiclogic.stashradar.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/** Client side for 1.12.2: the K key opens the search screen. */
public final class StashRadarClient112 {

    static KeyBinding key;

    static void init() {
        key = new KeyBinding("key.stashradar.search", Keyboard.KEY_K, "key.categories.stashradar");
        ClientRegistry.registerKeyBinding(key);
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
    }
}
