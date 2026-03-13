package net.sprocketgames.worldawakened.carrier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

public final class WorldAwakenedOwnedClientVisualCarrierService {
    private WorldAwakenedOwnedClientVisualCarrierService() {
    }

    public static void applyNightVisionFogColor(ViewportEvent.ComputeFogColor event) {
        float scale = resolveNightVisionFogScale(Minecraft.getInstance().player, (float) event.getPartialTick());
        if (scale <= 0.0F) {
            return;
        }
        Vector3f adjusted = vanillaNightVisionLift(event.getRed(), event.getGreen(), event.getBlue(), scale);
        event.setRed(adjusted.x());
        event.setGreen(adjusted.y());
        event.setBlue(adjusted.z());
    }

    public static void applyNightVisionFogDistance(ViewportEvent.RenderFog event) {
        // Vanilla night vision does not own custom fog-distance changes. Keep this carrier visual-only.
    }

    static boolean isNightVisionVisualActive(LocalPlayer player) {
        return player != null
                && WorldAwakenedOwnedCarrierService.hasCarrier(player, WorldAwakenedOwnedCarrierIds.NIGHT_VISION_PASSIVE)
                && !player.hasEffect(MobEffects.NIGHT_VISION);
    }

    public static boolean shouldUseNightVisionLightmap(LocalPlayer player) {
        return player != null && (player.hasEffect(MobEffects.NIGHT_VISION)
                || WorldAwakenedOwnedCarrierService.hasCarrier(player, WorldAwakenedOwnedCarrierIds.NIGHT_VISION_PASSIVE));
    }

    public static float resolveNightVisionLightmapScale(LocalPlayer player, float partialTick) {
        if (player == null) {
            return 0.0F;
        }
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            return GameRenderer.getNightVisionScale(player, partialTick);
        }
        if (!WorldAwakenedOwnedCarrierService.hasCarrier(player, WorldAwakenedOwnedCarrierIds.NIGHT_VISION_PASSIVE)) {
            return 0.0F;
        }
        return resolveNightVisionScale(false, 0.0F, true);
    }

    static float resolveNightVisionFogScale(LocalPlayer player, float partialTick) {
        if (player == null || player.hasEffect(MobEffects.NIGHT_VISION)) {
            return 0.0F;
        }
        return resolveNightVisionScale(false, 0.0F,
                WorldAwakenedOwnedCarrierService.hasCarrier(player, WorldAwakenedOwnedCarrierIds.NIGHT_VISION_PASSIVE));
    }

    static float resolveNightVisionScale(boolean hasVanillaEffect, float vanillaScale, boolean hasOwnedCarrier) {
        if (hasVanillaEffect) {
            return vanillaScale;
        }
        // Permanent WA-owned passive night vision should track vanilla's steady-state scale.
        return hasOwnedCarrier ? 1.0F : 0.0F;
    }

    static Vector3f vanillaNightVisionLift(float red, float green, float blue, float scale) {
        Vector3f color = new Vector3f(red, green, blue);
        if (scale <= 0.0F) {
            return color;
        }
        float brightest = Math.max(color.x(), Math.max(color.y(), color.z()));
        if (brightest <= 0.0F || brightest >= 1.0F) {
            return color;
        }
        float normalize = 1.0F / brightest;
        Vector3f lifted = new Vector3f(color).mul(normalize);
        color.lerp(lifted, scale);
        return color;
    }
}
