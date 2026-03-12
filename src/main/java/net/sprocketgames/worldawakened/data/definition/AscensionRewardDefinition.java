package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record AscensionRewardDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        List<AscensionComponentDefinition> components,
        RewardPresentation presentation,
        RewardWeights weights,
        RewardConstraints constraints) implements WorldAwakenedDataDefinition {
    private static final MapCodec<RewardPresentation> PRESENTATION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.JSON_ELEMENT.fieldOf("display_name").forGetter(RewardPresentation::displayName),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("description").forGetter(RewardPresentation::description),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("icon").forGetter(RewardPresentation::icon),
            Codec.STRING.optionalFieldOf("rarity").forGetter(RewardPresentation::rarity),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("tags", List.of()).forGetter(RewardPresentation::tags),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("ui_style").forGetter(RewardPresentation::uiStyle))
            .apply(instance, RewardPresentation::new));

    private static final MapCodec<RewardWeights> WEIGHTS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("tier_weight").forGetter(RewardWeights::tierWeight),
            Codec.DOUBLE.optionalFieldOf("offer_weight").forGetter(RewardWeights::offerWeight))
            .apply(instance, RewardWeights::new));

    private static final MapCodec<RewardConstraints> CONSTRAINTS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("unique_group").forGetter(RewardConstraints::uniqueGroup),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("exclusion_tags", List.of()).forGetter(RewardConstraints::exclusionTags),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("requires_conditions", List.of())
                    .forGetter(RewardConstraints::requiresConditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("forbidden_conditions", List.of())
                    .forGetter(RewardConstraints::forbiddenConditions),
            Codec.INT.optionalFieldOf("max_rank", 1).forGetter(RewardConstraints::maxRank))
            .apply(instance, RewardConstraints::new));

    public static final Codec<AscensionRewardDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(AscensionRewardDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(AscensionRewardDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(AscensionRewardDefinition::enabled),
            AscensionComponentDefinition.CODEC.listOf().fieldOf("components").forGetter(AscensionRewardDefinition::components),
            PRESENTATION_CODEC.forGetter(AscensionRewardDefinition::presentation),
            WEIGHTS_CODEC.forGetter(AscensionRewardDefinition::weights),
            CONSTRAINTS_CODEC.forGetter(AscensionRewardDefinition::constraints))
            .apply(instance, AscensionRewardDefinition::new));

    public JsonElement displayName() {
        return presentation.displayName();
    }

    public Optional<JsonElement> description() {
        return presentation.description();
    }

    public Optional<JsonElement> icon() {
        return presentation.icon();
    }

    public Optional<String> rarity() {
        return presentation.rarity();
    }

    public List<String> tags() {
        return presentation.tags();
    }

    public Optional<JsonElement> uiStyle() {
        return presentation.uiStyle();
    }

    public Optional<Double> tierWeight() {
        return weights.tierWeight();
    }

    public Optional<Double> offerWeight() {
        return weights.offerWeight();
    }

    public Optional<String> uniqueGroup() {
        return constraints.uniqueGroup();
    }

    public List<String> exclusionTags() {
        return constraints.exclusionTags();
    }

    public List<JsonElement> requiresConditions() {
        return constraints.requiresConditions();
    }

    public List<JsonElement> forbiddenConditions() {
        return constraints.forbiddenConditions();
    }

    public int maxRank() {
        return constraints.maxRank();
    }

    public record RewardPresentation(
            JsonElement displayName,
            Optional<JsonElement> description,
            Optional<JsonElement> icon,
            Optional<String> rarity,
            List<String> tags,
            Optional<JsonElement> uiStyle) {
    }

    public record RewardWeights(
            Optional<Double> tierWeight,
            Optional<Double> offerWeight) {
    }

    public record RewardConstraints(
            Optional<String> uniqueGroup,
            List<String> exclusionTags,
            List<JsonElement> requiresConditions,
            List<JsonElement> forbiddenConditions,
            int maxRank) {
    }
}

