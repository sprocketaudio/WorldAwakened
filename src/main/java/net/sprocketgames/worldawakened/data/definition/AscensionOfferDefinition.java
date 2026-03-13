package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record AscensionOfferDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        OfferPresentation presentation,
        OfferFilters filters,
        OfferCandidates candidates,
        OfferPolicy policy) implements WorldAwakenedDataDefinition {
    private static final MapCodec<OfferPresentation> PRESENTATION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.JSON_ELEMENT.fieldOf("display_name").forGetter(OfferPresentation::displayName),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("description").forGetter(OfferPresentation::description))
            .apply(instance, OfferPresentation::new));

    private static final MapCodec<OfferFilters> FILTERS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("trigger_conditions", List.of())
                    .forGetter(OfferFilters::triggerConditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("stage_filters").forGetter(OfferFilters::stageFilters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("pressure_tier_filters").forGetter(OfferFilters::pressureTierFilters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("apotheosis_tier_filters")
                    .forGetter(OfferFilters::apotheosisTierFilters))
            .apply(instance, OfferFilters::new));

    private static final MapCodec<OfferCandidates> CANDIDATES_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("candidate_rewards", List.of())
                    .forGetter(OfferCandidates::candidateRewards),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("candidate_reward_tags", List.of())
                    .forGetter(OfferCandidates::candidateRewardTags),
            WorldAwakenedJsonCodecs.enumCodec(AscensionOfferMode.class).optionalFieldOf("offer_mode", AscensionOfferMode.EXPLICIT_LIST)
                    .forGetter(OfferCandidates::offerMode),
            WorldAwakenedJsonCodecs.JSON_OBJECT.optionalFieldOf("weighting_rules", new JsonObject()).forGetter(OfferCandidates::weightingRules))
            .apply(instance, OfferCandidates::new));

    private static final MapCodec<OfferPolicy> POLICY_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("choice_count", 2).forGetter(OfferPolicy::choiceCount),
            Codec.INT.optionalFieldOf("selection_count", 1).forGetter(OfferPolicy::selectionCount),
            Codec.INT.optionalFieldOf("ui_priority", 0).forGetter(OfferPolicy::uiPriority),
            Codec.BOOL.optionalFieldOf("allow_duplicates_across_players", true).forGetter(OfferPolicy::allowDuplicatesAcrossPlayers),
            WorldAwakenedJsonCodecs.enumCodec(AscensionRewardRepeatPolicy.class)
                    .optionalFieldOf("reward_repeat_policy", AscensionRewardRepeatPolicy.BLOCK_ALL)
                    .forGetter(OfferPolicy::rewardRepeatPolicy))
            .apply(instance, OfferPolicy::new));

    public static final Codec<AscensionOfferDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(AscensionOfferDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(AscensionOfferDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(AscensionOfferDefinition::enabled),
            PRESENTATION_CODEC.forGetter(AscensionOfferDefinition::presentation),
            FILTERS_CODEC.forGetter(AscensionOfferDefinition::filters),
            CANDIDATES_CODEC.forGetter(AscensionOfferDefinition::candidates),
            POLICY_CODEC.forGetter(AscensionOfferDefinition::policy))
            .apply(instance, AscensionOfferDefinition::new));

    public JsonElement displayName() {
        return presentation.displayName();
    }

    public Optional<JsonElement> description() {
        return presentation.description();
    }

    public List<JsonElement> triggerConditions() {
        return filters.triggerConditions();
    }

    public Optional<JsonElement> stageFilters() {
        return filters.stageFilters();
    }

    public Optional<JsonElement> pressureTierFilters() {
        return filters.pressureTierFilters();
    }

    public Optional<JsonElement> apotheosisTierFilters() {
        return filters.apotheosisTierFilters();
    }

    public int choiceCount() {
        return policy.choiceCount();
    }

    public int selectionCount() {
        return policy.selectionCount();
    }

    public int uiPriority() {
        return policy.uiPriority();
    }

    public boolean allowDuplicatesAcrossPlayers() {
        return policy.allowDuplicatesAcrossPlayers();
    }

    public AscensionRewardRepeatPolicy rewardRepeatPolicy() {
        return policy.rewardRepeatPolicy();
    }

    public List<ResourceLocation> candidateRewards() {
        return candidates.candidateRewards();
    }

    public List<String> candidateRewardTags() {
        return candidates.candidateRewardTags();
    }

    public AscensionOfferMode offerMode() {
        return candidates.offerMode();
    }

    public JsonObject weightingRules() {
        return candidates.weightingRules();
    }

    public record OfferPresentation(
            JsonElement displayName,
            Optional<JsonElement> description) {
    }

    public record OfferFilters(
            List<JsonElement> triggerConditions,
            Optional<JsonElement> stageFilters,
            Optional<JsonElement> pressureTierFilters,
            Optional<JsonElement> apotheosisTierFilters) {
    }

    public record OfferCandidates(
            List<ResourceLocation> candidateRewards,
            List<String> candidateRewardTags,
            AscensionOfferMode offerMode,
            JsonObject weightingRules) {
    }

    public record OfferPolicy(
            int choiceCount,
            int selectionCount,
            int uiPriority,
            boolean allowDuplicatesAcrossPlayers,
            AscensionRewardRepeatPolicy rewardRepeatPolicy) {
    }
}

