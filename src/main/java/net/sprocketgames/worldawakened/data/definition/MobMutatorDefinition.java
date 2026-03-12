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

public record MobMutatorDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        int weight,
        List<MutationComponentDefinition> components,
        MutatorPresentation presentation,
        MutatorStacking stacking,
        MutatorEligibility eligibility,
        MutatorBehavior behavior) implements WorldAwakenedDataDefinition {
    private static final MapCodec<MutatorPresentation> PRESENTATION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.JSON_ELEMENT.fieldOf("display_name").forGetter(MutatorPresentation::displayName),
            Codec.STRING.optionalFieldOf("rarity").forGetter(MutatorPresentation::rarity))
            .apply(instance, MutatorPresentation::new));

    private static final MapCodec<MutatorStacking> STACKING_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("stacking_group").forGetter(MutatorStacking::stackingGroup),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("exclusive_with", List.of())
                    .forGetter(MutatorStacking::exclusiveWith),
            Codec.INT.optionalFieldOf("max_stack_count", 1).forGetter(MutatorStacking::maxStackCount))
            .apply(instance, MutatorStacking::new));

    private static final MapCodec<MutatorEligibility> ELIGIBILITY_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_entities", List.of())
                    .forGetter(MutatorEligibility::eligibleEntities),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("eligible_entity_tags", List.of())
                    .forGetter(MutatorEligibility::eligibleEntityTags),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("excluded_entities", List.of())
                    .forGetter(MutatorEligibility::excludedEntities),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("excluded_entity_tags", List.of())
                    .forGetter(MutatorEligibility::excludedEntityTags),
            Codec.BOOL.optionalFieldOf("applies_to_bosses", false).forGetter(MutatorEligibility::appliesToBosses),
            Codec.BOOL.optionalFieldOf("applies_to_invaders", false).forGetter(MutatorEligibility::appliesToInvaders))
            .apply(instance, MutatorEligibility::new));

    private static final MapCodec<MutatorBehavior> BEHAVIOR_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("required_conditions", List.of())
                    .forGetter(MutatorBehavior::requiredConditions),
            WorldAwakenedJsonCodecs.JSON_OBJECT.optionalFieldOf("reward_modifier", new JsonObject())
                    .forGetter(MutatorBehavior::rewardModifier),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("visuals").forGetter(MutatorBehavior::visuals),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("sounds").forGetter(MutatorBehavior::sounds),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("application_contexts", List.of("on_spawn")).forGetter(MutatorBehavior::applicationContexts),
            Codec.INT.optionalFieldOf("component_budget").forGetter(MutatorBehavior::componentBudget))
            .apply(instance, MutatorBehavior::new));

    public static final Codec<MobMutatorDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(MobMutatorDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(MobMutatorDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(MobMutatorDefinition::enabled),
            Codec.INT.fieldOf("weight").forGetter(MobMutatorDefinition::weight),
            MutationComponentDefinition.CODEC.listOf().fieldOf("components").forGetter(MobMutatorDefinition::components),
            PRESENTATION_CODEC.forGetter(MobMutatorDefinition::presentation),
            STACKING_CODEC.forGetter(MobMutatorDefinition::stacking),
            ELIGIBILITY_CODEC.forGetter(MobMutatorDefinition::eligibility),
            BEHAVIOR_CODEC.forGetter(MobMutatorDefinition::behavior))
            .apply(instance, MobMutatorDefinition::new));

    public JsonElement displayName() {
        return presentation.displayName();
    }

    public Optional<String> rarity() {
        return presentation.rarity();
    }

    public Optional<String> stackingGroup() {
        return stacking.stackingGroup();
    }

    public List<ResourceLocation> exclusiveWith() {
        return stacking.exclusiveWith();
    }

    public int maxStackCount() {
        return stacking.maxStackCount();
    }

    public List<ResourceLocation> eligibleEntities() {
        return eligibility.eligibleEntities();
    }

    public List<String> eligibleEntityTags() {
        return eligibility.eligibleEntityTags();
    }

    public List<ResourceLocation> excludedEntities() {
        return eligibility.excludedEntities();
    }

    public List<String> excludedEntityTags() {
        return eligibility.excludedEntityTags();
    }

    public boolean appliesToBosses() {
        return eligibility.appliesToBosses();
    }

    public boolean appliesToInvaders() {
        return eligibility.appliesToInvaders();
    }

    public List<JsonElement> requiredConditions() {
        return behavior.requiredConditions();
    }

    public JsonObject rewardModifier() {
        return behavior.rewardModifier();
    }

    public Optional<JsonElement> visuals() {
        return behavior.visuals();
    }

    public Optional<JsonElement> sounds() {
        return behavior.sounds();
    }

    public List<String> applicationContexts() {
        return behavior.applicationContexts();
    }

    public Optional<Integer> componentBudget() {
        return behavior.componentBudget();
    }

    public record MutatorPresentation(
            JsonElement displayName,
            Optional<String> rarity) {
    }

    public record MutatorStacking(
            Optional<String> stackingGroup,
            List<ResourceLocation> exclusiveWith,
            int maxStackCount) {
    }

    public record MutatorEligibility(
            List<ResourceLocation> eligibleEntities,
            List<String> eligibleEntityTags,
            List<ResourceLocation> excludedEntities,
            List<String> excludedEntityTags,
            boolean appliesToBosses,
            boolean appliesToInvaders) {
    }

    public record MutatorBehavior(
            List<JsonElement> requiredConditions,
            JsonObject rewardModifier,
            Optional<JsonElement> visuals,
            Optional<JsonElement> sounds,
            List<String> applicationContexts,
            Optional<Integer> componentBudget) {
    }
}

