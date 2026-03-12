package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record MutationPoolDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        int weight,
        List<JsonElement> conditions,
        Optional<JsonElement> stageFilters,
        Optional<JsonElement> apotheosisTierFilters,
        List<ResourceLocation> eligibleDimensions,
        List<ResourceLocation> eligibleBiomes,
        List<ResourceLocation> eligibleEntities,
        List<JsonElement> mutators,
        Optional<Integer> maxMutatorsPerEntity,
        Optional<String> rerollPolicy) implements WorldAwakenedDataDefinition {
    public static final Codec<MutationPoolDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(MutationPoolDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(MutationPoolDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(MutationPoolDefinition::enabled),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(MutationPoolDefinition::weight),
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
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().fieldOf("mutators").forGetter(MutationPoolDefinition::mutators),
            Codec.INT.optionalFieldOf("max_mutators_per_entity").forGetter(MutationPoolDefinition::maxMutatorsPerEntity),
            Codec.STRING.optionalFieldOf("reroll_policy").forGetter(MutationPoolDefinition::rerollPolicy))
            .apply(instance, MutationPoolDefinition::new));
}

