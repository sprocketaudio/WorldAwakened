package net.sprocketgames.worldawakened.progression;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;

public final class WorldAwakenedStageStateEngine {
    private static final Comparator<StageDefinition> GROUP_HIGHEST_ORDER = Comparator
            .comparingInt(StageDefinition::sortIndex)
            .thenComparing(stage -> stage.id().toString());

    private WorldAwakenedStageStateEngine() {
    }

    public static WorldAwakenedStageMutationResult unlockStage(
            WorldAwakenedStageRegistry registry,
            WorldAwakenedMutableStageState stageState,
            ResourceLocation requestedStageId,
            String source,
            long unlockTimestampMillis) {
        Optional<ResourceLocation> resolvedOpt = registry.resolveCanonicalId(requestedStageId);
        if (resolvedOpt.isEmpty()) {
            return WorldAwakenedStageMutationResult.invalid(requestedStageId, "Unknown stage id or alias: " + requestedStageId);
        }

        ResourceLocation resolvedStageId = resolvedOpt.get();
        if (stageState.unlockedStages().contains(resolvedStageId)) {
            return WorldAwakenedStageMutationResult.alreadyUnlocked(
                    requestedStageId,
                    resolvedStageId,
                    "Stage already unlocked: " + resolvedStageId);
        }

        StageDefinition definition = registry.definition(resolvedStageId).orElse(null);
        if (definition == null) {
            return WorldAwakenedStageMutationResult.invalid(requestedStageId, "Resolved stage is missing from registry: " + resolvedStageId);
        }

        Optional<ResourceLocation> replacedStageId = Optional.empty();
        if (definition.progressionGroup().isPresent() && definition.unlockPolicy() != StageUnlockPolicy.CUMULATIVE) {
            String groupId = definition.progressionGroup().get();
            ResourceLocation activeStage = stageState.activeGroupStages().get(groupId);
            if (activeStage != null && !activeStage.equals(resolvedStageId) && stageState.unlockedStages().contains(activeStage)) {
                if (definition.unlockPolicy() == StageUnlockPolicy.EXCLUSIVE_GROUP) {
                    return WorldAwakenedStageMutationResult.blocked(
                            requestedStageId,
                            Optional.of(resolvedStageId),
                            "Group '" + groupId + "' already has active stage " + activeStage + " and policy is exclusive_group");
                }
                replacedStageId = Optional.of(activeStage);
                clearStage(stageState, activeStage);
            }
            stageState.activeGroupStages().put(groupId, resolvedStageId);
        }

        stageState.unlockedStages().add(resolvedStageId);
        stageState.unlockTimestamps().put(resolvedStageId, unlockTimestampMillis);
        stageState.unlockSources().put(resolvedStageId, source);
        stageState.markDirty();

        return WorldAwakenedStageMutationResult.unlocked(
                requestedStageId,
                resolvedStageId,
                replacedStageId,
                "Unlocked stage " + resolvedStageId);
    }

    public static WorldAwakenedStageMutationResult lockStage(
            WorldAwakenedStageRegistry registry,
            WorldAwakenedMutableStageState stageState,
            ResourceLocation requestedStageId,
            boolean allowRegression) {
        Optional<ResourceLocation> resolvedOpt = registry.resolveCanonicalId(requestedStageId);
        ResourceLocation targetStageId = resolvedOpt.orElse(requestedStageId);
        boolean isKnownInactive = resolvedOpt.isEmpty() && stageState.unlockedStages().contains(requestedStageId);

        if (!stageState.unlockedStages().contains(targetStageId)) {
            if (isKnownInactive) {
                targetStageId = requestedStageId;
            } else if (resolvedOpt.isPresent()) {
                return WorldAwakenedStageMutationResult.alreadyLocked(
                        requestedStageId,
                        resolvedOpt.get(),
                        "Stage already locked: " + resolvedOpt.get());
            } else {
                return WorldAwakenedStageMutationResult.invalid(requestedStageId, "Unknown stage id or alias: " + requestedStageId);
            }
        }

        if (!allowRegression) {
            return WorldAwakenedStageMutationResult.blocked(
                    requestedStageId,
                    Optional.of(targetStageId),
                    "Stage regression is disabled by config");
        }

        clearStage(stageState, targetStageId);
        stageState.markDirty();
        return WorldAwakenedStageMutationResult.locked(
                requestedStageId,
                targetStageId,
                "Locked stage " + targetStageId);
    }

    public static boolean isStageUnlocked(
            WorldAwakenedStageRegistry registry,
            WorldAwakenedMutableStageState stageState,
            ResourceLocation requestedStageId) {
        Optional<ResourceLocation> resolvedOpt = registry.resolveCanonicalId(requestedStageId);
        if (resolvedOpt.isPresent()) {
            return stageState.unlockedStages().contains(resolvedOpt.get());
        }
        return stageState.unlockedStages().contains(requestedStageId);
    }

    public static Optional<ResourceLocation> getHighestUnlockedStageInGroup(
            WorldAwakenedStageRegistry registry,
            WorldAwakenedMutableStageState stageState,
            String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return Optional.empty();
        }

        ResourceLocation activeStage = stageState.activeGroupStages().get(groupId);
        if (activeStage != null && stageState.unlockedStages().contains(activeStage)) {
            return Optional.of(activeStage);
        }

        return registry.stagesInGroup(groupId).stream()
                .filter(stage -> stageState.unlockedStages().contains(stage.id()))
                .max(GROUP_HIGHEST_ORDER)
                .map(StageDefinition::id);
    }

    private static void clearStage(WorldAwakenedMutableStageState stageState, ResourceLocation stageId) {
        stageState.unlockedStages().remove(stageId);
        stageState.unlockTimestamps().remove(stageId);
        stageState.unlockSources().remove(stageId);
        stageState.activeGroupStages().entrySet().removeIf(entry -> stageId.equals(entry.getValue()));
    }
}

