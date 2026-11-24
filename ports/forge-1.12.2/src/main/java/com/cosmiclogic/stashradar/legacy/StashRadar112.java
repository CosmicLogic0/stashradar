package com.cosmiclogic.stashradar.legacy;

import com.cosmiclogic.stashradar.StashRadar;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Stash Radar for Forge 1.12.2. The search runs on the server; players without the mod can join and
 * use /find (acceptableRemoteVersions = "*"), players with it also get the search screen (key K).
 * Startup and config are the shared {@link StashRadar} class, same as every other version.
 */
@Mod(modid = StashRadar.MOD_ID, name = StashRadar.MOD_NAME, version = "1.1.0",
        dependencies = "required-after:logiclib@[0.1.0,)",
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public class StashRadar112 {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        StashRadar.init();
        MinecraftForge.EVENT_BUS.register(new Highlighter112());
        if (event.getSide().isClient()) {
            ClientStart.run();
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FindCommand112());
        event.registerServerCommand(new StashRadarCommand112());
    }

    /** In its own class so dedicated servers never load client code. */
    static final class ClientStart {
        static void run() {
            StashRadarClient112.init();
        }
    }
}
