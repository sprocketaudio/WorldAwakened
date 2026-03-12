package net.sprocketgames.worldawakened.rules.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedRuleMatchContext(
        String eventType,
        Optional<ResourceLocation> targetedRuleId,
        ResourceLocation dimensionId,
        Optional<ResourceLocation> biomeId,
        Optional<ResourceLocation> entityTypeId,
        Set<ResourceLocation> entityTags,
        Optional<String> playerUuid,
        Optional<String> entityUuid,
        boolean hasPlayerContext,
        boolean hasEntityContext,
        boolean entityIsBoss,
        boolean entityIsMutated,
        OptionalLong worldDay,
        OptionalDouble playerDistanceFromSpawn,
        int playerCountOnline,
        OptionalDouble localDifficulty,
        long nowMillis,
        long tick,
        Set<ResourceLocation> worldStageSnapshot,
        Set<ResourceLocation> playerStageSnapshot,
        WorldAwakenedRuleStateSnapshot worldRuleStateSnapshot,
        WorldAwakenedRuleStateSnapshot playerRuleStateSnapshot,
        Set<ResourceLocation> ownedAscensionRewards,
        Set<ResourceLocation> pendingAscensionOffers,
        Set<String> loadedMods,
        Map<String, Boolean> configToggles,
        boolean invasionActive,
        Optional<String> structureContext) {
}
