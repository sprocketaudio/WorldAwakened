package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record StageDefinition(
        int schemaVersion,
        ResourceLocation id,
        List<ResourceLocation> aliases,
        JsonElement displayName,
        Optional<JsonElement> shortName,
        Optional<JsonElement> description,
        Optional<JsonElement> icon,
        int sortIndex,
        boolean visibleToPlayers,
        boolean enabled,
        List<String> tags,
        Optional<JsonElement> style,
        Optional<String> progressionGroup,
        StageUnlockPolicy unlockPolicy,
        boolean defaultUnlocked) implements WorldAwakenedDataDefinition {
    public static final Codec<StageDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(StageDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(StageDefinition::id),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("aliases", List.of()).forGetter(StageDefinition::aliases),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.fieldOf("display_name").forGetter(StageDefinition::displayName),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("short_name").forGetter(StageDefinition::shortName),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("description").forGetter(StageDefinition::description),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("icon").forGetter(StageDefinition::icon),
            Codec.INT.optionalFieldOf("sort_index", 0).forGetter(StageDefinition::sortIndex),
            Codec.BOOL.optionalFieldOf("visible_to_players", true).forGetter(StageDefinition::visibleToPlayers),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(StageDefinition::enabled),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("tags", List.of()).forGetter(StageDefinition::tags),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("style").forGetter(StageDefinition::style),
            Codec.STRING.optionalFieldOf("progression_group").forGetter(StageDefinition::progressionGroup),
            WorldAwakenedJsonCodecs.enumCodec(StageUnlockPolicy.class).optionalFieldOf("unlock_policy", StageUnlockPolicy.CUMULATIVE)
                    .forGetter(StageDefinition::unlockPolicy),
            Codec.BOOL.optionalFieldOf("default_unlocked", false).forGetter(StageDefinition::defaultUnlocked))
            .apply(instance, StageDefinition::new));
}

