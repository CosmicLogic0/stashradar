package com.cosmiclogic.stashradar;

import com.cosmiclogic.stashradar.client.ConfigScreen;
import com.cosmiclogic.stashradar.client.StashRadarClient;
import com.cosmiclogic.stashradar.command.StashRadarCommands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(StashRadar.MOD_ID)
public class StashRadarNeoForge {

    public StashRadarNeoForge(IEventBus modBus, Dist dist, ModContainer container) {
        StashRadar.init();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        if (dist.isClient()) {
            Client.init(modBus, container);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StashRadarCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    /** Client-only hooks, kept in their own class so a dedicated server never loads client code. */
    static final class Client {
        static void init(IEventBus modBus, ModContainer container) {
            modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> {
                for (net.minecraft.client.KeyMapping key : StashRadarClient.KEYS) {
                    e.register(key);
                }
            });
            // Mods list → Stash Radar → Config
            container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (mod, parent) -> new ConfigScreen(parent));
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.Key e) -> StashRadarClient.onKeyInput());
        }
    }
}
