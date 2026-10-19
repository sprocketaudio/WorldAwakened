package net.sprocketgames.worldawakened.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.rarity.RarityDefinition;
import net.sprocketgames.worldawakened.rarity.WorldAwakenedRarityResolver;

/**
 * Deterministic, side-effect-free generated-combination resolver. Callers persist its
 * concrete output rather than re-running it when the owner reloads.
 */
public final class WorldAwakenedEffectGenerationService {
    private WorldAwakenedEffectGenerationService() {
    }

    public static Optional<WorldAwakenedGeneratedResult> generate(
            String instanceId,
            List<WorldAwakenedEffectCandidate> candidates,
            WorldAwakenedGenerationSpec spec,
            Map<ResourceLocation, RarityDefinition> rarities,
            long seed) {
        if (instanceId == null || instanceId.isBlank() || candidates == null || spec == null) return Optional.empty();
        SplittableRandom random = new SplittableRandom(seed);
        int targetSize = weightedPick(spec.sizeWeights(), random);
        if (targetSize < 1) return Optional.empty();
        List<WorldAwakenedEffectCandidate> remaining = candidates.stream()
                .filter(candidate -> candidate.selectionWeight() > 0)
                .sorted(Comparator.comparing(candidate -> candidate.id().toString()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<WorldAwakenedEffectCandidate> selected = new ArrayList<>();
        while (selected.size() < targetSize && !remaining.isEmpty()) {
            List<WorldAwakenedEffectCandidate> compatible = remaining.stream()
                    .filter(candidate -> selected.stream().noneMatch(existing ->
                            candidate.excludes().contains(existing.id()) || existing.excludes().contains(candidate.id())))
                    .toList();
            if (compatible.isEmpty()) break;
            WorldAwakenedEffectCandidate picked = weightedPickCandidate(compatible, random);
            selected.add(picked);
            if (!picked.allowRepeat()) remaining.removeIf(candidate -> candidate.id().equals(picked.id()));
        }
        if (selected.size() != targetSize) return Optional.empty();
        List<WorldAwakenedGeneratedEffect> effects = selected.stream().map(WorldAwakenedEffectCandidate::resolve).toList();
        int totalPower = effects.stream().mapToInt(WorldAwakenedGeneratedEffect::power).sum();
        List<ResourceLocation> provenance = selected.stream().map(WorldAwakenedEffectCandidate::id).toList();
        return Optional.of(new WorldAwakenedGeneratedResult(instanceId, WorldAwakenedGenerationMode.GENERATED,
                effects, totalPower, WorldAwakenedRarityResolver.resolve(totalPower, rarities), provenance));
    }

    private static int weightedPick(Map<Integer, Integer> weights, SplittableRandom random) {
        long total = weights.entrySet().stream().filter(entry -> entry.getKey() > 0 && entry.getValue() > 0)
                .mapToLong(Map.Entry::getValue).sum();
        if (total <= 0) return 0;
        long roll = random.nextLong(total);
        long cursor = 0;
        for (Map.Entry<Integer, Integer> entry : weights.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (entry.getKey() <= 0 || entry.getValue() <= 0) continue;
            cursor += entry.getValue();
            if (roll < cursor) return entry.getKey();
        }
        return 0;
    }

    private static WorldAwakenedEffectCandidate weightedPickCandidate(
            List<WorldAwakenedEffectCandidate> candidates, SplittableRandom random) {
        long total = candidates.stream().mapToLong(WorldAwakenedEffectCandidate::selectionWeight).sum();
        long roll = random.nextLong(total);
        long cursor = 0;
        for (WorldAwakenedEffectCandidate candidate : candidates) {
            cursor += candidate.selectionWeight();
            if (roll < cursor) return candidate;
        }
        return candidates.getLast();
    }
}
