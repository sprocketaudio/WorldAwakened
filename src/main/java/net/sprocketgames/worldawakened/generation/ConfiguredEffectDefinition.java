package net.sprocketgames.worldawakened.generation;

import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;
import net.sprocketgames.worldawakened.data.definition.WorldAwakenedDataDefinition;

/**
 * A reusable, authored instance of an existing runtime-backed component.
 * Power classifies a completed combination; selection weight only controls frequency.
 */
public record ConfiguredEffectDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        WorldAwakenedEffectScope scope,
        ResourceLocation effectType,
        JsonObject parameters,
        int power,
        int selectionWeight,
        boolean allowRepeat,
        List<ResourceLocation> excludes) implements WorldAwakenedDataDefinition {
    public static final Codec<ConfiguredEffectDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(ConfiguredEffectDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(ConfiguredEffectDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ConfiguredEffectDefinition::enabled),
            WorldAwakenedEffectScope.CODEC.fieldOf("scope").forGetter(ConfiguredEffectDefinition::scope),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("effect_type").forGetter(ConfiguredEffectDefinition::effectType),
            WorldAwakenedJsonCodecs.JSON_OBJECT.optionalFieldOf("parameters", new JsonObject())
                    .forGetter(ConfiguredEffectDefinition::parameters),
            Codec.INT.optionalFieldOf("power", 0).forGetter(ConfiguredEffectDefinition::power),
            Codec.INT.optionalFieldOf("selection_weight", 1).forGetter(ConfiguredEffectDefinition::selectionWeight),
            Codec.BOOL.optionalFieldOf("allow_repeat", false).forGetter(ConfiguredEffectDefinition::allowRepeat),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("excludes", List.of())
                    .forGetter(ConfiguredEffectDefinition::excludes))
            .apply(instance, ConfiguredEffectDefinition::new));
}
