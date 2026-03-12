package net.sprocketgames.worldawakened.progression;

import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedEffectiveStageContext(
        WorldAwakenedProgressionMode configuredMode,
        WorldAwakenedProgressionMode effectiveMode,
        boolean usedWorldFallback,
        Set<ResourceLocation> unlockedStages,
        Set<ResourceLocation> inactiveUnlockedStages,
        Map<String, ResourceLocation> activeGroupStages) {
    public WorldAwakenedEffectiveStageContext {
        unlockedStages = Set.copyOf(unlockedStages);
        inactiveUnlockedStages = Set.copyOf(inactiveUnlockedStages);
        activeGroupStages = Map.copyOf(activeGroupStages);
    }
}

