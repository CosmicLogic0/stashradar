package com.cosmiclogic.stashradar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File helpers for 1.2.0: fixing single wrong-typed values before a config is read (so one typo doesn't reset
 * the whole file), and the per-player looks (config/stashradar-players.json). Shared by every build.
 */
public final class StashRadarFiles {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StashRadarFiles() {
    }

    @SuppressWarnings("deprecation")
    static JsonElement parse(String text) {
        return new JsonParser().parse(text);
    }

    /**
     * Removes values whose JSON type can't fit the field (e.g. {@code "range": "abc"}, {@code "hudScale": true}),
     * so the config loader fills in the default for just that key. Values that are written as text but mean the
     * right thing ({@code "range": "20"}, {@code "glowThroughWalls": "off"}) are fixed in place.
     * A file that isn't JSON at all is left alone (the loader backs it up as .broken and starts fresh).
     *
     * @return warnings to log, one per fixed key
     */
    public static List<String> fixTypes(Path file, Class<?> type) {
        List<String> warnings = new ArrayList<String>();
        if (file == null || !Files.exists(file)) {
            return warnings;
        }
        JsonObject root;
        try {
            JsonElement e = parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (e == null || !e.isJsonObject()) {
                return warnings;
            }
            root = e.getAsJsonObject();
        } catch (Exception broken) {
            return warnings;
        }
        JsonObject fixed = new JsonObject();
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            JsonElement v = entry.getValue();
            Field f;
            try {
                f = type.getField(key);
            } catch (NoSuchFieldException unknown) {
                fixed.add(key, v);   // unknown keys are the loader's business (it drops them)
                continue;
            }
            String want = StashRadarSettings.jsonType(f.getType());
            JsonElement ok = fit(v, want);
            if (ok == null) {
                warnings.add(key + " = " + v + " isn't a " + want + ", using the default");
                changed = true;
            } else {
                if (ok != v) {
                    changed = true;
                }
                fixed.add(key, ok);
            }
        }
        if (changed) {
            try {
                Files.write(file, GSON.toJson(fixed).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                warnings.add("couldn't save the fixed file (" + e + ")");
            }
        }
        return warnings;
    }

    /** The value in the JSON type the field needs, or null if it can't be. Returns {@code v} itself if it already fits. */
    static JsonElement fit(JsonElement v, String want) {
        if (v == null || v.isJsonNull()) {
            return null;
        }
        if (want.equals("list")) {
            return v.isJsonArray() ? v : null;
        }
        if (want.equals("object")) {
            return v.isJsonObject() ? v : null;
        }
        if (!v.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = v.getAsJsonPrimitive();
        if (want.equals("string")) {
            return p.isString() ? v : new JsonPrimitive(p.getAsString());
        }
        if (want.equals("boolean")) {
            if (p.isBoolean()) {
                return v;
            }
            String s = p.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
            if (s.equals("true") || s.equals("on") || s.equals("yes")) {
                return new JsonPrimitive(true);
            }
            if (s.equals("false") || s.equals("off") || s.equals("no")) {
                return new JsonPrimitive(false);
            }
            return null;
        }
        double d;
        try {
            d = p.isNumber() ? p.getAsDouble() : Double.parseDouble(p.getAsString().trim());
        } catch (Exception e) {
            return null;
        }
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        if (want.equals("integer")) {
            if (d != Math.rint(d) || Math.abs(d) > Integer.MAX_VALUE) {
                return null;
            }
            return p.isNumber() && p.getAsDouble() == d && !p.getAsString().contains(".") ? v : new JsonPrimitive((int) d);
        }
        return p.isNumber() ? v : new JsonPrimitive(d);
    }

    // ------------------------------------------------------------------ per-player looks

    /** Each player's own look (uuid -> key -> value), kept in config/stashradar-players.json. */
    public static final class Players {
        private final Path file;
        private final Map<UUID, Map<String, String>> looks = new ConcurrentHashMap<UUID, Map<String, String>>();
        /** Players whose game has the mod (they sent their look); only they get the HUD data. */
        private final Set<UUID> modded = ConcurrentHashMap.newKeySet();

        public Players(Path file) {
            this.file = file;
            load();
        }

        public Map<String, String> get(UUID player) {
            Map<String, String> m = looks.get(player);
            return m == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(m);
        }

        /** Replaces a player's look (already checked). Empty = back to the server defaults. */
        public void put(UUID player, Map<String, String> look) {
            if (look.isEmpty()) {
                looks.remove(player);
            } else {
                looks.put(player, new LinkedHashMap<String, String>(look));
            }
            save();
        }

        public void markModded(UUID player, boolean has) {
            if (has) {
                modded.add(player);
            } else {
                modded.remove(player);
            }
        }

        public boolean isModded(UUID player) {
            return modded.contains(player);
        }

        public int size() {
            return looks.size();
        }

        private void load() {
            looks.clear();
            if (file == null || !Files.exists(file)) {
                return;
            }
            try {
                JsonElement e = parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                for (Map.Entry<String, JsonElement> p : e.getAsJsonObject().entrySet()) {
                    try {
                        Map<String, String> look = new LinkedHashMap<String, String>();
                        for (Map.Entry<String, JsonElement> kv : p.getValue().getAsJsonObject().entrySet()) {
                            look.put(kv.getKey(), kv.getValue().isJsonPrimitive() ? kv.getValue().getAsString() : "");
                        }
                        List<String> bad = StashRadarSettings.cleanLook(look);
                        if (!bad.isEmpty()) {
                            StashRadar.LOG.warn("Stash Radar: {} in {} for {}: using the default for {}",
                                    "bad setting", file.getFileName(), p.getKey(), bad);
                        }
                        if (!look.isEmpty()) {
                            looks.put(UUID.fromString(p.getKey()), look);
                        }
                    } catch (Exception one) {
                        StashRadar.LOG.warn("Stash Radar: skipped an unreadable player entry in {} ({})", file.getFileName(), one.toString());
                    }
                }
            } catch (Exception e) {
                StashRadar.LOG.warn("Stash Radar: {} is broken ({}), players start with the server defaults", file.getFileName(), e.toString());
            }
        }

        private synchronized void save() {
            if (file == null) {
                return;
            }
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, Map<String, String>> p : looks.entrySet()) {
                JsonObject o = new JsonObject();
                for (Map.Entry<String, String> kv : p.getValue().entrySet()) {
                    o.addProperty(kv.getKey(), kv.getValue());
                }
                root.add(p.getKey().toString(), o);
            }
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                StashRadar.LOG.warn("Stash Radar: couldn't save {} ({})", file.getFileName(), e.toString());
            }
        }
    }
}
