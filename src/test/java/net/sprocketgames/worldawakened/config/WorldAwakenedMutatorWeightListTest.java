package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorldAwakenedMutatorWeightListTest {
    @Test
    void ordersByDescendingWeightAndKeepsEqualWeightsStable() {
        LinkedHashMap<String, Integer> weights = weights("hunter", 2, "bulwark", 6, "restless", 1, "vampiric", 2);

        assertEquals(List.of("bulwark", "hunter", "vampiric", "restless"),
                WorldAwakenedMutatorWeightList.ordered(weights).stream()
                        .map(WorldAwakenedMutatorWeightList.Entry::id)
                        .toList());
    }

    @Test
    void calculatesRelativeSelectionSharesUsingAWideTotal() {
        LinkedHashMap<String, Integer> weights = weights("bulwark", 6, "hunter", 2, "restless", 1);
        long total = WorldAwakenedMutatorWeightList.totalWeight(weights);

        assertEquals(9L, total);
        assertEquals(66.7D, WorldAwakenedMutatorWeightList.relativeShare(6, total), 0.05D);
        assertEquals(22.2D, WorldAwakenedMutatorWeightList.relativeShare(2, total), 0.05D);
        assertEquals(11.1D, WorldAwakenedMutatorWeightList.relativeShare(1, total), 0.05D);
    }

    @Test
    void swappingAdjacentEntriesChangesOnlyTheirExistingWeights() {
        LinkedHashMap<String, Integer> weights = weights("bulwark", 6, "hunter", 2, "restless", 1);

        assertTrue(WorldAwakenedMutatorWeightList.swapWeights(weights, "bulwark", "hunter"));
        assertEquals(weights("bulwark", 2, "hunter", 6, "restless", 1), weights);
        assertEquals(List.of("hunter", "bulwark", "restless"),
                WorldAwakenedMutatorWeightList.ordered(weights).stream()
                        .map(WorldAwakenedMutatorWeightList.Entry::id)
                        .toList());
    }

    @Test
    void equalWeightSwapIsANoOpWithoutInventingSeparatePriority() {
        LinkedHashMap<String, Integer> weights = weights("hunter", 2, "vampiric", 2);

        assertFalse(WorldAwakenedMutatorWeightList.swapWeights(weights, "hunter", "vampiric"));
        assertEquals(List.of("hunter", "vampiric"),
                WorldAwakenedMutatorWeightList.ordered(weights).stream()
                        .map(WorldAwakenedMutatorWeightList.Entry::id)
                        .toList());
    }

    private static LinkedHashMap<String, Integer> weights(Object... entries) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], (Integer) entries[index + 1]);
        }
        return result;
    }
}
