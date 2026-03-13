package net.sprocketgames.worldawakened.carrier;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class WorldAwakenedOwnedClientVisualCarrierEventHandlers {
    public void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!cameraOwnedByLocalPlayer(event)) {
            return;
        }
        WorldAwakenedOwnedClientVisualCarrierService.applyNightVisionFogColor(event);
    }

    public void onRenderFog(ViewportEvent.RenderFog event) {
        if (!cameraOwnedByLocalPlayer(event)) {
            return;
        }
        WorldAwakenedOwnedClientVisualCarrierService.applyNightVisionFogDistance(event);
    }

    private static boolean cameraOwnedByLocalPlayer(ViewportEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && event.getCamera().getEntity() == minecraft.player;
    }
}
