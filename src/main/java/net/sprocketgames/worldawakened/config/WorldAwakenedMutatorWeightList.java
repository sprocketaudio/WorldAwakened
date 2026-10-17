package net.sprocketgames.worldawakened.config;

import java.util.List;
import java.util.Map;

/** Pure list operations used by the mutation-pool editor's weight controls. */
public final class WorldAwakenedMutatorWeightList {
    private WorldAwakenedMutatorWeightList() {}

    public static List<Entry> ordered(Map<String, Integer> weights) {
        return weights.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .toList();
    }

    public static long totalWeight(Map<String, Integer> weights) {
        return weights.values().stream()
                .filter(weight -> weight != null && weight > 0)
                .mapToLong(Integer::longValue)
                .sum();
    }

    public static double relativeShare(int weight, long totalWeight) {
        return weight > 0 && totalWeight > 0 ? weight * 100.0D / totalWeight : 0.0D;
    }

    public static boolean swapWeights(Map<String, Integer> weights, String firstId, String secondId) {
        Integer first = weights.get(firstId);
        Integer second = weights.get(secondId);
        if (first == null || second == null || first.equals(second)) return false;
        weights.put(firstId, second);
        weights.put(secondId, first);
        return true;
    }

    public record Entry(String id, int weight) {}
}
