package net.sprocketgames.worldawakened.data.load;

import java.time.Instant;

import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;

public record WorldAwakenedDatapackSnapshot(
        Instant loadedAt,
        WorldAwakenedCompiledData data,
        WorldAwakenedValidationSummary validationSummary) {
    public static WorldAwakenedDatapackSnapshot empty() {
        return new WorldAwakenedDatapackSnapshot(Instant.EPOCH, WorldAwakenedCompiledData.empty(), WorldAwakenedValidationSummary.empty());
    }
}

