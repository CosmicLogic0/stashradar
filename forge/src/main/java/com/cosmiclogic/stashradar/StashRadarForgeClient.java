package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.StashRadarClient;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client-only half. Only loaded on the client (see StashRadarForge). */
final class StashRadarForgeClient {

    private StashRadarForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(StashRadarClient.KEY));
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key event) -> StashRadarClient.onKeyInput());
    }
}
