package com.cosmiclogic.stashradar.pageshots;

import com.cosmiclogic.stashradar.client.SearchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Touches Stash Radar's own classes; only loaded in mode "mod" (the plugin run has no Stash Radar mod). */
final class ModOnly {

    private static double guiX, guiY;

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

    /** Moves the real mouse pointer over the suggestion row with this name (the row lights up). */
    static void hover(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof SearchScreen screen)) {
            throw new IllegalStateException("search screen not open");
        }
        try {
            Field f = SearchScreen.class.getDeclaredField("results");
            f.setAccessible(true);
            List<?> results = (List<?>) f.get(screen);
            int row = -1;
            for (int i = 0; i < results.size(); i++) {
                Object e = results.get(i);
                Field n = e.getClass().getDeclaredField("name");
                n.setAccessible(true);
                if (name.equals(n.get(e))) {
                    row = i;
                    break;
                }
            }
            if (row < 0) {
                throw new IllegalStateException("no row named " + name);
            }
            Method top = SearchScreen.class.getDeclaredMethod("listTop");
            top.setAccessible(true);
            int listTop = (int) top.invoke(screen);
            guiX = screen.width / 2.0 - 60;
            guiY = listTop + row * 20 + 10;
            double scale = mc.getWindow().getGuiScale();
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), guiX * scale, guiY * scale);
            for (String fn : new String[]{"xpos", "ypos"}) {
                Field mf = MouseHandler.class.getDeclaredField(fn);
                mf.setAccessible(true);
                mf.setDouble(mc.mouseHandler, fn.equals("xpos") ? guiX * scale : guiY * scale);
            }
            PageShots.LOG.info("SRSHOT mouse over row {} '{}'", row, name);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** False when the open search screen says the server has no Stash Radar. */
    static boolean searchAvailable() {
        try {
            Field f = SearchScreen.class.getDeclaredField("available");
            f.setAccessible(true);
            return f.getBoolean(Minecraft.getInstance().screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void openSettings() {
        Minecraft.getInstance().setScreen(new com.cosmiclogic.stashradar.client.ConfigScreen(null));
    }

    static boolean settingsOpen() {
        return Minecraft.getInstance().screen instanceof com.cosmiclogic.stashradar.client.ConfigScreen;
    }

    /** Left click where the mouse is (after hover): the search screen picks that row and sends /find. */
    static void click() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.mouseClicked(guiX, guiY, 0);
            PageShots.LOG.info("SRSHOT clicked at {} {}", guiX, guiY);
        }
    }
}
