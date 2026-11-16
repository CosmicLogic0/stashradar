package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.StashRadarClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only half. Only loaded on the client (see StashRadarNeoForge). */
final class StashRadarNeoForgeClient {

    private StashRadarNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(StashRadarClient.KEY));
        NeoForge.EVENT_BUS.addListener((InputEvent.Key event) -> StashRadarClient.onKeyInput());
    }
}
