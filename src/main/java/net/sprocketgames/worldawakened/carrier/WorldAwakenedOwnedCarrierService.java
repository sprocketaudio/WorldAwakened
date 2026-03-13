package net.sprocketgames.worldawakened.carrier;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class WorldAwakenedOwnedCarrierService {
    private WorldAwakenedOwnedCarrierService() {
    }

    public static boolean clearOwnedCarriers(LivingEntity entity) {
        return entity.getData(WorldAwakenedOwnedCarrierAttachments.ACTIVE_CARRIERS).clear();
    }

    public static boolean applyOwnedCarrier(LivingEntity entity, ResourceLocation stableKey, ResourceLocation carrierId) {
        return entity.getData(WorldAwakenedOwnedCarrierAttachments.ACTIVE_CARRIERS).put(stableKey, carrierId);
    }

    public static boolean hasCarrier(LivingEntity entity, ResourceLocation carrierId) {
        WorldAwakenedOwnedCarrierState state = entity.getExistingDataOrNull(WorldAwakenedOwnedCarrierAttachments.ACTIVE_CARRIERS);
        return state != null && state.hasCarrier(carrierId);
    }

    public static Map<ResourceLocation, ResourceLocation> snapshot(LivingEntity entity) {
        WorldAwakenedOwnedCarrierState state = entity.getExistingDataOrNull(WorldAwakenedOwnedCarrierAttachments.ACTIVE_CARRIERS);
        return state == null ? Map.of() : state.snapshot();
    }

    public static void syncOwnedCarriers(LivingEntity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(WorldAwakenedOwnedCarrierAttachments.ACTIVE_CARRIERS);
        }
    }
}
