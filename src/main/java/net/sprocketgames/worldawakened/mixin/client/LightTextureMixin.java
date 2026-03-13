package net.sprocketgames.worldawakened.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedClientVisualCarrierService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
abstract class LightTextureMixin {
    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z",
                    ordinal = 0))
    private boolean worldAwakened$allowOwnedNightVisionLightmap(LocalPlayer player, Holder<MobEffect> effect) {
        if (MobEffects.NIGHT_VISION.equals(effect)) {
            return WorldAwakenedOwnedClientVisualCarrierService.shouldUseNightVisionLightmap(player);
        }
        return player.hasEffect(effect);
    }

    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
    private float worldAwakened$resolveOwnedNightVisionScale(LivingEntity entity, float partialTick) {
        if (entity instanceof LocalPlayer player) {
            return WorldAwakenedOwnedClientVisualCarrierService.resolveNightVisionLightmapScale(player, partialTick);
        }
        return GameRenderer.getNightVisionScale(entity, partialTick);
    }
}
