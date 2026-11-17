package com.cosmiclogic.stashradar;

import com.cosmiclogic.logiclib.config.LogicConfig;
import com.cosmiclogic.logiclib.platform.Services;
import com.cosmiclogic.logiclib.server.CreatorServer;
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

    private StashRadar() {
    }

    /** Called once by the loader-specific entrypoint. */
    public static void init() {
        config = LogicConfig.create(MOD_ID, StashRadarConfig.class, StashRadarConfig::new);
        CreatorServer.request(MOD_ID, config().addCreatorServer);
        LOG.info("{} loaded on {}", MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    public static StashRadarConfig config() {
        return config.get();
    }

    public static void reloadConfig() {
        config.load();
    }
}
