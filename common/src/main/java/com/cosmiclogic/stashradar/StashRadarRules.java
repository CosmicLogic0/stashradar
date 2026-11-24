package com.cosmiclogic.stashradar;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plain-Java rules shared by every build (Forge 1.12.2 included): range limits, the search cooldown,
 * config upgrades and display names. No Minecraft classes here, so the self-test can check them directly.
 */
public final class StashRadarRules {

    /** The config layout this version writes. 2 = Stash Radar 1.1.0. */
    public static final int CONFIG_VERSION = 2;

    private StashRadarRules() {
    }

    /** The range a search really uses: the player's choice (or the default), never above maxRange. */
    public static int range(int requested, StashRadarConfig config) {
        int max = Math.max(1, config.maxRange);
        return Math.max(1, Math.min(requested > 0 ? requested : config.range, max));
    }

    /** True when the player asked for more than the server allows (so we can tell them). */
    public static boolean rangeWasLimited(int requested, StashRadarConfig config) {
        return requested > Math.max(1, config.maxRange);
    }

    /**
     * Brings an older config/stashradar.json up to date without touching the owner's own choices.
     * 1.1.0: copper chests (Minecraft 1.21.9+) are searched too, if the list still searches normal chests.
     *
     * @return true if something changed (the caller saves the file)
     */
    public static boolean upgrade(StashRadarConfig config) {
        if (config.configVersion >= CONFIG_VERSION) {
            return false;
        }
        if (config.containers != null && config.containers.contains("minecraft:chest")
                && !config.containers.contains("copper_chests")) {
            config.containers.add("copper_chests");
        }
        config.configVersion = CONFIG_VERSION;
        return true;
    }

    /** "minecraft:trapped_chest" becomes "Trapped Chest". */
    public static String pretty(String id) {
        String path = id.indexOf(':') >= 0 ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * Who searched when. Old entries are dropped as new searches come in, so the map never grows past the
     * players who searched within the last cooldown (the 1.0 map kept every player forever).
     */
    public static final class Cooldowns {
        private final Map<UUID, Long> last = new ConcurrentHashMap<UUID, Long>();

        /**
         * @return 0 if the player may search now (and the search is recorded), else the milliseconds left
         */
        public long tryUse(UUID player, long now, long cooldownMillis) {
            prune(now, cooldownMillis);
            if (cooldownMillis <= 0) {
                return 0;
            }
            Long previous = last.get(player);
            if (previous != null && now - previous < cooldownMillis) {
                return cooldownMillis - (now - previous);
            }
            last.put(player, now);
            return 0;
        }

        public void forget(UUID player) {
            last.remove(player);
        }

        public int size() {
            return last.size();
        }

        private void prune(long now, long cooldownMillis) {
            for (Iterator<Map.Entry<UUID, Long>> it = last.entrySet().iterator(); it.hasNext(); ) {
                if (now - it.next().getValue() >= cooldownMillis) {
                    it.remove();
                }
            }
        }
    }
}
