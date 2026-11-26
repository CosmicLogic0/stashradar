package com.cosmiclogic.stashradar;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every setting a player or server owner can change (1.2.0 "make it yours"), with its limits and default.
 * Plain Java (no Minecraft classes), shared by every build including Forge 1.12.2, so the rules and the
 * self-tests are the same everywhere.
 *
 * <p>Three kinds of settings:
 * <ul>
 *   <li><b>server</b>: config/stashradar.json, set by the owner (ranges, limits, defaults for everyone)</li>
 *   <li><b>look</b>: a player's own highlight look (colors, particles, sounds...). Stored per player on the server,
 *       so it also works for players without the mod (/find settings). Empty means "use the server's default".</li>
 *   <li><b>client</b>: config/stashradar-client.json, only on the player's game (HUD, recent searches)</li>
 * </ul>
 * A bad value never crashes anything: it is replaced by the default and a warning says which key and why.
 */
public final class StashRadarSettings {

    private StashRadarSettings() {
    }

    // ------------------------------------------------------------------ choices

    public static final List<String> STYLES = Collections.unmodifiableList(Arrays.asList("particles", "lines", "off"));
    /** Particle types offered. Only "dust" takes a color; versions without a type fall back to dust (see Particles). */
    public static final List<String> PARTICLES = Collections.unmodifiableList(Arrays.asList(
            "dust", "end_rod", "flame", "soul_fire_flame", "happy_villager", "crit", "glow", "electric_spark"));
    public static final List<String> CORNERS = Collections.unmodifiableList(Arrays.asList(
            "top_left", "top_right", "bottom_left", "bottom_right"));

    /** Spacing and size used by the "lines" style: small specks close together read as a thin solid line. */
    public static final double LINE_SPACING = 0.0625;
    public static final double LINE_SIZE = 0.5;

    // ------------------------------------------------------------------ color themes

    /** A preset that sets every color at once. "colorblind" uses the Okabe-Ito orange + sky blue pair. */
    public static final class Theme {
        public final String id;
        public final String outline;
        public final String glow;
        public final String chat;
        public final String hudText;
        public final String hudAccent;
        public final String hudBackground;

        Theme(String id, String outline, String glow, String chat, String hudText, String hudAccent, String hudBackground) {
            this.id = id;
            this.outline = outline;
            this.glow = glow;
            this.chat = chat;
            this.hudText = hudText;
            this.hudAccent = hudAccent;
            this.hudBackground = hudBackground;
        }
    }

    public static final List<Theme> THEMES = Collections.unmodifiableList(Arrays.asList(
            new Theme("radar", "#3CFF7A", "#FFFFFF", "#55FF55", "#FFFFFF", "#3CFF7A", "#000000"),
            new Theme("colorblind", "#E69F00", "#FFAA00", "#56B4E9", "#FFFFFF", "#E69F00", "#000000"),
            new Theme("contrast", "#FFFF00", "#FFFF55", "#FFFF55", "#FFFFFF", "#FFFF00", "#000000"),
            new Theme("ocean", "#3CC8FF", "#55FFFF", "#55FFFF", "#FFFFFF", "#3CC8FF", "#001428"),
            new Theme("sunset", "#FF8800", "#FFAA00", "#FFAA00", "#FFFFFF", "#FF8800", "#200A00"),
            new Theme("berry", "#FF55FF", "#FF55FF", "#FF55FF", "#FFFFFF", "#FF55FF", "#140014"),
            new Theme("mono", "#FFFFFF", "#FFFFFF", "#AAAAAA", "#FFFFFF", "#FFFFFF", "#000000")));

    public static Theme theme(String id) {
        for (Theme t : THEMES) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ setting descriptors

    public enum Kind { INT, DOUBLE, BOOL, COLOR, CHOICE, LIST }

    /** One setting: its key (= field name), kind, limits and default text. */
    public static final class Setting {
        public final String key;
        public final Kind kind;
        public final double min;
        public final double max;
        public final List<String> choices;
        public final String def;

        Setting(String key, Kind kind, double min, double max, List<String> choices, String def) {
            this.key = key;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.choices = choices;
            this.def = def;
        }

        /** The value in its normal written form ("#FF8800", "1.3", "true"), or null if it isn't valid. */
        public String normalize(String raw) {
            if (raw == null) {
                return null;
            }
            String v = raw.trim();
            switch (kind) {
                case INT:
                    try {
                        double d = Double.parseDouble(v);
                        if (d != Math.rint(d) || d < min || d > max) {
                            return null;
                        }
                        return String.valueOf((long) d);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                case DOUBLE:
                    try {
                        double d = Double.parseDouble(v);
                        if (Double.isNaN(d) || d < min || d > max) {
                            return null;
                        }
                        return trimNumber(d);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                case BOOL: {
                    String b = v.toLowerCase(Locale.ROOT);
                    if (b.equals("true") || b.equals("on") || b.equals("yes")) {
                        return "true";
                    }
                    if (b.equals("false") || b.equals("off") || b.equals("no")) {
                        return "false";
                    }
                    return null;
                }
                case COLOR:
                    return color(v);
                case CHOICE: {
                    String c = v.toLowerCase(Locale.ROOT);
                    return choices.contains(c) ? c : null;
                }
                default:
                    return v;
            }
        }

        /** "1-256", "on/off", "particles, lines, off", "a #RRGGBB color" for messages and tooltips. */
        public String describe() {
            switch (kind) {
                case INT:
                    return (long) min + " to " + (long) max;
                case DOUBLE:
                    return trimNumber(min) + " to " + trimNumber(max);
                case BOOL:
                    return "on or off";
                case COLOR:
                    return "a color like #FF8800 or a theme name";
                case CHOICE:
                    return String.join(", ", choices);
                default:
                    return "a list";
            }
        }
    }

    private static Setting num(String key, int min, int max, int def) {
        return new Setting(key, Kind.INT, min, max, null, String.valueOf(def));
    }

    private static Setting dbl(String key, double min, double max, double def) {
        return new Setting(key, Kind.DOUBLE, min, max, null, trimNumber(def));
    }

    private static Setting bool(String key, boolean def) {
        return new Setting(key, Kind.BOOL, 0, 0, null, String.valueOf(def));
    }

    private static Setting color(String key, String def) {
        return new Setting(key, Kind.COLOR, 0, 0, null, def);
    }

    private static Setting choice(String key, List<String> choices, String def) {
        return new Setting(key, Kind.CHOICE, 0, 0, choices, def);
    }

    /** config/stashradar.json. Keys are field names of {@link StashRadarConfig}; defaults match 1.1.0 exactly. */
    public static final List<Setting> SERVER = Collections.unmodifiableList(Arrays.asList(
            num("range", 1, 256, 16),
            num("maxRange", 1, 256, 32),
            num("cooldownSeconds", 0, 3600, 5),
            num("permissionLevel", 0, 4, 0),
            new Setting("containers", Kind.LIST, 0, 0, null, ""),
            bool("searchInsideItems", true),
            bool("searchItemFrames", true),
            bool("searchEnderChest", true),
            num("highlightSeconds", 1, 600, 12),
            color("highlightColor", "#3CFF7A"),
            num("maxHighlights", 1, 256, 16),
            bool("glowThroughWalls", true),
            bool("addCreatorServer", true),
            // 1.2.0: the server's default look for everyone (players can pick their own)
            choice("outlineStyle", STYLES, "particles"),
            choice("particleType", PARTICLES, "dust"),
            dbl("particleSize", 0.3, 4.0, 1.3),
            dbl("particleSpacing", 0.05, 1.0, 0.15),
            color("glowColor", "#FFFFFF"),
            color("chatColor", "#55FF55"),
            bool("chatResults", true),
            bool("soundOnResults", false),
            num("soundVolume", 0, 100, 100),
            // 1.2.0: the owner's limits
            num("updateMillis", 250, 5000, 500),
            num("maxParticlesPerUpdate", 50, 4000, 480),
            num("maxHighlightSeconds", 1, 600, 60),
            bool("allowPlayerSettings", true)));

    /**
     * A player's own look. Same keys as the server defaults they replace. Stored as text; a missing key means
     * "use the server's default". highlightColor is the outline color.
     */
    public static final List<Setting> LOOK = Collections.unmodifiableList(Arrays.asList(
            color("highlightColor", "#3CFF7A"),
            color("glowColor", "#FFFFFF"),
            color("chatColor", "#55FF55"),
            choice("outlineStyle", STYLES, "particles"),
            choice("particleType", PARTICLES, "dust"),
            dbl("particleSize", 0.3, 4.0, 1.3),
            dbl("particleSpacing", 0.05, 1.0, 0.15),
            num("highlightSeconds", 1, 600, 12),
            bool("glowThroughWalls", true),
            bool("chatResults", true),
            bool("soundOnResults", false),
            num("soundVolume", 0, 100, 100)));

    /** config/stashradar-client.json (only on the player's game). */
    public static final List<Setting> CLIENT = Collections.unmodifiableList(Arrays.asList(
            bool("hudEnabled", false),
            choice("hudCorner", CORNERS, "top_left"),
            num("hudOffsetX", 0, 2000, 4),
            num("hudOffsetY", 0, 2000, 4),
            dbl("hudScale", 0.5, 3.0, 1.0),
            num("hudOpacity", 0, 100, 50),
            bool("hudBackground", true),
            bool("hudCompact", false),
            bool("hudShowItem", true),
            bool("hudShowCount", true),
            bool("hudShowNearest", true),
            bool("hudShowTime", true),
            color("hudTextColor", "#FFFFFF"),
            color("hudAccentColor", "#3CFF7A"),
            color("hudBackgroundColor", "#000000"),
            bool("rememberSearches", true)));

    public static Setting find(List<Setting> list, String key) {
        for (Setting s : list) {
            if (s.key.equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ checking config objects

    /**
     * Checks every field of a loaded config object against its setting. A bad value is set back to the default
     * and a warning is returned (the caller logs them). Never throws.
     */
    public static List<String> validate(Object config, List<Setting> settings) {
        List<String> warnings = new ArrayList<String>();
        for (Setting s : settings) {
            if (s.kind == Kind.LIST) {
                continue;
            }
            try {
                Field f = config.getClass().getField(s.key);
                Object value = f.get(config);
                String text = value == null ? null : (value instanceof Double ? trimNumber((Double) value) : String.valueOf(value));
                String ok = s.normalize(text);
                if (ok == null) {
                    warnings.add(s.key + " = " + text + " isn't valid (" + s.describe() + "), using the default " + s.def);
                    set(f, config, s.def);
                } else if (!ok.equals(text)) {
                    set(f, config, ok);   // "#ff8800" -> "#FF8800", "ON" -> true
                }
            } catch (Exception e) {
                warnings.add(s.key + " couldn't be checked (" + e + ")");
            }
        }
        return warnings;
    }

    /** Puts every setting back to its default ("Reset to defaults"). */
    public static void reset(Object config, List<Setting> settings) {
        for (Setting s : settings) {
            if (s.kind == Kind.LIST) {
                continue;
            }
            try {
                set(config.getClass().getField(s.key), config, s.def);
            } catch (Exception ignored) {
                // every key is a field (the self-test checks it)
            }
        }
    }

    /** Sets one field from text; returns false (and leaves the field alone) if the text isn't valid. */
    public static boolean setText(Object config, Setting s, String raw) {
        String ok = s.normalize(raw);
        if (ok == null) {
            return false;
        }
        try {
            set(config.getClass().getField(s.key), config, ok);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getText(Object config, Setting s) {
        try {
            Object v = config.getClass().getField(s.key).get(config);
            return v instanceof Double ? trimNumber((Double) v) : String.valueOf(v);
        } catch (Exception e) {
            return s.def;
        }
    }

    private static void set(Field f, Object config, String text) throws IllegalAccessException {
        Class<?> t = f.getType();
        if (t == int.class || t == Integer.class) {
            f.set(config, (int) Double.parseDouble(text));
        } else if (t == double.class || t == Double.class) {
            f.set(config, Double.parseDouble(text));
        } else if (t == boolean.class || t == Boolean.class) {
            f.set(config, Boolean.parseBoolean(text));
        } else {
            f.set(config, text);
        }
    }

    // ------------------------------------------------------------------ JSON type check (before Gson reads it)

    /**
     * Which JSON type each field needs: "number", "integer", "boolean", "string", "list". Used to fix single
     * wrong-typed values (e.g. "range": "abc") before the file is read, so one typo doesn't reset the whole file.
     */
    public static String jsonType(Class<?> fieldType) {
        if (fieldType == int.class || fieldType == Integer.class || fieldType == long.class) {
            return "integer";
        }
        if (fieldType == double.class || fieldType == Double.class || fieldType == float.class) {
            return "number";
        }
        if (fieldType == boolean.class || fieldType == Boolean.class) {
            return "boolean";
        }
        if (List.class.isAssignableFrom(fieldType)) {
            return "list";
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            return "object";
        }
        return "string";
    }

    // ------------------------------------------------------------------ a player's look (key -> text)

    /** Checks a player's look map: drops unknown keys and bad values. Returns warnings (for /find settings). */
    public static List<String> cleanLook(Map<String, String> look) {
        List<String> warnings = new ArrayList<String>();
        Map<String, String> clean = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : look.entrySet()) {
            Setting s = find(LOOK, e.getKey());
            if (s == null) {
                warnings.add(e.getKey() + " isn't a setting");
                continue;
            }
            String v = e.getValue();
            if (v == null || v.trim().isEmpty() || v.trim().equalsIgnoreCase("default")) {
                continue;   // server default
            }
            String ok = s.normalize(v);
            if (ok == null) {
                warnings.add(s.key + " = " + v + " isn't valid (" + s.describe() + ")");
            } else {
                clean.put(s.key, ok);
            }
        }
        look.clear();
        look.putAll(clean);
        return warnings;
    }

    /** "highlightColor=#FF8800;outlineStyle=lines" (how the mod sends a player's look to the server). */
    public static String encode(Map<String, String> look) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : look.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public static Map<String, String> decode(String text) {
        Map<String, String> look = new LinkedHashMap<String, String>();
        if (text == null) {
            return look;
        }
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                look.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        return look;
    }

    // ------------------------------------------------------------------ the look a search really uses

    /** The look a highlight uses: the player's choices over the server's defaults, within the owner's limits. */
    public static final class Look {
        public int outline;
        public int glow;
        public int chat;
        public String style;
        public String particle;
        public double size;
        public double spacing;
        public int seconds;
        public boolean glowThroughWalls;
        public boolean chatResults;
        public boolean sound;
        public float volume;
    }

    public static Look effective(StashRadarConfig server, Map<String, String> mine) {
        Map<String, String> p = server.allowPlayerSettings && mine != null ? mine : Collections.<String, String>emptyMap();
        Look l = new Look();
        l.outline = rgb(pick(p, "highlightColor", server.highlightColor, "#3CFF7A"));
        l.glow = rgb(pick(p, "glowColor", server.glowColor, "#FFFFFF"));
        l.chat = rgb(pick(p, "chatColor", server.chatColor, "#55FF55"));
        l.style = pick(p, "outlineStyle", server.outlineStyle, "particles");
        l.particle = pick(p, "particleType", server.particleType, "dust");
        l.size = Double.parseDouble(pick(p, "particleSize", trimNumber(server.particleSize), "1.3"));
        l.spacing = Double.parseDouble(pick(p, "particleSpacing", trimNumber(server.particleSpacing), "0.15"));
        l.seconds = Math.min((int) Double.parseDouble(pick(p, "highlightSeconds", String.valueOf(server.highlightSeconds), "12")),
                Math.max(server.highlightSeconds, server.maxHighlightSeconds));
        // The owner's switch wins: if the server turns glow off, nobody gets it.
        l.glowThroughWalls = server.glowThroughWalls && Boolean.parseBoolean(pick(p, "glowThroughWalls", "true", "true"));
        l.chatResults = Boolean.parseBoolean(pick(p, "chatResults", String.valueOf(server.chatResults), "true"));
        l.sound = Boolean.parseBoolean(pick(p, "soundOnResults", String.valueOf(server.soundOnResults), "false"));
        l.volume = (int) Double.parseDouble(pick(p, "soundVolume", String.valueOf(server.soundVolume), "100")) / 100f;
        return l;
    }

    /** The player's value if valid, else the server's if valid, else the built-in default. */
    private static String pick(Map<String, String> mine, String key, String server, String builtIn) {
        Setting s = find(LOOK, key);
        String v = s.normalize(mine.get(key));
        if (v != null) {
            return v;
        }
        v = s.normalize(server);
        return v != null ? v : builtIn;
    }

    /**
     * Points per box edge for one redraw: as many as the spacing asks for (1.04 blocks per edge), but all
     * outlines together stay within the owner's particle budget. Defaults give the 1.1.0 result exactly.
     */
    public static int perEdge(double spacing, int boxes, int budget) {
        int wanted = Math.max(1, (int) Math.round(1.04 / Math.max(0.01, spacing)) - 1);
        int fits = (budget / Math.max(1, boxes) - 8) / 12;
        return Math.max(1, Math.min(wanted, fits));
    }

    // ------------------------------------------------------------------ colors

    /** "#ff8800", "ff8800", "#F80" or a theme name ("colorblind" = that theme's outline color) to "#FF8800". */
    public static String color(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        Theme t = theme(v);
        if (t != null) {
            return t.outline;
        }
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        if (v.matches("[0-9a-fA-F]{3}")) {
            v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
        }
        return v.matches("[0-9a-fA-F]{6}") ? "#" + v.toUpperCase(Locale.ROOT) : null;
    }

    public static int rgb(String hex) {
        String c = color(hex);
        return c == null ? 0xFFFFFF : Integer.parseInt(c.substring(1), 16);
    }

    /** The 16 chat colors (index = ChatFormatting color id 0-15). Glow outlines can only use these. */
    public static final int[] CHAT_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** The chat color nearest to an RGB color (for the glow outline). */
    public static int nearestChatColor(int rgb) {
        int best = 15;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < CHAT_COLORS.length; i++) {
            int c = CHAT_COLORS[i];
            long dr = ((rgb >> 16) & 0xFF) - ((c >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((c >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (c & 0xFF);
            long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    public static String trimNumber(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) {
            return String.valueOf((long) d) + ".0";
        }
        String s = String.format(Locale.ROOT, "%.4f", d);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }
}
