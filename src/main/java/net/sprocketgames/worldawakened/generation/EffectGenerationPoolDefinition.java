package net.sprocketgames.worldawakened.generation;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;
import net.sprocketgames.worldawakened.data.definition.WorldAwakenedDataDefinition;

/** A weighted source and combination-size policy for configured reusable effects. */
public record EffectGenerationPoolDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        WorldAwakenedEffectScope scope,
        WorldAwakenedGenerationMode mode,
        List<ResourceLocation> configuredEffects,
        int curatedSourceWeight,
        int generatedSourceWeight,
        List<SizeWeight> combinationSizes,
        Optional<JsonElement> stageFilters) implements WorldAwakenedDataDefinition {
    public static final Codec<EffectGenerationPoolDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(EffectGenerationPoolDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(EffectGenerationPoolDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(EffectGenerationPoolDefinition::enabled),
            WorldAwakenedEffectScope.CODEC.fieldOf("scope").forGetter(EffectGenerationPoolDefinition::scope),
            WorldAwakenedGenerationMode.CODEC.optionalFieldOf("mode", WorldAwakenedGenerationMode.GENERATED)
                    .forGetter(EffectGenerationPoolDefinition::mode),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("configured_effects", List.of())
                    .forGetter(EffectGenerationPoolDefinition::configuredEffects),
            Codec.INT.optionalFieldOf("curated_source_weight", 1).forGetter(EffectGenerationPoolDefinition::curatedSourceWeight),
            Codec.INT.optionalFieldOf("generated_source_weight", 1).forGetter(EffectGenerationPoolDefinition::generatedSourceWeight),
            SizeWeight.CODEC.listOf().optionalFieldOf("combination_sizes", List.of(new SizeWeight(1, 1)))
                    .forGetter(EffectGenerationPoolDefinition::combinationSizes),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("stage_filters")
                    .forGetter(EffectGenerationPoolDefinition::stageFilters))
            .apply(instance, EffectGenerationPoolDefinition::new));

    public record SizeWeight(int size, int weight) {
        public static final Codec<SizeWeight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("size").forGetter(SizeWeight::size),
                Codec.INT.fieldOf("weight").forGetter(SizeWeight::weight))
                .apply(instance, SizeWeight::new));
    }
}
