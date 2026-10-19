package net.sprocketgames.worldawakened.rarity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Pure shared resolver used by every system which produces a powered result. */
public final class WorldAwakenedRarityResolver {
    private WorldAwakenedRarityResolver() {
    }

    public static Optional<ResourceLocation> resolve(int totalPower, Map<ResourceLocation, RarityDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) return Optional.empty();
        return definitions.values().stream()
                .filter(RarityDefinition::enabled)
                .filter(definition -> definition.minimumPower() <= totalPower)
                .max(Comparator.comparingInt(RarityDefinition::minimumPower)
                        .thenComparing(definition -> definition.id().toString()))
                .map(RarityDefinition::id);
    }

    public static List<RarityDefinition> ordered(Map<ResourceLocation, RarityDefinition> definitions) {
        if (definitions == null) return List.of();
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(RarityDefinition::minimumPower)
                        .thenComparing(definition -> definition.id().toString()))
                .toList();
    }
}
