package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.command.StashRadarCommands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
//#if MC < 1.21.6
import net.minecraftforge.common.MinecraftForge;
//#endif

@Mod(StashRadar.MOD_ID)
public class StashRadarForge {

    //#if MC >= 26.1
    public StashRadarForge(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
    //#else
    public StashRadarForge() {
    //#endif
        StashRadar.init();
        //#if MC >= 1.21.6
        RegisterCommandsEvent.BUS.addListener(this::onRegisterCommands);
        //#else
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        //#endif
        if (FMLEnvironment.dist == Dist.CLIENT) {
            //#if MC >= 26.1
            StashRadarForgeClient.init(context);
            //#else
            StashRadarForgeClient.init();
            //#endif
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
