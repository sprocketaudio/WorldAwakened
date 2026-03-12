package net.sprocketgames.worldawakened.data.definition;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record MutationComponentDefinition(
        ResourceLocation type,
        boolean enabled,
        int priority,
        JsonObject parameters,
        List<JsonElement> conditions,
        List<ResourceLocation> exclusions,
        List<ResourceLocation> conflictsWith) {
    public static final Codec<MutationComponentDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("type").forGetter(MutationComponentDefinition::type),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(MutationComponentDefinition::enabled),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MutationComponentDefinition::priority),
            WorldAwakenedJsonCodecs.JSON_OBJECT.optionalFieldOf("parameters", new JsonObject()).forGetter(MutationComponentDefinition::parameters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("conditions", List.of()).forGetter(MutationComponentDefinition::conditions),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("exclusions", List.of()).forGetter(MutationComponentDefinition::exclusions),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("conflicts_with", List.of()).forGetter(MutationComponentDefinition::conflictsWith))
            .apply(instance, MutationComponentDefinition::new));
}
