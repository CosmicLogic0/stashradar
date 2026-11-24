package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.command.StashRadarCommands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import net.minecraftforge.common.MinecraftForge;


@Mod(StashRadar.MOD_ID)
public class StashRadarForge {




    public StashRadarForge() {

        StashRadar.init();



        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {



            StashRadarForgeClient.init();

        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {

        StashRadarCommands.register(event.getDispatcher(), event.getBuildContext());



    }
}
