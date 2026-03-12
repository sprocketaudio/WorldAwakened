package net.sprocketgames.worldawakened.data.definition;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record EntityBossFlagMapDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        List<BossFlagEntry> entries) implements WorldAwakenedDataDefinition {
    public static final Codec<EntityBossFlagMapDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(EntityBossFlagMapDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(EntityBossFlagMapDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(EntityBossFlagMapDefinition::enabled),
            BossFlagEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(EntityBossFlagMapDefinition::entries))
            .apply(instance, EntityBossFlagMapDefinition::new));
}

