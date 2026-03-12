package net.sprocketgames.worldawakened.rules.runtime;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedEvaluationSnapshot(
        UUID passToken,
        WorldAwakenedRuntimeLayer layer,
        String eventType,
        ResourceLocation dimensionId,
        Set<ResourceLocation> activeStages,
        Map<String, Double> activeScalars,
        Map<String, String> integrationStates) {
    public static WorldAwakenedEvaluationSnapshot create(
            WorldAwakenedRuntimeLayer layer,
            String eventType,
            ResourceLocation dimensionId,
            Set<ResourceLocation> activeStages,
            Map<String, Double> activeScalars,
            Map<String, String> integrationStates) {
        return new WorldAwakenedEvaluationSnapshot(
                UUID.randomUUID(),
                layer,
                eventType,
                dimensionId,
                Set.copyOf(activeStages),
                Map.copyOf(activeScalars),
                Map.copyOf(integrationStates));
    }
}

