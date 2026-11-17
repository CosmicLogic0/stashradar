package com.cosmiclogic.stashradar.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client side: the key that opens the search screen. Loader entrypoints register KEY and call onKeyInput(). */
public final class StashRadarClient {

    /** GLFW_KEY_K. Written as a number: 26.x no longer ships org.lwjgl.glfw on the compile classpath. */
    private static final int KEY_K = 75;

    //#if MC >= 26.1
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("stashradar", "main"));
    // values()[0] is KEYSYM up to 26.2 and KEYBOARD from 26.3: the keyboard either way.
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.values()[0], KEY_K, CATEGORY);
    //#elif MC >= 1.21.11
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("stashradar", "main"));
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, KEY_K, CATEGORY);
    //#elif MC >= 1.21.9
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("stashradar", "main"));
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, KEY_K, CATEGORY);
    //#else
    public static final String CATEGORY = "key.categories.stashradar";
    public static final KeyMapping KEY = new KeyMapping("key.stashradar.search", InputConstants.Type.KEYSYM, KEY_K, CATEGORY);
    //#endif

    private StashRadarClient() {
    }

    public static void onKeyInput() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            //#if MC >= 26.2
            if (mc.gui.screen() == null && mc.player != null) {
                mc.gui.setScreen(new SearchScreen());
            }
            //#else
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new SearchScreen());
            }
            //#endif
        }
    }
}
