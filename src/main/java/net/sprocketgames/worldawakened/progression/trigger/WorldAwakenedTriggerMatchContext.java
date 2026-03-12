package net.sprocketgames.worldawakened.progression.trigger;

import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedTriggerMatchContext(
        ResourceLocation triggerType,
        Optional<ResourceLocation> targetedRuleId,
        boolean hasPlayerContext,
        ResourceLocation dimensionId,
        Optional<ResourceLocation> advancementId,
        Optional<ResourceLocation> entityId,
        Set<ResourceLocation> entityTags,
        boolean bossFlagMapMatch,
        Optional<ResourceLocation> manualTriggerId,
        long nowMillis,
        Set<ResourceLocation> worldStageSnapshot,
        Set<ResourceLocation> playerStageSnapshot,
        WorldAwakenedTriggerStateSnapshot worldTriggerStateSnapshot,
        WorldAwakenedTriggerStateSnapshot playerTriggerStateSnapshot) {
}
