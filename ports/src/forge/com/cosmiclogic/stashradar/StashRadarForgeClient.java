package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.StashRadarClient;
import net.minecraftforge.client.event.InputEvent;
//#if MC < 1.21.6
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#endif
//#if MC >= 1.19
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#elif MC >= 1.17
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//#else
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//#endif

/** Client-only half. Only loaded on the client (see StashRadarForge). */
final class StashRadarForgeClient {

    private StashRadarForgeClient() {
    }

    //#if MC >= 1.21.6
    static void init(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        //#if MC >= 26.1
        RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(StashRadarClient.KEY));
        //#else
        RegisterKeyMappingsEvent.getBus(context.getModBusGroup()).addListener(e -> e.register(StashRadarClient.KEY));
        //#endif
        InputEvent.Key.BUS.addListener(e -> StashRadarClient.onKeyInput());
    }
    //#else
    static void init() {
        //#if MC >= 1.19
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> e.register(StashRadarClient.KEY));
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key e) -> StashRadarClient.onKeyInput());
        //#else
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> ClientRegistry.registerKeyBinding(StashRadarClient.KEY));
        MinecraftForge.EVENT_BUS.addListener((InputEvent.KeyInputEvent e) -> StashRadarClient.onKeyInput());
        //#endif
    }
    //#endif
}
