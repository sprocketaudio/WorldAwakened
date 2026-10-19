package net.sprocketgames.worldawakened.generation;

import java.util.List;
import java.util.Map;

/** Explicit generation controls; source weights are deliberately separate from effect weights. */
public record WorldAwakenedGenerationSpec(
        WorldAwakenedGenerationMode mode,
        int curatedSourceWeight,
        int generatedSourceWeight,
        Map<Integer, Integer> sizeWeights) {
    public WorldAwakenedGenerationSpec {
        sizeWeights = Map.copyOf(sizeWeights);
    }
    public static WorldAwakenedGenerationSpec generated(Map<Integer, Integer> sizeWeights) {
        return new WorldAwakenedGenerationSpec(WorldAwakenedGenerationMode.GENERATED, 0, 1, sizeWeights);
    }
}
