package com.cosmiclogic.stashradar.pageshots;

import com.cosmiclogic.stashradar.client.SearchScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/** Touches Stash Radar's own classes; only loaded in mode "mod" (the plugin run has no Stash Radar mod). */
final class ModOnly {

    private ModOnly() {
    }

    /** Opens the real search screen (what the K key opens) with this text typed in. */
    static void openSearch(String query, int range) {
        try {
            Field q = SearchScreen.class.getDeclaredField("lastQuery");
            q.setAccessible(true);
            q.set(null, query);
            Field r = SearchScreen.class.getDeclaredField("lastRange");
            r.setAccessible(true);
            r.setInt(null, range);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        Minecraft.getInstance().setScreen(new SearchScreen());
        PageShots.LOG.info("SRSHOT search screen '{}' range {}", query, range);
    }

    /** Enter in the search screen: picks the first suggestion and sends /find, as a player would. */
    static void pressEnter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
    }
}
