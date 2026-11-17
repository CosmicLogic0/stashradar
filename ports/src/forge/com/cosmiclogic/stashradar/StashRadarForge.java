package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.command.StashRadarCommands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(StashRadar.MOD_ID)
public class StashRadarForge {

    public StashRadarForge() {
        StashRadar.init();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            StashRadarForgeClient.init(modBus);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        //#if MC >= 1.19
        StashRadarCommands.register(event.getDispatcher(), event.getBuildContext());
        //#else
        StashRadarCommands.register(event.getDispatcher());
        //#endif
    }
}
