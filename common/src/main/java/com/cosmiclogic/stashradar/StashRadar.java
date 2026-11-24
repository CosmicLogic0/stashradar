package com.cosmiclogic.stashradar;

import com.cosmiclogic.logiclib.config.LogicConfig;
import com.cosmiclogic.logiclib.platform.Services;
import com.cosmiclogic.logiclib.server.CreatorServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;





/** Stash Radar: "Where did I put my diamonds?" Server-checked search of nearby containers. */
public final class StashRadar {

    public static final String MOD_ID = "stashradar";
    public static final String MOD_NAME = "Stash Radar";

    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);




    private static LogicConfig<StashRadarConfig> config;

    private StashRadar() {
    }

    /** Called once by the loader-specific entrypoint. */
    public static void init() {
        config = LogicConfig.create(MOD_ID, StashRadarConfig.class, StashRadarConfig::new);
        upgradeConfig();
        CreatorServer.request(MOD_ID, config().addCreatorServer);
        LOG.info("{} loaded on {}", MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    public static StashRadarConfig config() {
        return config.get();
    }

    public static void reloadConfig() {
        config.load();
        upgradeConfig();
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
