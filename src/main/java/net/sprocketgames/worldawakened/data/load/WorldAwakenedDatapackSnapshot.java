package net.sprocketgames.worldawakened.data.load;

import java.time.Instant;

import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;

public record WorldAwakenedDatapackSnapshot(
        long generation,
        Instant loadedAt,
        WorldAwakenedCompiledData data,
        WorldAwakenedValidationSummary validationSummary) {
    public static WorldAwakenedDatapackSnapshot empty() {
        return new WorldAwakenedDatapackSnapshot(0L, Instant.EPOCH, WorldAwakenedCompiledData.empty(), WorldAwakenedValidationSummary.empty());
    }

    public WorldAwakenedDatapackSnapshot withGeneration(long newGeneration) {
        return new WorldAwakenedDatapackSnapshot(newGeneration, loadedAt, data, validationSummary);
    }
}

