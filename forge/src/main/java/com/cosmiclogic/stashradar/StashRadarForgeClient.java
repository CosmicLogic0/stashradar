package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.ConfigScreen;
import com.cosmiclogic.stashradar.client.StashRadarClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.fml.ModLoadingContext;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;










/** Client-only half. Only loaded on the client (see StashRadarForge). */
final class StashRadarForgeClient {

    private StashRadarForgeClient() {
    }






















    static void init() {

        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> {
            for (KeyMapping key : StashRadarClient.KEYS) {
                e.register(key);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key e) -> StashRadarClient.onKeyInput());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));


















    }

}
