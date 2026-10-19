package net.sprocketgames.worldawakened.progression;

import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedEffectiveStageContext(
        WorldAwakenedProgressionMode configuredMode,
        WorldAwakenedProgressionMode effectiveMode,
        boolean usedWorldFallback,
        Set<ResourceLocation> unlockedStages,
        Set<ResourceLocation> activeStages,
        Set<ResourceLocation> inactiveUnlockedStages,
        Map<String, ResourceLocation> activeGroupStages) {
    public WorldAwakenedEffectiveStageContext {
        unlockedStages = Set.copyOf(unlockedStages);
        activeStages = Set.copyOf(activeStages);
        inactiveUnlockedStages = Set.copyOf(inactiveUnlockedStages);
        activeGroupStages = Map.copyOf(activeGroupStages);
    }
}

