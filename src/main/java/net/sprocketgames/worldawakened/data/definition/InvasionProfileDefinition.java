package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record InvasionProfileDefinition(
        int schemaVersion,
        ResourceLocation id,
        JsonElement displayName,
        boolean enabled,
        InvasionTriggerMode triggerMode,
        List<JsonElement> conditions,
        Optional<JsonElement> stageFilters,
        List<ResourceLocation> dimensions,
        List<ResourceLocation> biomeFilters,
        int minPlayers,
        Optional<Integer> cooldownSeconds,
        Optional<Integer> warningSeconds,
        int durationSeconds,
        double pressureModifier,
        Optional<ResourceLocation> rewardProfile,
        List<String> tags) implements WorldAwakenedDataDefinition {

    public static final Codec<InvasionProfileDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(InvasionProfileDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(InvasionProfileDefinition::id),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.fieldOf("display_name").forGetter(InvasionProfileDefinition::displayName),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(InvasionProfileDefinition::enabled),
            WorldAwakenedJsonCodecs.enumCodec(InvasionTriggerMode.class).fieldOf("trigger_mode").forGetter(InvasionProfileDefinition::triggerMode),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("conditions", List.of()).forGetter(InvasionProfileDefinition::conditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("stage_filters").forGetter(InvasionProfileDefinition::stageFilters),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("dimensions", List.of()).forGetter(InvasionProfileDefinition::dimensions),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("biome_filters", List.of()).forGetter(InvasionProfileDefinition::biomeFilters),
            Codec.INT.optionalFieldOf("min_players", 1).forGetter(InvasionProfileDefinition::minPlayers),
            Codec.INT.optionalFieldOf("cooldown_seconds").forGetter(InvasionProfileDefinition::cooldownSeconds),
            Codec.INT.optionalFieldOf("warning_seconds").forGetter(InvasionProfileDefinition::warningSeconds),
            Codec.INT.fieldOf("duration_seconds").forGetter(InvasionProfileDefinition::durationSeconds),
            Codec.DOUBLE.optionalFieldOf("pressure_modifier", 1.0D).forGetter(InvasionProfileDefinition::pressureModifier),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.optionalFieldOf("reward_profile").forGetter(InvasionProfileDefinition::rewardProfile),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("tags", List.of()).forGetter(InvasionProfileDefinition::tags))
            .apply(instance, InvasionProfileDefinition::new));
}

