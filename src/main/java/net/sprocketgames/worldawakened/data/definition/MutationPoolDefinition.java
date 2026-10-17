package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record MutationPoolDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        MutationSelection selection,
        boolean allowFromSpawner,
        boolean allowFromTrialSpawner,
        List<JsonElement> conditions,
        Optional<JsonElement> stageFilters,
        Optional<JsonElement> apotheosisTierFilters,
        List<ResourceLocation> eligibleDimensions,
        List<ResourceLocation> eligibleBiomes,
        List<ResourceLocation> eligibleEntities,
        List<JsonElement> mutators) implements WorldAwakenedDataDefinition {
    private static final MapCodec<MutationSelection> SELECTION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("weight", 1).forGetter(MutationSelection::weight),
            Codec.DOUBLE.fieldOf("mutation_chance").forGetter(MutationSelection::mutationChance),
            Codec.DOUBLE.fieldOf("additional_mutation_chance").forGetter(MutationSelection::additionalMutationChance),
            Codec.INT.fieldOf("max_mutators_per_entity").forGetter(MutationSelection::maxMutatorsPerEntity),
            CombinationMode.CODEC.optionalFieldOf("combination_mode", CombinationMode.ANY_COMPATIBLE)
                    .forGetter(MutationSelection::combinationMode),
            CuratedCombination.CODEC.listOf().optionalFieldOf("rare_combinations", List.of())
                    .forGetter(MutationSelection::rareCombinations))
            .apply(instance, MutationSelection::new));

    public static final Codec<MutationPoolDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(MutationPoolDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(MutationPoolDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(MutationPoolDefinition::enabled),
            SELECTION_CODEC.forGetter(MutationPoolDefinition::selection),
            Codec.BOOL.optionalFieldOf("allow_from_spawner", false).forGetter(MutationPoolDefinition::allowFromSpawner),
            Codec.BOOL.optionalFieldOf("allow_from_trial_spawner", false).forGetter(MutationPoolDefinition::allowFromTrialSpawner),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("conditions", List.of()).forGetter(MutationPoolDefinition::conditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("stage_filters").forGetter(MutationPoolDefinition::stageFilters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("apotheosis_tier_filters")
                    .forGetter(MutationPoolDefinition::apotheosisTierFilters),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_dimensions", List.of())
                    .forGetter(MutationPoolDefinition::eligibleDimensions),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_biomes", List.of())
                    .forGetter(MutationPoolDefinition::eligibleBiomes),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_entities", List.of())
                    .forGetter(MutationPoolDefinition::eligibleEntities),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().fieldOf("mutators").forGetter(MutationPoolDefinition::mutators))
            .apply(instance, MutationPoolDefinition::new));

    public int weight() { return selection.weight(); }
    public double mutationChance() { return selection.mutationChance(); }
    public double additionalMutationChance() { return selection.additionalMutationChance(); }
    public int maxMutatorsPerEntity() { return selection.maxMutatorsPerEntity(); }
    public CombinationMode combinationMode() { return selection.combinationMode(); }
    public List<CuratedCombination> rareCombinations() { return selection.rareCombinations(); }

    public record MutationSelection(
            int weight,
            double mutationChance,
            double additionalMutationChance,
            int maxMutatorsPerEntity,
            CombinationMode combinationMode,
            List<CuratedCombination> rareCombinations) {
    }

    public record CuratedCombination(
            ResourceLocation id,
            int weight,
            List<ResourceLocation> mutators,
            List<ResourceLocation> eligibleEntities) {
        public static final Codec<CuratedCombination> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(CuratedCombination::id),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(CuratedCombination::weight),
                WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.fieldOf("mutators").forGetter(CuratedCombination::mutators),
                WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_entities", List.of())
                        .forGetter(CuratedCombination::eligibleEntities))
                .apply(instance, CuratedCombination::new));
    }

    public enum CombinationMode {
        ANY_COMPATIBLE("any_compatible"),
        CURATED("curated");

        public static final Codec<CombinationMode> CODEC = Codec.STRING.comapFlatMap(value -> {
            for (CombinationMode mode : values()) {
                if (mode.serializedName.equals(value)) return DataResult.success(mode);
            }
            return DataResult.error(() -> "Unknown mutation combination mode: " + value);
        }, CombinationMode::serializedName);

        private final String serializedName;

        CombinationMode(String serializedName) { this.serializedName = serializedName; }
        public String serializedName() { return serializedName; }
    }
}

