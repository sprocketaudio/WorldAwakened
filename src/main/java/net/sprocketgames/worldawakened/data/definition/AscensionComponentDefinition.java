package net.sprocketgames.worldawakened.data.definition;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record AscensionComponentDefinition(
        ResourceLocation type,
        boolean enabled,
        int priority,
        JsonObject parameters,
        List<JsonElement> conditions,
        List<ResourceLocation> exclusions,
        List<ResourceLocation> conflictsWith) {
    public static final Codec<AscensionComponentDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("type").forGetter(AscensionComponentDefinition::type),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(AscensionComponentDefinition::enabled),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(AscensionComponentDefinition::priority),
            WorldAwakenedJsonCodecs.JSON_OBJECT.optionalFieldOf("parameters", new JsonObject()).forGetter(AscensionComponentDefinition::parameters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("conditions", List.of()).forGetter(AscensionComponentDefinition::conditions),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("exclusions", List.of()).forGetter(AscensionComponentDefinition::exclusions),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("conflicts_with", List.of()).forGetter(AscensionComponentDefinition::conflictsWith))
            .apply(instance, AscensionComponentDefinition::new));
}
