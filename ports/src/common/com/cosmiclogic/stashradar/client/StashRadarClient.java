package com.cosmiclogic.stashradar.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Client side: the key that opens the search screen. Loader entrypoints register KEY and call onKeyInput(). */
public final class StashRadarClient {

    //#if MC >= 1.21.11
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("stashradar", "main"));
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    //#elif MC >= 1.21.9
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("stashradar", "main"));
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    //#else
    public static final String CATEGORY = "key.categories.stashradar";
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    //#endif

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
