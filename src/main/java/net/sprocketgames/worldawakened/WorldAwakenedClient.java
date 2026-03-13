package net.sprocketgames.worldawakened;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedClientVisualCarrierEventHandlers;

@Mod(value = WorldAwakenedConstants.MOD_ID, dist = Dist.CLIENT)
public final class WorldAwakenedClient {
    public WorldAwakenedClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        WorldAwakenedOwnedClientVisualCarrierEventHandlers visualCarrierHandlers = new WorldAwakenedOwnedClientVisualCarrierEventHandlers();
        NeoForge.EVENT_BUS.addListener(visualCarrierHandlers::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(visualCarrierHandlers::onRenderFog);
    }
}

