package net.sprocketgames.worldawakened.progression;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedStageMutationResult(
        WorldAwakenedStageMutationStatus status,
        ResourceLocation requestedStageId,
        Optional<ResourceLocation> resolvedStageId,
        Optional<ResourceLocation> replacedStageId,
        String message) {
    public WorldAwakenedStageMutationResult {
        resolvedStageId = resolvedStageId == null ? Optional.empty() : resolvedStageId;
        replacedStageId = replacedStageId == null ? Optional.empty() : replacedStageId;
        message = message == null ? "" : message;
    }

    public static WorldAwakenedStageMutationResult unlocked(
            ResourceLocation requestedStageId,
            ResourceLocation resolvedStageId,
            Optional<ResourceLocation> replacedStageId,
            String message) {
        return new WorldAwakenedStageMutationResult(
                WorldAwakenedStageMutationStatus.UNLOCKED,
                requestedStageId,
                Optional.of(resolvedStageId),
                replacedStageId,
                message);
    }

    public static WorldAwakenedStageMutationResult alreadyUnlocked(
            ResourceLocation requestedStageId,
            ResourceLocation resolvedStageId,
            String message) {
        return new WorldAwakenedStageMutationResult(
                WorldAwakenedStageMutationStatus.ALREADY_UNLOCKED,
                requestedStageId,
                Optional.of(resolvedStageId),
                Optional.empty(),
                message);
    }

    public static WorldAwakenedStageMutationResult locked(
            ResourceLocation requestedStageId,
            ResourceLocation resolvedStageId,
            String message) {
        return new WorldAwakenedStageMutationResult(
                WorldAwakenedStageMutationStatus.LOCKED,
                requestedStageId,
                Optional.of(resolvedStageId),
                Optional.empty(),
                message);
    }

    public static WorldAwakenedStageMutationResult alreadyLocked(
            ResourceLocation requestedStageId,
            ResourceLocation resolvedStageId,
            String message) {
        return new WorldAwakenedStageMutationResult(
                WorldAwakenedStageMutationStatus.ALREADY_LOCKED,
                requestedStageId,
                Optional.of(resolvedStageId),
                Optional.empty(),
                message);
    }

    public static WorldAwakenedStageMutationResult blocked(
            ResourceLocation requestedStageId,
            Optional<ResourceLocation> resolvedStageId,
            String message) {
        return new WorldAwakenedStageMutationResult(
                WorldAwakenedStageMutationStatus.BLOCKED,
                requestedStageId,
                resolvedStageId,
                Optional.empty(),
                message);
    }

    public static WorldAwakenedStageMutationResult invalid(
            ResourceLocation requestedStageId,
            String message) {
        return new WorldAwakenedStageMutationResult(
                WorldAwakenedStageMutationStatus.INVALID,
                requestedStageId,
                Optional.empty(),
                Optional.empty(),
                message);
    }
}

