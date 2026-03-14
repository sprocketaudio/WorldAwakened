package net.sprocketgames.worldawakened;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.sprocketgames.worldawakened.client.render.WorldAwakenedClientGlowStyleEventHandlers;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedClientVisualCarrierEventHandlers;

@Mod(value = WorldAwakenedConstants.MOD_ID, dist = Dist.CLIENT)
public final class WorldAwakenedClient {
    public WorldAwakenedClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        WorldAwakenedOwnedClientVisualCarrierEventHandlers visualCarrierHandlers = new WorldAwakenedOwnedClientVisualCarrierEventHandlers();
        WorldAwakenedClientGlowStyleEventHandlers glowStyleHandlers = new WorldAwakenedClientGlowStyleEventHandlers();
        NeoForge.EVENT_BUS.addListener(visualCarrierHandlers::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(visualCarrierHandlers::onRenderFog);
        NeoForge.EVENT_BUS.addListener(glowStyleHandlers::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(glowStyleHandlers::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(glowStyleHandlers::onClientTickPost);
    }
}

