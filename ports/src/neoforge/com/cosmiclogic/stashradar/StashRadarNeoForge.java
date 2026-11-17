package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.command.StashRadarCommands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(StashRadar.MOD_ID)
public class StashRadarNeoForge {

    public StashRadarNeoForge(IEventBus modBus) {
        StashRadar.init();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            StashRadarNeoForgeClient.init(modBus);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StashRadarCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
