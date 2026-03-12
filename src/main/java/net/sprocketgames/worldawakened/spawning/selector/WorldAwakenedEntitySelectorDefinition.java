package net.sprocketgames.worldawakened.spawning.selector;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record WorldAwakenedEntitySelectorDefinition(
        List<ResourceLocation> eligibleEntities,
        List<String> eligibleEntityTags,
        List<String> eligibleNamespaces,
        List<String> eligibleMobCategories,
        List<ResourceLocation> excludedEntities,
        List<String> excludedEntityTags) {
    public static final Codec<WorldAwakenedEntitySelectorDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_entities", List.of())
                    .forGetter(WorldAwakenedEntitySelectorDefinition::eligibleEntities),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("eligible_entity_tags", List.of())
                    .forGetter(WorldAwakenedEntitySelectorDefinition::eligibleEntityTags),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("eligible_namespaces", List.of())
                    .forGetter(WorldAwakenedEntitySelectorDefinition::eligibleNamespaces),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("eligible_mob_categories", List.of())
                    .forGetter(WorldAwakenedEntitySelectorDefinition::eligibleMobCategories),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("excluded_entities", List.of())
                    .forGetter(WorldAwakenedEntitySelectorDefinition::excludedEntities),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("excluded_entity_tags", List.of())
                    .forGetter(WorldAwakenedEntitySelectorDefinition::excludedEntityTags))
            .apply(instance, WorldAwakenedEntitySelectorDefinition::new));
}

