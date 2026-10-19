package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record LootProfileDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        LootProfileType profileType,
        LootProfileAuthoringDefinition authoring,
        List<ResourceLocation> targetLootTables,
        List<JsonElement> conditions,
        Optional<JsonElement> apotheosisTierFilters,
        Optional<JsonElement> stageFilters,
        Optional<JsonElement> mutationRarityFilters,
        LootReplaceMode replaceMode,
        List<JsonElement> entries,
        double weightMultiplier,
        double qualityScalar,
        Optional<String> configGate,
        List<JsonElement> modConditions) implements WorldAwakenedDataDefinition {
    public static final Codec<LootProfileDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(LootProfileDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(LootProfileDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(LootProfileDefinition::enabled),
            WorldAwakenedJsonCodecs.enumCodec(LootProfileType.class).fieldOf("profile_type")
                    .forGetter(LootProfileDefinition::profileType),
            LootProfileAuthoringDefinition.CODEC.optionalFieldOf("authoring", LootProfileAuthoringDefinition.EMPTY)
                    .forGetter(LootProfileDefinition::authoring),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("target_loot_tables", List.of()).forGetter(LootProfileDefinition::targetLootTables),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("conditions", List.of()).forGetter(LootProfileDefinition::conditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("apotheosis_tier_filters")
                    .forGetter(LootProfileDefinition::apotheosisTierFilters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("stage_filters").forGetter(LootProfileDefinition::stageFilters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("mutation_rarity_filters")
                    .forGetter(LootProfileDefinition::mutationRarityFilters),
            WorldAwakenedJsonCodecs.enumCodec(LootReplaceMode.class).optionalFieldOf("replace_mode", LootReplaceMode.INJECT)
                    .forGetter(LootProfileDefinition::replaceMode),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("entries", List.of()).forGetter(LootProfileDefinition::entries),
            Codec.DOUBLE.optionalFieldOf("weight_multiplier", 1.0D).forGetter(LootProfileDefinition::weightMultiplier),
            Codec.DOUBLE.optionalFieldOf("quality_scalar", 1.0D).forGetter(LootProfileDefinition::qualityScalar),
            Codec.STRING.optionalFieldOf("config_gate").forGetter(LootProfileDefinition::configGate),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("mod_conditions", List.of()).forGetter(LootProfileDefinition::modConditions))
            .apply(instance, LootProfileDefinition::new));
}

