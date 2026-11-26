package com.cosmiclogic.stashradar;

import com.cosmiclogic.logiclib.config.LogicConfig;
import com.cosmiclogic.logiclib.platform.Services;
import com.cosmiclogic.logiclib.server.CreatorServer;

import java.util.List;
//#if MC >= 1.18
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//#endif

/** Stash Radar: "Where did I put my diamonds?" Server-checked search of nearby containers. */
public final class StashRadar {

    public static final String MOD_ID = "stashradar";
    public static final String MOD_NAME = "Stash Radar";
    //#if MC >= 1.18
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    //#else
    public static final Logger LOG = LogManager.getLogger(MOD_NAME);
    //#endif

    private static LogicConfig<StashRadarConfig> config;
    private static LogicConfig<StashRadarClientConfig> clientConfig;
    private static StashRadarFiles.Players players;

    private StashRadar() {
    }

    /** Called once by the loader-specific entrypoint. */
    public static void init() {
        java.nio.file.Path file = Services.PLATFORM.getConfigDir().resolve(MOD_ID + ".json");
        warn(file, StashRadarFiles.fixTypes(file, StashRadarConfig.class));
        config = LogicConfig.create(MOD_ID, StashRadarConfig.class, StashRadarConfig::new);
        upgradeConfig();
        checkConfig();
        CreatorServer.request(MOD_ID, config().addCreatorServer);
        LOG.info("{} loaded on {}", MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    public static StashRadarConfig config() {
        return config.get();
    }

    public static void reloadConfig() {
        warn(config.getFile(), StashRadarFiles.fixTypes(config.getFile(), StashRadarConfig.class));
        config.load();
        upgradeConfig();
        checkConfig();
    }

    /** Each player's own look (config/stashradar-players.json), loaded on first use. */
    public static synchronized StashRadarFiles.Players players() {
        if (players == null) {
            players = new StashRadarFiles.Players(Services.PLATFORM.getConfigDir().resolve(MOD_ID + "-players.json"));
        }
        return players;
    }

    /** config/stashradar-client.json (only touched by client code, so servers never get the file). */
    public static synchronized StashRadarClientConfig clientConfig() {
        if (clientConfig == null) {
            java.nio.file.Path file = Services.PLATFORM.getConfigDir().resolve(MOD_ID + "-client.json");
            warn(file, StashRadarFiles.fixTypes(file, StashRadarClientConfig.class));
            clientConfig = LogicConfig.create(MOD_ID + "-client", StashRadarClientConfig.class, StashRadarClientConfig::new);
            checkClientConfig();
        }
        return clientConfig.get();
    }

    public static void saveClientConfig() {
        clientConfig();
        clientConfig.save();
    }

    private static void checkClientConfig() {
        StashRadarClientConfig c = clientConfig.get();
        List<String> w = StashRadarSettings.validate(c, StashRadarSettings.CLIENT);
        if (c.look == null) {
            c.look = new java.util.LinkedHashMap<>();
            w.add("look wasn't a list of settings, using the server defaults");
        }
        for (String bad : StashRadarSettings.cleanLook(c.look)) {
            w.add("look: " + bad + ", using the server default");
        }
        if (!w.isEmpty()) {
            warn(clientConfig.getFile(), w);
            clientConfig.save();
        }
    }

    /** Checks every value; a bad one is logged and replaced by its default (the file is saved with the fix). */
    private static void checkConfig() {
        StashRadarConfig c = config.get();
        List<String> w = StashRadarSettings.validate(c, StashRadarSettings.SERVER);
        if (c.containers == null) {
            c.containers = new StashRadarConfig().containers;
            w.add("containers wasn't a list, using the default list");
        }
        if (c.maxRange < c.range) {
            w.add("range " + c.range + " is above maxRange " + c.maxRange + ", searches use at most " + c.maxRange);
        }
        if (!w.isEmpty()) {
            warn(config.getFile(), w);
            config.save();
        }
    }

    private static void warn(java.nio.file.Path file, List<String> warnings) {
        for (String w : warnings) {
            LOG.warn("{}: {} in {}", MOD_NAME, w, file.getFileName());
        }
    }

    /** Saves changes made in the config screen. */
    public static void saveConfig() {
        config.save();
    }

    public static java.nio.file.Path configFile() {
        return config.getFile();
    }

    private static void upgradeConfig() {
        if (StashRadarRules.upgrade(config.get())) {
            config.save();
            LOG.info("{}: config updated to version {} (your settings were kept)", MOD_NAME, StashRadarRules.CONFIG_VERSION);
        }
    }
}
