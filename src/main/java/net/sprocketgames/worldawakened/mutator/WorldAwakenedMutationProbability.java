package net.sprocketgames.worldawakened.mutator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/** Shared probability math for runtime rolls and read-only editor previews. */
public final class WorldAwakenedMutationProbability {
    private WorldAwakenedMutationProbability() {}

    public static int mutationCountAfterInitialSuccess(
            double additionalChance,
            int maximumMutations,
            IntToDoubleFunction rollForAdditionalIndex) {
        int maximum = Math.max(1, maximumMutations);
        double chance = clampChance(additionalChance);
        int count = 1;
        while (count < maximum && rollForAdditionalIndex.applyAsDouble(count - 1) < chance) count++;
        return count;
    }

    public static List<Double> theoreticalDistribution(
            double initialChance,
            double additionalChance,
            int maximumMutations) {
        int maximum = Math.max(1, maximumMutations);
        double initial = clampChance(initialChance);
        double additional = clampChance(additionalChance);
        List<Double> probabilities = new ArrayList<>(maximum + 1);
        probabilities.add(1.0D - initial);
        double reached = initial;
        for (int count = 1; count < maximum; count++) {
            probabilities.add(reached * (1.0D - additional));
            reached *= additional;
        }
        probabilities.add(reached);
        return List.copyOf(probabilities);
    }

    private static double clampChance(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }
}
