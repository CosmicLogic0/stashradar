package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.StashRadarClient;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
//#if MC >= 1.19
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#else
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//#endif

/** Client-only half. Only loaded on the client (see StashRadarForge). */
final class StashRadarForgeClient {

    private StashRadarForgeClient() {
    }

    static void init(IEventBus modBus) {
        //#if MC >= 1.19
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(StashRadarClient.KEY));
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key event) -> StashRadarClient.onKeyInput());
        //#else
        modBus.addListener((FMLClientSetupEvent event) -> ClientRegistry.registerKeyBinding(StashRadarClient.KEY));
        MinecraftForge.EVENT_BUS.addListener((InputEvent.KeyInputEvent event) -> StashRadarClient.onKeyInput());
        //#endif
    }
}
