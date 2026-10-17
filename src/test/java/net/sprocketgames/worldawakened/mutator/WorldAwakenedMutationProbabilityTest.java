package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class WorldAwakenedMutationProbabilityTest {
    @Test
    void elevenPercentInitialAndTwoPercentAdditionalMatchesDocumentedDistribution() {
        List<Double> distribution = WorldAwakenedMutationProbability.theoreticalDistribution(0.11D, 0.02D, 2);

        assertEquals(0.89D, distribution.get(0), 0.0000001D);
        assertEquals(0.1078D, distribution.get(1), 0.0000001D);
        assertEquals(0.0022D, distribution.get(2), 0.0000001D);
    }

    @Test
    void repeatedAdditionalRollsRespectMaximumsOneThroughFour() {
        assertEquals(1, WorldAwakenedMutationProbability.mutationCountAfterInitialSuccess(1.0D, 1, ignored -> 0.0D));
        assertEquals(2, WorldAwakenedMutationProbability.mutationCountAfterInitialSuccess(1.0D, 2, ignored -> 0.0D));
        assertEquals(3, WorldAwakenedMutationProbability.mutationCountAfterInitialSuccess(1.0D, 3, ignored -> 0.0D));
        assertEquals(4, WorldAwakenedMutationProbability.mutationCountAfterInitialSuccess(1.0D, 4, ignored -> 0.0D));
        assertEquals(2, WorldAwakenedMutationProbability.mutationCountAfterInitialSuccess(
                0.20D, 4, index -> index == 0 ? 0.10D : 0.30D));
    }

    @Test
    void seededSamplingApproximatesTheTheoreticalMutationCountDistribution() {
        int samples = 250_000;
        int[] counts = new int[3];
        SplittableRandom random = new SplittableRandom(0x5A17C0DEL);
        for (int sample = 0; sample < samples; sample++) {
            if (random.nextDouble() >= 0.11D) {
                counts[0]++;
                continue;
            }
            int count = WorldAwakenedMutationProbability.mutationCountAfterInitialSuccess(
                    0.02D, 2, ignored -> random.nextDouble());
            counts[count]++;
        }

        assertEquals(0.89D, counts[0] / (double) samples, 0.002D);
        assertEquals(0.1078D, counts[1] / (double) samples, 0.002D);
        assertEquals(0.0022D, counts[2] / (double) samples, 0.0005D);
    }

    @Test
    void sharedWeightedSelectionHonoursCuratedCombinationWeights() {
        List<WorldAwakenedMutatorService.WeightedCandidate<String>> combinations = List.of(
                new WorldAwakenedMutatorService.WeightedCandidate<>("common_combo", 3),
                new WorldAwakenedMutatorService.WeightedCandidate<>("rare_combo", 1));
        int samples = 100_000;
        int common = 0;
        for (long seed = 0; seed < samples; seed++) {
            if ("common_combo".equals(WorldAwakenedMutatorService.weightedPick(combinations, seed).orElseThrow())) {
                common++;
            }
        }

        assertEquals(0.75D, common / (double) samples, 0.01D);
        assertEquals(WorldAwakenedMutatorService.weightedPick(combinations, 42L),
                WorldAwakenedMutatorService.weightedPick(combinations, 42L));
    }
}
