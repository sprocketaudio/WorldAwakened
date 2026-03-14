package net.sprocketgames.worldawakened.network.client;

import net.minecraft.client.Minecraft;
import net.sprocketgames.worldawakened.client.render.WorldAwakenedClientGlowStyleService;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedGlowStyleState;

public final class WorldAwakenedClientGlowStylePayloadHandler {
    private WorldAwakenedClientGlowStylePayloadHandler() {
    }

    public static void handleGlowStyleState(
            int entityId,
            boolean active,
            int colorRgb,
            float brightness,
            boolean seeThroughWalls,
            boolean pulse,
            float pulseSpeed,
            float pulseStrength) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (!active) {
            WorldAwakenedClientGlowStyleService.removeGlowStyle(entityId);
            return;
        }
        WorldAwakenedClientGlowStyleService.upsertGlowStyle(
                entityId,
                new WorldAwakenedGlowStyleState.GlowStyleDefinition(
                        colorRgb,
                        brightness,
                        seeThroughWalls,
                        pulse,
                        pulseSpeed,
                        pulseStrength));
    }
}
