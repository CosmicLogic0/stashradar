package com.cosmiclogic.stashradar.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Client side: the key that opens the search screen. Loader entrypoints register KEY and call onKeyInput(). */
public final class StashRadarClient {

    public static final String CATEGORY = "key.categories.stashradar";
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private StashRadarClient() {
    }

    public static void onKeyInput() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new SearchScreen());
            }
        }
    }
}
