package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

/** Friendly native-editor metadata which compiles into the canonical loot resolver. */
public record LootProfileAuthoringDefinition(
        Optional<String> displayName,
        Optional<String> description,
        List<ResourceLocation> targetEntities,
        List<ResourceLocation> eligibleDimensions,
        double itemChance,
        Optional<LootBonusXpDefinition> bonusXp) {
    public static final LootProfileAuthoringDefinition EMPTY = new LootProfileAuthoringDefinition(
            Optional.empty(), Optional.empty(), List.of(), List.of(), 1.0D, Optional.empty());

    public static final Codec<LootProfileAuthoringDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("display_name").forGetter(LootProfileAuthoringDefinition::displayName),
            Codec.STRING.optionalFieldOf("description").forGetter(LootProfileAuthoringDefinition::description),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("target_entities", List.of())
                    .forGetter(LootProfileAuthoringDefinition::targetEntities),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_dimensions", List.of())
                    .forGetter(LootProfileAuthoringDefinition::eligibleDimensions),
            Codec.DOUBLE.optionalFieldOf("item_chance", 1.0D).forGetter(LootProfileAuthoringDefinition::itemChance),
            LootBonusXpDefinition.CODEC.optionalFieldOf("bonus_xp").forGetter(LootProfileAuthoringDefinition::bonusXp))
            .apply(instance, LootProfileAuthoringDefinition::new));
}
