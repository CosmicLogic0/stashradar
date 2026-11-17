package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.StashRadarClient;
import com.cosmiclogic.stashradar.command.StashRadarCommands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(StashRadar.MOD_ID)
public class StashRadarNeoForge {

    public StashRadarNeoForge(IEventBus modBus, Dist dist) {
        StashRadar.init();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        if (dist.isClient()) {
            Client.init(modBus);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StashRadarCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    /** Client-only hooks, kept in their own class so a dedicated server never loads client code. */
    static final class Client {
        static void init(IEventBus modBus) {
            modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> e.register(StashRadarClient.KEY));
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.Key e) -> StashRadarClient.onKeyInput());
        }
    }
}
