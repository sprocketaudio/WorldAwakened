package net.sprocketgames.worldawakened.rarity;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;
import net.sprocketgames.worldawakened.data.definition.WorldAwakenedDataDefinition;

/** A data-defined classification threshold for a resolved effect combination. */
public record RarityDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        JsonElement displayName,
        int minimumPower) implements WorldAwakenedDataDefinition {
    public static final Codec<RarityDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(RarityDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(RarityDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(RarityDefinition::enabled),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.fieldOf("display_name").forGetter(RarityDefinition::displayName),
            Codec.INT.fieldOf("minimum_power").forGetter(RarityDefinition::minimumPower))
            .apply(instance, RarityDefinition::new));
}
