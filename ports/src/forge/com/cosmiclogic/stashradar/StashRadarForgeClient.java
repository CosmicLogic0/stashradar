package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.ConfigScreen;
import com.cosmiclogic.stashradar.client.StashRadarClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.fml.ModLoadingContext;
//#if MC < 1.21.6
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#endif
//#if MC >= 1.19
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#elif MC >= 1.17
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//#else
import net.minecraftforge.fml.ExtensionPoint;
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
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            for (KeyMapping key : StashRadarClient.KEYS) {
                e.register(key);
            }
        });
        //#else
        RegisterKeyMappingsEvent.getBus(context.getModBusGroup()).addListener(e -> {
            for (KeyMapping key : StashRadarClient.KEYS) {
                e.register(key);
            }
        });
        //#endif
        InputEvent.Key.BUS.addListener(e -> StashRadarClient.onKeyInput());
        // Mods list → Stash Radar → Config
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
    }
    //#else
    static void init() {
        //#if MC >= 1.19
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> {
            for (KeyMapping key : StashRadarClient.KEYS) {
                e.register(key);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key e) -> StashRadarClient.onKeyInput());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
        //#elif MC >= 1.17
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> {
            for (KeyMapping key : StashRadarClient.KEYS) {
                ClientRegistry.registerKeyBinding(key);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.KeyInputEvent e) -> StashRadarClient.onKeyInput());
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new ConfigScreen(parent)));
        //#else
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> {
            for (KeyMapping key : StashRadarClient.KEYS) {
                ClientRegistry.registerKeyBinding(key);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.KeyInputEvent e) -> StashRadarClient.onKeyInput());
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (mc, parent) -> new ConfigScreen(parent));
        //#endif
    }
    //#endif
}
