package net.sprocketgames.worldawakened.data.load;

import java.io.Reader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.sprocketgames.worldawakened.ascension.component.WorldAwakenedAscensionComponentValidation;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedConfigService;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.WorldAwakenedDataDefinition;
import net.sprocketgames.worldawakened.data.definition.EntityBossFlagMapDefinition;
import net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootProfileType;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.validation.WorldAwakenedMutationPoolValidation;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.generation.ConfiguredEffectDefinition;
import net.sprocketgames.worldawakened.generation.EffectGenerationPoolDefinition;
import net.sprocketgames.worldawakened.generation.WorldAwakenedEffectScope;
import net.sprocketgames.worldawakened.rarity.RarityDefinition;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnostic;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticSeverity;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRejectionReason;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuntimeLayer;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleEngine;
import net.sprocketgames.worldawakened.mutator.component.WorldAwakenedMutationComponentValidation;
import net.sprocketgames.worldawakened.mutator.component.WorldAwakenedMutationComponentRegistry;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionRewardEffects;
import net.sprocketgames.worldawakened.ascension.component.WorldAwakenedAscensionComponentRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

public final class WorldAwakenedDatapackLoader {
    private static final Pattern CONFIG_GATE_PATTERN = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+)*$");
    static final int MAX_MUTATORS_PER_SPAWN_BUDGET = 8;
    static final int MAX_COMPONENTS_PER_MUTATOR_BUDGET = 10;
    private static final Set<String> STAGE_CONTEXT_KEYS = Set.of(
            "stage",
            "stage_id",
            "stageid",
            "stage_ref",
            "required_stage",
            "unlock_stage",
            "lock_stage",
            "stage_filters",
            "stages",
            "min_stage",
            "max_stage",
            "stage_alias");
    private static final Set<String> SUPPORTED_TRIGGER_CONDITION_PATHS = Set.of(
            "stage_unlocked",
            "stage_locked",
            "current_dimension",
            "world_day_gte",
            "advancement_completed",
            "entity_type",
            "entity_tag",
            "manual_trigger",
            "boss_killed");
    private static final Set<String> SUPPORTED_TRIGGER_ACTION_PATHS = Set.of(
            "unlock_stage",
            "lock_stage",
            "emit_named_event",
            "increment_counter",
            "record_unlock_condition",
            "send_warning_message",
            "grant_ascension_offer");

    private static final WorldAwakenedObjectType<StageDefinition> STAGES = new WorldAwakenedObjectType<>(
            "stages",
            "stages",
            StageDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.id() == null) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "stages",
                            null,
                            sourcePath,
                            "Missing required field: id",
                            "disabled_object"));
                }
            });

    private static final WorldAwakenedObjectType<RarityDefinition> RARITIES = new WorldAwakenedObjectType<>(
            "rarities", "rarities", RarityDefinition.CODEC, (sourcePath, definition, collector) -> {
                if (definition.minimumPower() < 0) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "rarities", definition.id(), sourcePath,
                            "minimum_power must be >= 0", "disabled_object"));
                }
            });

    private static final WorldAwakenedObjectType<ConfiguredEffectDefinition> CONFIGURED_EFFECTS = new WorldAwakenedObjectType<>(
            "configured_effects", "configured_effects", ConfiguredEffectDefinition.CODEC, (sourcePath, definition, collector) -> {
                if (definition.power() < 0 || definition.selectionWeight() <= 0) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "configured_effects", definition.id(), sourcePath,
                            "power must be >= 0 and selection_weight must be > 0", "disabled_object"));
                    return;
                }
                Optional<String> parameterError;
                boolean live;
                if (definition.scope() == WorldAwakenedEffectScope.MOB_MUTATION) {
                    parameterError = WorldAwakenedMutationComponentRegistry.lookup(definition.effectType())
                            .map(type -> type.parameterValidator().validate(definition.parameters()))
                            .orElse(Optional.of("unknown mutation component type: " + definition.effectType()));
                    live = WorldAwakenedMutationComponentRegistry.hasLiveRuntimeHandler(definition.effectType());
                } else {
                    parameterError = WorldAwakenedAscensionComponentRegistry.lookup(definition.effectType())
                            .map(type -> type.parameterValidator().validate(definition.parameters()))
                            .orElse(Optional.of("unknown ascension component type: " + definition.effectType()));
                    live = WorldAwakenedAscensionRewardEffects.hasLiveRuntimeHandler(definition.effectType());
                }
                parameterError.ifPresent(error -> collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.COMPONENT_PARAMETERS_INVALID,
                        "configured_effects", definition.id(), sourcePath, error, "disabled_object")));
                if (!live) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.COMPONENT_NO_RUNTIME_RESULT,
                            "configured_effects", definition.id(), sourcePath,
                            "effect_type has no safe live runtime handler: " + definition.effectType(), "disabled_object"));
                }
            });

    private static final WorldAwakenedObjectType<EffectGenerationPoolDefinition> EFFECT_GENERATION_POOLS = new WorldAwakenedObjectType<>(
            "effect_generation_pools", "effect_generation_pools", EffectGenerationPoolDefinition.CODEC, (sourcePath, definition, collector) -> {
                if (definition.configuredEffects().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "effect_generation_pools", definition.id(), sourcePath,
                            "configured_effects must include at least one effect instance", "disabled_object"));
                }
                if (definition.combinationSizes().isEmpty() || definition.combinationSizes().stream()
                        .anyMatch(size -> size.size() < 1 || size.weight() <= 0)) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.POOL_SELECTION_IMPOSSIBLE,
                            "effect_generation_pools", definition.id(), sourcePath,
                            "combination_sizes must contain positive size and weight values", "disabled_object"));
                }
                if (definition.curatedSourceWeight() < 0 || definition.generatedSourceWeight() < 0
                        || (definition.mode().name().equals("MIXED")
                        && definition.curatedSourceWeight() + definition.generatedSourceWeight() <= 0)) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.POOL_SELECTION_IMPOSSIBLE,
                            "effect_generation_pools", definition.id(), sourcePath,
                            "source weights must be non-negative and Mixed needs a positive total", "disabled_object"));
                }
                validateTypedNodes("effect_generation_pools", definition.id(), sourcePath, "stage_filters",
                        definition.stageFilters().map(List::of).orElse(List.of()), true, collector);
            });

    private static final WorldAwakenedObjectType<TriggerRuleDefinition> TRIGGER_RULES = new WorldAwakenedObjectType<>(
            "trigger_rules",
            "trigger_rules",
            TriggerRuleDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.actions().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "trigger_rules",
                            definition.id(),
                            sourcePath,
                            "Trigger rule must include at least one action",
                            "disabled_object"));
                }
                String sourceScope = definition.sourceScope().name().toLowerCase(Locale.ROOT);
                validateTypedNodes(
                        "trigger_rules",
                        definition.id(),
                        sourcePath,
                        "conditions",
                        definition.conditions(),
                        true,
                        Optional.of(sourceScope),
                        collector);
                validateTypedNodes(
                        "trigger_rules",
                        definition.id(),
                        sourcePath,
                        "actions",
                        definition.actions(),
                        false,
                        Optional.of(sourceScope),
                        collector);
            });

    private static final WorldAwakenedObjectType<RuleDefinition> RULES = new WorldAwakenedObjectType<>(
            "rules",
            "rules",
            RuleDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.conditions().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.WARNING,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "rules",
                            definition.id(),
                            sourcePath,
                            "Rule has no conditions; it will always be eligible unless gated elsewhere",
                            "retained"));
                }
                if (definition.weight() <= 0.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "rules",
                            definition.id(),
                            sourcePath,
                            "weight must be > 0",
                            "disabled_object"));
                }
                if (definition.chance() < 0.0D || definition.chance() > 1.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "rules",
                            definition.id(),
                            sourcePath,
                            "chance must be in range [0.0, 1.0]",
                            "disabled_object"));
                }
                String executionScope = definition.executionScope().name().toLowerCase(Locale.ROOT);
                validateTypedNodes(
                        "rules",
                        definition.id(),
                        sourcePath,
                        "conditions",
                        definition.conditions(),
                        true,
                        Optional.of(executionScope),
                        collector);
                validateTypedNodes(
                        "rules",
                        definition.id(),
                        sourcePath,
                        "actions",
                        definition.actions(),
                        false,
                        Optional.of(executionScope),
                        collector);
            });

    private static final WorldAwakenedObjectType<AscensionRewardDefinition> ASCENSION_REWARDS = new WorldAwakenedObjectType<>(
            "ascension_rewards",
            "ascension_rewards",
            AscensionRewardDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                validateAscensionComponents(definition, sourcePath, collector);
                if (definition.maxRank() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.ASCENSION_REWARD_INVALID,
                            "ascension_rewards",
                            definition.id(),
                            sourcePath,
                            "max_rank must be >= 1",
                            "disabled_object"));
                }
                validateIconReference("ascension_rewards", definition.id(), sourcePath, definition.icon(), collector);
                validateTypedNodes("ascension_rewards", definition.id(), sourcePath, "requires_conditions", definition.requiresConditions(), true, collector);
                validateTypedNodes("ascension_rewards", definition.id(), sourcePath, "forbidden_conditions", definition.forbiddenConditions(), true, collector);
                for (int index = 0; index < definition.components().size(); index++) {
                    AscensionComponentDefinition component = definition.components().get(index);
                    validateTypedNodes(
                            "ascension_rewards",
                            definition.id(),
                            sourcePath,
                            "components[" + index + "].conditions",
                            component.conditions(),
                            true,
                            collector);
                }
                collector.addTrace(
                        WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                        "ascension_rewards",
                        definition.id(),
                        sourcePath,
                        "resolved_components=" + joinResourceLocations(definition.components().stream().map(AscensionComponentDefinition::type).toList()));
            });

    private static final WorldAwakenedObjectType<AscensionOfferDefinition> ASCENSION_OFFERS = new WorldAwakenedObjectType<>(
            "ascension_offers",
            "ascension_offers",
            AscensionOfferDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.choiceCount() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "ascension_offers",
                            definition.id(),
                            sourcePath,
                            "choice_count must be >= 1",
                            "disabled_object"));
                }
                if (definition.selectionCount() != 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.ASCENSION_REWARD_INVALID,
                            "ascension_offers",
                            definition.id(),
                            sourcePath,
                            "selection_count must equal 1 in v1",
                            "disabled_object"));
                }
                if (definition.candidateRewards().isEmpty() && definition.candidateRewardTags().isEmpty()
                        && definition.effectGenerationPool().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.ASCENSION_REWARD_INVALID,
                            "ascension_offers",
                            definition.id(),
                            sourcePath,
                            "Offer must include candidate_rewards, candidate_reward_tags, or effect_generation_pool",
                            "disabled_object"));
                }
                validateTypedNodes("ascension_offers", definition.id(), sourcePath, "trigger_conditions", definition.triggerConditions(), true, collector);
                validateOptionalApotheosisFilter("ascension_offers", definition.id(), sourcePath, definition.apotheosisTierFilters(), collector);
            });

    private static final WorldAwakenedObjectType<MobMutatorDefinition> MOB_MUTATORS = new WorldAwakenedObjectType<>(
            "mob_mutators",
            "mob_mutators",
            MobMutatorDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.weight() <= 0) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "mob_mutators",
                            definition.id(),
                            sourcePath,
                            "Mutator weight must be > 0",
                            "disabled_object"));
                }
                if (definition.maxStackCount() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "mob_mutators",
                            definition.id(),
                            sourcePath,
                            "max_stack_count must be >= 1",
                            "disabled_object"));
                }
                validateMutationComponents(definition, sourcePath, collector);
                evaluateMutatorComponentCountLimit(
                        definition,
                        sourcePath,
                        MAX_COMPONENTS_PER_MUTATOR_BUDGET)
                        .ifPresent(collector::addDiagnostic);
                validateEntityTagSelectors("mob_mutators", definition.id(), sourcePath, definition.eligibleEntityTags(), collector);
                validateEntityTagSelectors("mob_mutators", definition.id(), sourcePath, definition.excludedEntityTags(), collector);
                validateTypedNodes("mob_mutators", definition.id(), sourcePath, "required_conditions", definition.requiredConditions(), true, collector);
                for (int index = 0; index < definition.components().size(); index++) {
                    MutationComponentDefinition component = definition.components().get(index);
                    validateTypedNodes(
                            "mob_mutators",
                            definition.id(),
                            sourcePath,
                            "components[" + index + "].conditions",
                            component.conditions(),
                            true,
                            collector);
                }
                collector.addTrace(
                        WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                        "mob_mutators",
                        definition.id(),
                        sourcePath,
                        "resolved_components=" + joinResourceLocations(definition.components().stream().map(MutationComponentDefinition::type).toList()));
            });

    private static final WorldAwakenedObjectType<MutationPoolDefinition> MUTATION_POOLS = new WorldAwakenedObjectType<>(
            "mutation_pools",
            "mutation_pools",
            MutationPoolDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.mutators().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "mutation_pools",
                            definition.id(),
                            sourcePath,
                            "Mutation pool must include at least one mutator ref",
                            "disabled_object"));
                }
                if (definition.weight() <= 0) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.POOL_SELECTION_IMPOSSIBLE,
                            "mutation_pools",
                            definition.id(),
                            sourcePath,
                            "Mutation pool weight must be > 0",
                            "disabled_object"));
                }
                if (!Double.isFinite(definition.mutationChance())
                        || definition.mutationChance() < 0.0D
                        || definition.mutationChance() > 1.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MUTATION_CHANCE_INVALID,
                            "mutation_pools",
                            definition.id(),
                            sourcePath,
                            "mutation_chance must be a number in range [0.0, 1.0]",
                            "disabled_object"));
                }
                if (!Double.isFinite(definition.additionalMutationChance())
                        || definition.additionalMutationChance() < 0.0D
                        || definition.additionalMutationChance() > 1.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MUTATION_CHANCE_INVALID,
                            "mutation_pools",
                            definition.id(),
                            sourcePath,
                            "additional_mutation_chance must be a number in range [0.0, 1.0]",
                            "disabled_object"));
                }
                if (definition.maxMutatorsPerEntity() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.POOL_SELECTION_IMPOSSIBLE,
                            "mutation_pools",
                            definition.id(),
                            sourcePath,
                            "max_mutators_per_entity must be >= 1",
                            "disabled_object"));
                }
                evaluatePoolMutatorCountLimit(
                        definition,
                        sourcePath,
                        MAX_MUTATORS_PER_SPAWN_BUDGET,
                        Math.max(1, WorldAwakenedCommonConfig.MAX_MUTATORS_PER_MOB.get()))
                        .ifPresent(collector::addDiagnostic);
                validateTypedNodes("mutation_pools", definition.id(), sourcePath, "conditions", definition.conditions(), true, collector);
                validateOptionalApotheosisFilter("mutation_pools", definition.id(), sourcePath, definition.apotheosisTierFilters(), collector);
            });

    private static final WorldAwakenedObjectType<LootProfileDefinition> LOOT_PROFILES = new WorldAwakenedObjectType<>(
            "loot_profiles",
            "loot_profiles",
            LootProfileDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.profileType() == LootProfileType.CHEST && definition.targetLootTables().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "loot_profiles",
                            definition.id(),
                            sourcePath,
                            "Chest loot profile must include target_loot_tables",
                            "disabled_object"));
                }
                if (definition.profileType() == LootProfileType.MOB
                        && definition.targetLootTables().isEmpty()
                        && definition.authoring().targetEntities().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "loot_profiles",
                            definition.id(),
                            sourcePath,
                            "Mob loot profile must include authoring.target_entities or target_loot_tables",
                            "disabled_object"));
                }
                boolean hasBonusXp = definition.authoring().bonusXp().map(value -> value.enabled()).orElse(false);
                if (definition.entries().isEmpty() && !hasBonusXp) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "loot_profiles",
                            definition.id(),
                            sourcePath,
                            "Loot profile must include entries or an enabled mob bonus_xp reward",
                            "disabled_object"));
                }
                if (definition.authoring().itemChance() < 0.0D || definition.authoring().itemChance() > 1.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "loot_profiles",
                            definition.id(),
                            sourcePath,
                            "authoring.item_chance must be between 0 and 1",
                            "disabled_object"));
                }
                definition.authoring().bonusXp().ifPresent(xp -> {
                    if (definition.profileType() == LootProfileType.CHEST) {
                        collector.addDiagnostic(new WorldAwakenedDiagnostic(
                                WorldAwakenedDiagnosticSeverity.ERROR,
                                WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                                "loot_profiles", definition.id(), sourcePath,
                                "bonus_xp is supported only by mob loot profiles", "disabled_object"));
                    }
                    if (xp.minimum() < 0 || xp.maximum() < xp.minimum()
                            || xp.chance() < 0.0D || xp.chance() > 1.0D || xp.multiplier() < 0.0D) {
                        collector.addDiagnostic(new WorldAwakenedDiagnostic(
                                WorldAwakenedDiagnosticSeverity.ERROR,
                                WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                                "loot_profiles", definition.id(), sourcePath,
                                "bonus_xp values are invalid", "disabled_object"));
                    }
                });
                definition.configGate().ifPresent(configGate -> validateConfigGate("loot_profiles", definition.id(), sourcePath, configGate, collector));
                validateTypedNodes("loot_profiles", definition.id(), sourcePath, "conditions", definition.conditions(), true, collector);
                validateTypedNodes("loot_profiles", definition.id(), sourcePath, "mod_conditions", definition.modConditions(), true, collector);
                validateOptionalApotheosisFilter("loot_profiles", definition.id(), sourcePath, definition.apotheosisTierFilters(), collector);
            });

    private static final WorldAwakenedObjectType<InvasionProfileDefinition> INVASION_PROFILES = new WorldAwakenedObjectType<>(
            "invasion_profiles",
            "invasion_profiles",
            InvasionProfileDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.minPlayers() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "min_players must be >= 1",
                            "disabled_object"));
                }
                if (definition.durationSeconds() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "duration_seconds must be >= 1",
                            "disabled_object"));
                }
                if (!Double.isFinite(definition.pressureModifier()) || definition.pressureModifier() <= 0.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVASION_COMPOSITION_INVALID,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "pressure_modifier must be finite and > 0",
                            "disabled_object"));
                }
                if (definition.cooldownSeconds().isPresent() && definition.cooldownSeconds().get() < 0) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVASION_COMPOSITION_INVALID,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "cooldown_seconds must be >= 0 when present",
                            "disabled_object"));
                }
                if (definition.warningSeconds().isPresent() && definition.warningSeconds().get() < 0) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVASION_COMPOSITION_INVALID,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "warning_seconds must be >= 0 when present",
                            "disabled_object"));
                }
                for (int i = 0; i < definition.tags().size(); i++) {
                    if (definition.tags().get(i).isBlank()) {
                        collector.addDiagnostic(new WorldAwakenedDiagnostic(
                                WorldAwakenedDiagnosticSeverity.ERROR,
                                WorldAwakenedDiagnosticCodes.INVASION_COMPOSITION_INVALID,
                                "invasion_profiles",
                                definition.id(),
                                sourcePath,
                                "tags[" + i + "] must not be blank",
                                "disabled_object"));
                    }
                }
                validateTypedNodes("invasion_profiles", definition.id(), sourcePath, "conditions", definition.conditions(), true, collector);
            });

    private static final WorldAwakenedObjectType<IntegrationProfileDefinition> INTEGRATION_PROFILES = new WorldAwakenedObjectType<>(
            "integration_profiles",
            "integration_profiles",
            IntegrationProfileDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.modId().isBlank()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "integration_profiles",
                            definition.id(),
                            sourcePath,
                            "Integration profile mod_id must not be blank",
                            "disabled_object"));
                }
                validateConfigGate("integration_profiles", definition.id(), sourcePath, definition.configKey(), collector);
                validateEntityTagSelectors("integration_profiles", definition.id(), sourcePath, definition.entityTags(), collector);
                validateEntityTagSelectors("integration_profiles", definition.id(), sourcePath, definition.bossTags(), collector);
                validateTypedNodes("integration_profiles", definition.id(), sourcePath, "stage_hooks", definition.stageHooks(), true, collector);
                validateTypedNodes("integration_profiles", definition.id(), sourcePath, "special_conditions", definition.specialConditions(), true, collector);
            });

    private static final WorldAwakenedObjectType<EntityBossFlagMapDefinition> ENTITY_BOSS_FLAGS = new WorldAwakenedObjectType<>(
            "maps/entity_boss_flags",
            "maps/entity_boss_flags",
            EntityBossFlagMapDefinition.CODEC,
            (sourcePath, definition, collector) -> {
            });

    private WorldAwakenedDatapackLoader() {
    }

    public static WorldAwakenedDatapackSnapshot load(ResourceManager resourceManager, Logger logger) {
        WorldAwakenedValidationSummary.Builder collector = new WorldAwakenedValidationSummary.Builder();

        Map<ResourceLocation, StageDefinition> stages = loadType(resourceManager, STAGES, collector, logger);
        Map<ResourceLocation, TriggerRuleDefinition> triggerRules = loadType(resourceManager, TRIGGER_RULES, collector, logger);
        Map<ResourceLocation, RuleDefinition> rules = loadType(resourceManager, RULES, collector, logger);
        Map<ResourceLocation, AscensionRewardDefinition> ascensionRewards = loadType(resourceManager, ASCENSION_REWARDS, collector, logger);
        Map<ResourceLocation, AscensionOfferDefinition> ascensionOffers = loadType(resourceManager, ASCENSION_OFFERS, collector, logger);
        Map<ResourceLocation, MobMutatorDefinition> mobMutators = loadType(resourceManager, MOB_MUTATORS, collector, logger);
        Map<ResourceLocation, MutationPoolDefinition> mutationPools = loadType(resourceManager, MUTATION_POOLS, collector, logger);
        Map<ResourceLocation, LootProfileDefinition> lootProfiles = loadType(resourceManager, LOOT_PROFILES, collector, logger);
        Map<ResourceLocation, InvasionProfileDefinition> invasionProfiles = loadType(resourceManager, INVASION_PROFILES, collector, logger);
        Map<ResourceLocation, IntegrationProfileDefinition> integrationProfiles = loadType(resourceManager, INTEGRATION_PROFILES, collector, logger);
        Map<ResourceLocation, EntityBossFlagMapDefinition> entityBossFlags = loadType(resourceManager, ENTITY_BOSS_FLAGS, collector, logger);
        Map<ResourceLocation, RarityDefinition> rarities = loadType(resourceManager, RARITIES, collector, logger);
        Map<ResourceLocation, ConfiguredEffectDefinition> configuredEffects = loadType(resourceManager, CONFIGURED_EFFECTS, collector, logger);
        Map<ResourceLocation, EffectGenerationPoolDefinition> effectGenerationPools = loadType(resourceManager, EFFECT_GENERATION_POOLS, collector, logger);

        if (!WorldAwakenedConfigService.settings().defaultGameplayEnabled()) {
            stages = filterBundledDefaults(stages, false);
            triggerRules = filterBundledDefaults(triggerRules, false);
            rules = filterBundledDefaults(rules, false);
            ascensionRewards = filterBundledDefaults(ascensionRewards, false);
            ascensionOffers = filterBundledDefaults(ascensionOffers, false);
            mobMutators = filterBundledDefaults(mobMutators, false);
            mutationPools = filterBundledDefaults(mutationPools, false);
            lootProfiles = filterBundledDefaults(lootProfiles, false);
            invasionProfiles = filterBundledDefaults(invasionProfiles, false);
            integrationProfiles = filterBundledDefaults(integrationProfiles, false);
            entityBossFlags = filterBundledDefaults(entityBossFlags, false);
            rarities = filterBundledDefaults(rarities, false);
            configuredEffects = filterBundledDefaults(configuredEffects, false);
            effectGenerationPools = filterBundledDefaults(effectGenerationPools, false);
            WorldAwakenedLog.info(
                    logger,
                    WorldAwakenedLogCategory.DATA_LOAD,
                    "Bundled gameplay profile disabled by general.enable_builtin_gameplay");
        }

        stages = dropConflictingStages(stages, collector);
        triggerRules = dropObjectsMissingStageRefs("trigger_rules", triggerRules, stages, TriggerRuleDefinition::id, rule ->
                mergeStageRefs(extractStageReferences(rule.conditions()), extractStageReferences(rule.actions())), collector);
        rules = dropObjectsMissingStageRefs("rules", rules, stages, RuleDefinition::id, rule ->
                mergeStageRefs(extractStageReferences(rule.conditions()), extractStageReferences(rule.actions())), collector);
        ascensionOffers = dropObjectsMissingStageRefs("ascension_offers", ascensionOffers, stages, AscensionOfferDefinition::id, offer ->
                mergeStageRefs(extractStageReferences(offer.triggerConditions()), extractStageReferences(offer.stageFilters())), collector);
        mutationPools = dropObjectsMissingStageRefs("mutation_pools", mutationPools, stages, MutationPoolDefinition::id, pool ->
                mergeStageRefs(extractStageReferences(pool.conditions()), extractStageReferences(pool.stageFilters())), collector);
        lootProfiles = dropObjectsMissingStageRefs("loot_profiles", lootProfiles, stages, LootProfileDefinition::id, profile ->
                mergeStageRefs(extractStageReferences(profile.conditions()), extractStageReferences(profile.stageFilters())), collector);
        invasionProfiles = dropObjectsMissingStageRefs("invasion_profiles", invasionProfiles, stages, InvasionProfileDefinition::id, profile ->
                mergeStageRefs(extractStageReferences(profile.conditions()), extractStageReferences(profile.stageFilters())), collector);
        validateIntegrationStageHooks(integrationProfiles, WorldAwakenedStageRegistry.from(stages), collector);

        mutationPools = dropInvalidMutationPools(mutationPools, mobMutators, collector);
        ascensionOffers = dropInvalidAscensionOffers(ascensionOffers, ascensionRewards, collector);
        invasionProfiles = dropInvalidInvasionProfiles(invasionProfiles, lootProfiles, collector);
        rarities = dropConflictingRarityThresholds(rarities, collector);
        lootProfiles = dropLootProfilesMissingRarityReferences(lootProfiles, rarities, collector);
        effectGenerationPools = dropInvalidEffectGenerationPools(effectGenerationPools, configuredEffects, collector);
        ascensionOffers = dropAscensionOffersMissingEffectGenerationPools(ascensionOffers, effectGenerationPools, collector);
        mutationPools = dropMutationPoolsMissingEffectGenerationPools(mutationPools, effectGenerationPools, collector);

        WorldAwakenedValidationSummary summary = collector.build();
        if (WorldAwakenedFeatureGates.validationLoggingEnabled()) {
            WorldAwakenedLog.info(logger, WorldAwakenedLogCategory.VALIDATION, "Validation summary: {}", summary.toCompactString());
            for (WorldAwakenedDiagnostic diagnostic : summary.diagnostics()) {
                WorldAwakenedLog.warn(logger, WorldAwakenedLogCategory.VALIDATION, diagnostic.asLogLine());
            }
        }

        return new WorldAwakenedDatapackSnapshot(
                0L,
                Instant.now(),
                new WorldAwakenedCompiledData(
                        Map.copyOf(stages),
                        Map.copyOf(triggerRules),
                        Map.copyOf(rules),
                        Map.copyOf(ascensionRewards),
                        Map.copyOf(ascensionOffers),
                        Map.copyOf(mobMutators),
                        Map.copyOf(mutationPools),
                        Map.copyOf(lootProfiles),
                        Map.copyOf(invasionProfiles),
                        Map.copyOf(integrationProfiles),
                        Map.copyOf(entityBossFlags),
                        Map.copyOf(rarities),
                        Map.copyOf(configuredEffects),
                        Map.copyOf(effectGenerationPools),
                        WorldAwakenedDataDrivenBossClassifier.fromMaps(entityBossFlags.values().stream().toList())),
                summary);
    }

    static <T extends WorldAwakenedDataDefinition> Map<ResourceLocation, T> filterBundledDefaults(
            Map<ResourceLocation, T> definitions,
            boolean enabled) {
        if (enabled || definitions.isEmpty()) {
            return definitions;
        }
        Map<ResourceLocation, T> filtered = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (!isBundledDefaultId(id)) {
                filtered.put(id, definition);
            }
        });
        return Map.copyOf(filtered);
    }

    private static boolean isBundledDefaultId(ResourceLocation id) {
        return id != null
                && "worldawakened".equals(id.getNamespace())
                && id.getPath().startsWith("defaults/");
    }

    private static Map<ResourceLocation, RarityDefinition> dropConflictingRarityThresholds(
            Map<ResourceLocation, RarityDefinition> definitions,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<Integer, List<RarityDefinition>> byThreshold = new LinkedHashMap<>();
        definitions.values().forEach(definition -> byThreshold
                .computeIfAbsent(definition.minimumPower(), ignored -> new ArrayList<>()).add(definition));
        LinkedHashMap<ResourceLocation, RarityDefinition> retained = new LinkedHashMap<>(definitions);
        for (Map.Entry<Integer, List<RarityDefinition>> entry : byThreshold.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            List<RarityDefinition> ordered = entry.getValue().stream()
                    .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList();
            RarityDefinition winner = ordered.getFirst();
            for (int index = 1; index < ordered.size(); index++) {
                RarityDefinition duplicate = ordered.get(index);
                retained.remove(duplicate.id());
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        "rarities", duplicate.id(), "rarities/" + duplicate.id().getPath() + ".json",
                        "minimum_power " + entry.getKey() + " duplicates " + winner.id(), "disabled_object"));
            }
        }
        return Map.copyOf(retained);
    }

    private static <T extends WorldAwakenedDataDefinition> Map<ResourceLocation, T> loadType(
            ResourceManager resourceManager,
            WorldAwakenedObjectType<T> objectType,
            WorldAwakenedValidationSummary.Builder collector,
            Logger logger) {
        FileToIdConverter converter = FileToIdConverter.json(objectType.folder());
        Map<ResourceLocation, Resource> resources = converter.listMatchingResources(resourceManager);
        Map<ResourceLocation, T> loaded = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation fileResourceId = entry.getKey();
            ResourceLocation fileId = converter.fileToId(fileResourceId);
            String sourcePath = fileResourceId.toString();
            collector.addTrace(WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD, objectType.key(), fileId, sourcePath, "load_begin");

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                Optional<String> rawMutationPoolChanceError = validateRawMutationChance(objectType.key(), root);
                if (rawMutationPoolChanceError.isPresent()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MUTATION_CHANCE_INVALID,
                            objectType.key(),
                            fileId,
                            sourcePath,
                            rawMutationPoolChanceError.get(),
                            "disabled_object"));
                    collector.addRejectedTrace(
                            WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                            objectType.key(),
                            fileId,
                            sourcePath,
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "mutation_chance_invalid");
                    collector.incrementDisabled(objectType.key());
                    continue;
                }
                DataResult<T> parsed = objectType.codec().parse(JsonOps.INSTANCE, root);
                T definition = parsed.result().orElse(null);
                if (definition == null) {
                    String message = parsed.error().map(error -> error.message()).orElse("Unknown codec parse error");
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.CODEC_PARSE_FAILED,
                            objectType.key(),
                            fileId,
                            sourcePath,
                            message,
                            "disabled_object"));
                    collector.addRejectedTrace(
                            WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                            objectType.key(),
                            fileId,
                            sourcePath,
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "codec_parse_failed");
                    collector.incrementDisabled(objectType.key());
                    continue;
                }

                if (definition.schemaVersion() > WorldAwakenedJsonCodecs.SUPPORTED_SCHEMA_VERSION) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.SCHEMA_UNSUPPORTED,
                            objectType.key(),
                            definition.id(),
                            sourcePath,
                            "Unsupported schema_version=" + definition.schemaVersion(),
                            "disabled_object"));
                    collector.addRejectedTrace(
                            WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                            objectType.key(),
                            definition.id(),
                            sourcePath,
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "schema_unsupported");
                    collector.incrementDisabled(objectType.key());
                    continue;
                }

                objectType.validator().validate(sourcePath, definition, collector);
                if (!definition.id().equals(fileId)) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.WARNING,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            objectType.key(),
                            definition.id(),
                            sourcePath,
                            "Object id does not match file id (file=" + fileId + ", object=" + definition.id() + ")",
                            "retained_object_id"));
                }

                boolean hasErrors = collector.hasErrorFor(objectType.key(), definition.id(), sourcePath);
                if (hasErrors) {
                    collector.addRejectedTrace(
                            WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                            objectType.key(),
                            definition.id(),
                            sourcePath,
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "validation_failed");
                    collector.incrementDisabled(objectType.key());
                    continue;
                }
                if (!definition.enabled()) {
                    collector.addRejectedTrace(
                            WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                            objectType.key(),
                            definition.id(),
                            sourcePath,
                            WorldAwakenedRejectionReason.FEATURE_DISABLED,
                            "definition_disabled");
                    collector.incrementDisabled(objectType.key());
                    continue;
                }

                if (loaded.containsKey(definition.id())) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.DUPLICATE_ID,
                            objectType.key(),
                            definition.id(),
                            sourcePath,
                            "Duplicate id in object set: " + definition.id(),
                            "replaced_existing"));
                    collector.addRejectedTrace(
                            WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                            objectType.key(),
                            definition.id(),
                            sourcePath,
                            WorldAwakenedRejectionReason.EXCLUSIVE_CONFLICT,
                            "duplicate_id");
                    collector.incrementDisabled(objectType.key());
                }

                loaded.put(definition.id(), definition);
                collector.incrementLoaded(objectType.key());
                collector.addTrace(WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD, objectType.key(), definition.id(), sourcePath, "loaded");
            } catch (Exception exception) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.RELOAD_EXCEPTION,
                        objectType.key(),
                        fileId,
                        sourcePath,
                        exception.getMessage(),
                        "disabled_object"));
                collector.addRejectedTrace(
                        WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                        objectType.key(),
                        fileId,
                        sourcePath,
                        WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                        "reload_exception");
                collector.incrementDisabled(objectType.key());
                WorldAwakenedLog.error(logger, WorldAwakenedLogCategory.DATA_LOAD, "Failed to load {} {}: {}", objectType.key(), fileId, exception.toString());
            }
        }

        return loaded;
    }

    private static void validateAscensionComponents(
            AscensionRewardDefinition definition,
            String sourcePath,
            WorldAwakenedValidationSummary.Builder collector) {
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(definition.components());
        for (WorldAwakenedAscensionComponentValidation.Issue issue : result.issues()) {
            String code = switch (issue.kind()) {
                case EMPTY_COMPONENT_LIST -> WorldAwakenedDiagnosticCodes.COMPONENT_ARRAY_EMPTY;
                case UNKNOWN_COMPONENT_TYPE -> WorldAwakenedDiagnosticCodes.COMPONENT_TYPE_UNKNOWN;
                case INVALID_COMPONENT_PARAMETERS -> WorldAwakenedDiagnosticCodes.COMPONENT_PARAMETERS_INVALID;
                case INCOMPATIBLE_COMPONENT_COMPOSITION -> WorldAwakenedDiagnosticCodes.COMPONENT_COMPOSITION_INVALID;
                case NO_RUNTIME_RESULT -> WorldAwakenedDiagnosticCodes.COMPONENT_NO_RUNTIME_RESULT;
                case DUPLICATE_COMPONENT_TYPE -> WorldAwakenedDiagnosticCodes.COMPONENT_DUPLICATE_UNSUPPORTED;
                case COMPONENT_NOT_SUPPRESSIBLE -> WorldAwakenedDiagnosticCodes.ASC_COMPONENT_NOT_SUPPRESSIBLE;
                case SUPPRESSION_GROUP_REQUIRED -> WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_GROUP_REQUIRED;
                case SUPPRESSION_INVALID_PARTIAL -> WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_INVALID_PARTIAL;
            };
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR,
                    code,
                    "ascension_rewards",
                    definition.id(),
                    sourcePath,
                    issue.detail(),
                    "disabled_object"));
        }
    }

    private static void validateMutationComponents(
            MobMutatorDefinition definition,
            String sourcePath,
            WorldAwakenedValidationSummary.Builder collector) {
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(definition.components());
        for (WorldAwakenedMutationComponentValidation.Issue issue : result.issues()) {
            String code = switch (issue.kind()) {
                case EMPTY_COMPONENT_LIST -> WorldAwakenedDiagnosticCodes.COMPONENT_ARRAY_EMPTY;
                case UNKNOWN_COMPONENT_TYPE -> WorldAwakenedDiagnosticCodes.COMPONENT_TYPE_UNKNOWN;
                case INVALID_COMPONENT_PARAMETERS -> WorldAwakenedDiagnosticCodes.COMPONENT_PARAMETERS_INVALID;
                case INCOMPATIBLE_COMPONENT_COMPOSITION, IMPOSSIBLE_COMPONENT_COMPOSITION -> WorldAwakenedDiagnosticCodes.COMPONENT_COMPOSITION_INVALID;
                case NO_RUNTIME_RESULT -> WorldAwakenedDiagnosticCodes.COMPONENT_NO_RUNTIME_RESULT;
                case DUPLICATE_COMPONENT_TYPE -> WorldAwakenedDiagnosticCodes.COMPONENT_DUPLICATE_UNSUPPORTED;
            };
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR,
                    code,
                    "mob_mutators",
                    definition.id(),
                    sourcePath,
                    issue.detail(),
                    "disabled_object"));
        }
    }

    static void validateIntegrationStageHooks(
            Map<ResourceLocation, IntegrationProfileDefinition> integrationProfiles,
            WorldAwakenedStageRegistry stageRegistry,
            WorldAwakenedValidationSummary.Builder collector) {
        for (IntegrationProfileDefinition definition : integrationProfiles.values()) {
            String sourcePath = definition.id().toString();
            for (int index = 0; index < definition.stageHooks().size(); index++) {
                JsonElement raw = definition.stageHooks().get(index);
                if (!raw.isJsonObject()) {
                    continue;
                }

                JsonObject node = raw.getAsJsonObject();
                JsonElement rawType = node.get("type");
                if (rawType == null || !rawType.isJsonPrimitive()) {
                    continue;
                }

                ResourceLocation typeId = ResourceLocation.tryParse(rawType.getAsString());
                if (typeId == null) {
                    continue;
                }
                if (!"tier_threshold_unlock".equals(typeId.getPath().toLowerCase(Locale.ROOT))) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INTEGRATION_STAGE_HOOK_INVALID,
                            "integration_profiles",
                            definition.id(),
                            sourcePath,
                            "stage_hooks[" + index + "] unsupported type: " + typeId,
                            "disabled_branch"));
                    continue;
                }

                JsonElement rawParameters = node.get("parameters");
                if (rawParameters == null || !rawParameters.isJsonObject()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INTEGRATION_STAGE_HOOK_INVALID,
                            "integration_profiles",
                            definition.id(),
                            sourcePath,
                            "stage_hooks[" + index + "] parameters must be an object",
                            "disabled_branch"));
                    continue;
                }

                JsonObject parameters = rawParameters.getAsJsonObject();
                OptionalDouble threshold = readAnyDouble(parameters, "tier", "threshold", "value", "min", "gte", "at_least");
                if (threshold.isEmpty() || threshold.getAsDouble() < 0.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INTEGRATION_STAGE_HOOK_INVALID,
                            "integration_profiles",
                            definition.id(),
                            sourcePath,
                            "stage_hooks[" + index + "] tier_threshold_unlock requires non-negative numeric tier/threshold",
                            "disabled_branch"));
                }

                ResourceLocation unlockStage = null;
                for (String key : List.of("unlock_stage", "stage", "stage_id")) {
                    JsonElement rawStage = parameters.get(key);
                    if (rawStage == null || !rawStage.isJsonPrimitive()) {
                        continue;
                    }
                    unlockStage = ResourceLocation.tryParse(rawStage.getAsString());
                    if (unlockStage != null) {
                        break;
                    }
                }
                if (unlockStage == null) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INTEGRATION_STAGE_HOOK_INVALID,
                            "integration_profiles",
                            definition.id(),
                            sourcePath,
                            "stage_hooks[" + index + "] tier_threshold_unlock requires parameters.unlock_stage",
                            "disabled_branch"));
                    continue;
                }
                if (stageRegistry.resolveCanonicalId(unlockStage).isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.STAGE_REF_MISSING,
                            "integration_profiles",
                            definition.id(),
                            sourcePath,
                            "stage_hooks[" + index + "] references missing stage " + unlockStage,
                            "disabled_branch"));
                }
            }
        }
    }

    static Optional<WorldAwakenedDiagnostic> evaluateMutatorComponentCountLimit(
            MobMutatorDefinition definition,
            String sourcePath,
            int maxComponentsPerMutator) {
        if (maxComponentsPerMutator < 1) {
            return Optional.empty();
        }
        long enabledComponentCount = definition.components().stream()
                .filter(MutationComponentDefinition::enabled)
                .count();
        if (enabledComponentCount <= maxComponentsPerMutator) {
            return Optional.empty();
        }
        return Optional.of(new WorldAwakenedDiagnostic(
                WorldAwakenedDiagnosticSeverity.WARNING,
                WorldAwakenedDiagnosticCodes.PERF_MUTATOR_COMPONENT_COUNT_EXCEEDED,
                "mob_mutators",
                definition.id(),
                sourcePath,
                "enabled component count " + enabledComponentCount
                        + " exceeds max_components_per_mutator=" + maxComponentsPerMutator,
                "warn_threshold"));
    }

    static Optional<WorldAwakenedDiagnostic> evaluatePoolMutatorCountLimit(
            MutationPoolDefinition definition,
            String sourcePath,
            int maxMutatorsPerSpawn,
            int defaultMaxMutatorsPerEntity) {
        if (maxMutatorsPerSpawn < 1) {
            return Optional.empty();
        }
        int requestedMax = definition.maxMutatorsPerEntity();
        if (requestedMax <= maxMutatorsPerSpawn) {
            return Optional.empty();
        }
        String source = "pool max_mutators_per_entity";
        return Optional.of(new WorldAwakenedDiagnostic(
                WorldAwakenedDiagnosticSeverity.WARNING,
                WorldAwakenedDiagnosticCodes.PERF_MUTATOR_COUNT_EXCEEDED,
                "mutation_pools",
                definition.id(),
                sourcePath,
                "requested mutator cap " + requestedMax
                        + " exceeds max_mutators_per_spawn=" + maxMutatorsPerSpawn
                        + " (" + source + ")",
                "warn_threshold"));
    }

    private static void validateTypedNodes(
            String objectType,
            ResourceLocation objectId,
            String sourcePath,
            String fieldName,
            List<JsonElement> nodes,
            boolean condition,
            WorldAwakenedValidationSummary.Builder collector) {
        validateTypedNodes(
                objectType,
                objectId,
                sourcePath,
                fieldName,
                nodes,
                condition,
                Optional.empty(),
                collector);
    }

    private static void validateTypedNodes(
            String objectType,
            ResourceLocation objectId,
            String sourcePath,
            String fieldName,
            List<JsonElement> nodes,
            boolean condition,
            Optional<String> declaredScope,
            WorldAwakenedValidationSummary.Builder collector) {
        for (int index = 0; index < nodes.size(); index++) {
            JsonElement element = nodes.get(index);
            if (!element.isJsonObject()) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] must be a JSON object",
                        "disabled_object"));
                continue;
            }

            JsonObject node = element.getAsJsonObject();
            if (node.has("enabled")
                    && (!node.get("enabled").isJsonPrimitive() || !node.getAsJsonPrimitive("enabled").isBoolean())) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] enabled must be a boolean",
                        "disabled_object"));
            }
            if (!condition && node.has("priority")
                    && (!node.get("priority").isJsonPrimitive() || !node.getAsJsonPrimitive("priority").isNumber())) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] priority must be numeric",
                        "disabled_object"));
            }
            if (node.has("debug_label")
                    && (!node.get("debug_label").isJsonPrimitive() || !node.getAsJsonPrimitive("debug_label").isString())) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] debug_label must be a string",
                        "disabled_object"));
            }

            JsonElement rawType = node.get("type");
            if (rawType == null || !rawType.isJsonPrimitive()) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] missing type",
                        "disabled_object"));
                continue;
            }

            ResourceLocation typeId = ResourceLocation.tryParse(rawType.getAsString());
            if (typeId == null) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] has invalid type: " + rawType.getAsString(),
                        "disabled_object"));
                continue;
            }

            if (isApotheosisType(typeId) && !WorldAwakenedFeatureGates.apotheosisEnabled()) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] uses Apotheosis while compat.apotheosis.enabled=false",
                        "disabled_object"));
            }

            String path = typeId.getPath().toLowerCase(Locale.ROOT);
            Optional<Set<String>> supportedPaths = supportedTypedPaths(objectType, condition);
            if (supportedPaths.isPresent() && !supportedPaths.get().contains(path)) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] unsupported type: " + typeId,
                        "disabled_object"));
                continue;
            }

            if (declaredScope.isPresent() && !scopeAllowsType(objectType, condition, path, declaredScope.get())) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] type " + typeId + " is invalid for scope " + declaredScope.get(),
                        "disabled_object"));
                continue;
            }

            JsonElement rawParameters = node.get("parameters");
            if (rawParameters == null || !rawParameters.isJsonObject()) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        condition ? WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE : WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                        objectType,
                        objectId,
                        sourcePath,
                        fieldName + "[" + index + "] parameters must be an object",
                        "disabled_object"));
                continue;
            }
            JsonObject parameters = rawParameters.getAsJsonObject();

            if (!condition && "record_unlock_condition".equals(path)) {
                boolean validTokens = parameters.has("group_token") && parameters.get("group_token").isJsonPrimitive()
                        && parameters.has("condition_token") && parameters.get("condition_token").isJsonPrimitive()
                        && parameters.has("condition_tokens") && parameters.get("condition_tokens").isJsonArray()
                        && !parameters.getAsJsonArray("condition_tokens").isEmpty();
                String mode = parameters.has("mode") && parameters.get("mode").isJsonPrimitive()
                        ? parameters.get("mode").getAsString() : "";
                if (!validTokens || !("all".equals(mode) || "any".equals(mode))) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_ACTION_TYPE,
                            objectType,
                            objectId,
                            sourcePath,
                            fieldName + "[" + index + "] record_unlock_condition requires group_token, condition_token, a non-empty condition_tokens array, and mode all|any",
                            "disabled_object"));
                }
            }

            if (condition && "world_day_gte".equals(path)) {
                OptionalDouble threshold = readAnyDouble(parameters, "value");
                if (threshold.isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE,
                            objectType,
                            objectId,
                            sourcePath,
                            fieldName + "[" + index + "] world_day_gte requires numeric parameters.value",
                            "disabled_object"));
                } else if (threshold.getAsDouble() < 0.0D) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE,
                            objectType,
                            objectId,
                            sourcePath,
                            fieldName + "[" + index + "] world_day_gte value must be >= 0",
                            "disabled_object"));
                }
            }
            if (condition && "player_distance_from_spawn".equals(path)) {
                OptionalDouble min = readAnyDouble(parameters, "min");
                OptionalDouble max = readAnyDouble(parameters, "max");
                if (min.isEmpty() && max.isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE,
                            objectType,
                            objectId,
                            sourcePath,
                            fieldName + "[" + index + "] player_distance_from_spawn requires min and/or max",
                            "disabled_object"));
                } else if (min.isPresent() && max.isPresent() && min.getAsDouble() > max.getAsDouble()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE,
                            objectType,
                            objectId,
                            sourcePath,
                            fieldName + "[" + index + "] player_distance_from_spawn requires min <= max",
                            "disabled_object"));
                }
            }
            if (condition && "config_toggle_enabled".equals(path)) {
                if (!parameters.has("config_gate") || !parameters.get("config_gate").isJsonPrimitive()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.CONFIG_GATE_INVALID,
                            objectType,
                            objectId,
                            sourcePath,
                            fieldName + "[" + index + "] config_toggle_enabled requires parameters.config_gate",
                            "disabled_object"));
                } else {
                    validateConfigGate(objectType, objectId, sourcePath, parameters.get("config_gate").getAsString(), collector);
                }
            }
        }
    }

    private static void validateEntityTagSelectors(
            String objectType,
            ResourceLocation objectId,
            String sourcePath,
            List<String> selectors,
            WorldAwakenedValidationSummary.Builder collector) {
        for (String selector : selectors) {
            if (!isValidTagSelector(selector)) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                        objectType,
                        objectId,
                        sourcePath,
                        "Invalid entity tag selector: " + selector,
                        "disabled_object"));
            }
        }
    }

    private static void validateOptionalApotheosisFilter(
            String objectType,
            ResourceLocation objectId,
            String sourcePath,
            Optional<JsonElement> filter,
            WorldAwakenedValidationSummary.Builder collector) {
        if (filter.isPresent() && !WorldAwakenedFeatureGates.apotheosisEnabled()) {
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR,
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    objectType,
                    objectId,
                    sourcePath,
                    "Apotheosis tier filter provided while compat.apotheosis.enabled=false",
                    "disabled_object"));
        }
    }

    private static void validateConfigGate(
            String objectType,
            ResourceLocation objectId,
            String sourcePath,
            String configGate,
            WorldAwakenedValidationSummary.Builder collector) {
        if (configGate == null || configGate.isBlank() || !CONFIG_GATE_PATTERN.matcher(configGate).matches()) {
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR,
                    WorldAwakenedDiagnosticCodes.CONFIG_GATE_INVALID,
                    objectType,
                    objectId,
                    sourcePath,
                    "Invalid config gate: " + configGate,
                    "disabled_object"));
        }
    }

    static Optional<String> validateRawMutationChance(String objectType, JsonElement root) {
        if (!"mutation_pools".equals(objectType)) {
            return Optional.empty();
        }
        if (root == null || !root.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("mutation_chance")) {
            return Optional.empty();
        }
        JsonElement rawChance = object.get("mutation_chance");
        if (!rawChance.isJsonPrimitive() || !rawChance.getAsJsonPrimitive().isNumber()) {
            return Optional.of("mutation_chance must be a number in range [0.0, 1.0]");
        }
        double chance = rawChance.getAsDouble();
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            return Optional.of("mutation_chance must be in range [0.0, 1.0]");
        }
        return Optional.empty();
    }

    private static void validateIconReference(
            String objectType,
            ResourceLocation objectId,
            String sourcePath,
            Optional<JsonElement> icon,
            WorldAwakenedValidationSummary.Builder collector) {
        if (icon.isPresent() && icon.get().isJsonPrimitive() && icon.get().getAsString().isBlank()) {
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR,
                    WorldAwakenedDiagnosticCodes.ASCENSION_REWARD_INVALID,
                    objectType,
                    objectId,
                    sourcePath,
                    "icon cannot be blank",
                    "disabled_object"));
        }
    }

    static Map<ResourceLocation, StageDefinition> dropConflictingStages(
            Map<ResourceLocation, StageDefinition> stages,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<String, List<StageDefinition>> byGroup = new LinkedHashMap<>();
        for (StageDefinition stage : stages.values()) {
            stage.progressionGroup().ifPresent(group -> byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(stage));
        }

        Set<ResourceLocation> blocked = new LinkedHashSet<>();
        Map<ResourceLocation, List<StageDefinition>> aliasOwners = new LinkedHashMap<>();
        for (StageDefinition stage : stages.values()) {
            Set<ResourceLocation> declared = new LinkedHashSet<>();
            for (ResourceLocation alias : stage.aliases()) {
                if (alias == null || !declared.add(alias) || alias.equals(stage.id()) || stages.containsKey(alias)) {
                    blockStageForAliasConflict(stage, alias, collector, blocked);
                    continue;
                }
                aliasOwners.computeIfAbsent(alias, ignored -> new ArrayList<>()).add(stage);
            }
        }
        for (Map.Entry<ResourceLocation, List<StageDefinition>> entry : aliasOwners.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (StageDefinition stage : entry.getValue()) {
                    blockStageForAliasConflict(stage, entry.getKey(), collector, blocked);
                }
            }
        }
        for (Map.Entry<String, List<StageDefinition>> entry : byGroup.entrySet()) {
            List<StageDefinition> group = entry.getValue().stream()
                    .filter(stage -> stage.unlockPolicy() == StageUnlockPolicy.EXCLUSIVE_GROUP || stage.unlockPolicy() == StageUnlockPolicy.REPLACE_GROUP)
                    .filter(StageDefinition::defaultUnlocked)
                    .toList();
            if (group.size() > 1) {
                for (StageDefinition stage : group) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "stages",
                            stage.id(),
                            null,
                            "Exclusive progression group has multiple default_unlocked stages: " + entry.getKey(),
                            "disabled_object"));
                    collector.addRejectedTrace(
                            WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                            "stages",
                            stage.id(),
                            null,
                            WorldAwakenedRejectionReason.EXCLUSIVE_CONFLICT,
                            "exclusive_group_default_conflict");
                    collector.incrementDisabled("stages");
                    blocked.add(stage.id());
                }
            }
        }

        if (blocked.isEmpty()) {
            return stages;
        }
        Map<ResourceLocation, StageDefinition> filtered = new LinkedHashMap<>();
        for (StageDefinition stage : stages.values()) {
            if (!blocked.contains(stage.id())) {
                filtered.put(stage.id(), stage);
            }
        }
        return filtered;
    }

    private static void blockStageForAliasConflict(
            StageDefinition stage,
            ResourceLocation alias,
            WorldAwakenedValidationSummary.Builder collector,
            Set<ResourceLocation> blocked) {
        if (!blocked.add(stage.id())) {
            return;
        }
        collector.addDiagnostic(new WorldAwakenedDiagnostic(
                WorldAwakenedDiagnosticSeverity.ERROR,
                WorldAwakenedDiagnosticCodes.STAGE_ALIAS_CONFLICT,
                "stages",
                stage.id(),
                null,
                "Stage alias must be unique and must not match a canonical stage ID: " + alias,
                "disabled_object"));
        collector.addRejectedTrace(
                WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                "stages",
                stage.id(),
                null,
                WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                "stage_alias_conflict");
        collector.incrementDisabled("stages");
    }

    static <T extends WorldAwakenedDataDefinition> Map<ResourceLocation, T> dropObjectsMissingStageRefs(
            String objectType,
            Map<ResourceLocation, T> objects,
            Map<ResourceLocation, StageDefinition> stages,
            Function<T, ResourceLocation> idGetter,
            Function<T, List<ResourceLocation>> stageRefExtractor,
            WorldAwakenedValidationSummary.Builder collector) {
        WorldAwakenedStageRegistry stageRegistry = WorldAwakenedStageRegistry.from(stages);
        Map<ResourceLocation, T> filtered = new LinkedHashMap<>();
        for (T definition : objects.values()) {
            boolean invalid = false;
            ResourceLocation objectId = idGetter.apply(definition);
            for (ResourceLocation stageRef : stageRefExtractor.apply(definition)) {
                if (stageRegistry.resolveCanonicalId(stageRef).isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.STAGE_REF_MISSING,
                            objectType,
                            objectId,
                            null,
                            "Missing stage reference: " + stageRef,
                            "disabled_object"));
                    invalid = true;
                }
            }
            if (invalid) {
                collector.addRejectedTrace(
                        WorldAwakenedRuntimeLayer.STATIC_DATA_LOAD,
                        objectType,
                        objectId,
                        null,
                        WorldAwakenedRejectionReason.STAGE_CONDITION_FAILED,
                        "missing_stage_reference");
                collector.incrementDisabled(objectType);
            } else {
                filtered.put(objectId, definition);
            }
        }
        return filtered;
    }

    private static Map<ResourceLocation, MutationPoolDefinition> dropInvalidMutationPools(
            Map<ResourceLocation, MutationPoolDefinition> mutationPools,
            Map<ResourceLocation, MobMutatorDefinition> mutators,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, MutationPoolDefinition> filtered = new LinkedHashMap<>();
        for (MutationPoolDefinition pool : mutationPools.values()) {
            List<String> errors = WorldAwakenedMutationPoolValidation.validate(pool, mutators);
            boolean invalid = !errors.isEmpty();
            for (String error : errors) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        error.contains("exclusive") || error.contains("stacking group")
                                ? WorldAwakenedDiagnosticCodes.COMPONENT_COMPOSITION_INVALID
                                : WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        "mutation_pools",
                        pool.id(),
                        null,
                        error,
                        "pool_disabled"));
            }
            if (invalid) {
                collector.incrementDisabled("mutation_pools");
            } else {
                filtered.put(pool.id(), pool);
            }
        }
        return filtered;
    }

    private static Map<ResourceLocation, AscensionOfferDefinition> dropInvalidAscensionOffers(
            Map<ResourceLocation, AscensionOfferDefinition> offers,
            Map<ResourceLocation, AscensionRewardDefinition> rewards,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, AscensionOfferDefinition> filtered = new LinkedHashMap<>();
        for (AscensionOfferDefinition offer : offers.values()) {
            boolean invalid = false;
            for (ResourceLocation rewardId : offer.candidateRewards()) {
                if (!rewards.containsKey(rewardId)) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "ascension_offers",
                            offer.id(),
                            null,
                            "Missing ascension reward reference: " + rewardId,
                            "offer_disabled"));
                    invalid = true;
                }
            }
            if (invalid) {
                collector.incrementDisabled("ascension_offers");
            } else {
                filtered.put(offer.id(), offer);
            }
        }
        return filtered;
    }

    private static Map<ResourceLocation, EffectGenerationPoolDefinition> dropInvalidEffectGenerationPools(
            Map<ResourceLocation, EffectGenerationPoolDefinition> pools,
            Map<ResourceLocation, ConfiguredEffectDefinition> effects,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, EffectGenerationPoolDefinition> filtered = new LinkedHashMap<>();
        for (EffectGenerationPoolDefinition pool : pools.values()) {
            boolean invalid = false;
            for (ResourceLocation effectId : pool.configuredEffects()) {
                ConfiguredEffectDefinition effect = effects.get(effectId);
                if (effect == null) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "effect_generation_pools", pool.id(), null,
                            "Missing configured effect reference: " + effectId, "pool_disabled"));
                    invalid = true;
                } else if (effect.scope() != pool.scope()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "effect_generation_pools", pool.id(), null,
                            "Configured effect scope does not match pool scope: " + effectId, "pool_disabled"));
                    invalid = true;
                }
            }
            if (invalid) {
                collector.incrementDisabled("effect_generation_pools");
            } else {
                filtered.put(pool.id(), pool);
            }
        }
        return Map.copyOf(filtered);
    }

    private static Map<ResourceLocation, AscensionOfferDefinition> dropAscensionOffersMissingEffectGenerationPools(
            Map<ResourceLocation, AscensionOfferDefinition> offers,
            Map<ResourceLocation, EffectGenerationPoolDefinition> generationPools,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, AscensionOfferDefinition> filtered = new LinkedHashMap<>();
        for (AscensionOfferDefinition offer : offers.values()) {
            Optional<ResourceLocation> reference = offer.effectGenerationPool();
            if (reference.isEmpty()) {
                filtered.put(offer.id(), offer);
                continue;
            }
            EffectGenerationPoolDefinition pool = generationPools.get(reference.get());
            if (pool != null && pool.scope() == WorldAwakenedEffectScope.ASCENSION) {
                filtered.put(offer.id(), offer);
                continue;
            }
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                    "ascension_offers", offer.id(), null,
                    "Missing or non-ascension effect_generation_pool: " + reference.get(), "offer_disabled"));
            collector.incrementDisabled("ascension_offers");
        }
        return Map.copyOf(filtered);
    }

    private static Map<ResourceLocation, MutationPoolDefinition> dropMutationPoolsMissingEffectGenerationPools(
            Map<ResourceLocation, MutationPoolDefinition> pools,
            Map<ResourceLocation, EffectGenerationPoolDefinition> generationPools,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, MutationPoolDefinition> filtered = new LinkedHashMap<>();
        for (MutationPoolDefinition pool : pools.values()) {
            Optional<ResourceLocation> reference = pool.effectGenerationPool();
            if (reference.isEmpty()) {
                filtered.put(pool.id(), pool);
                continue;
            }
            EffectGenerationPoolDefinition generationPool = generationPools.get(reference.get());
            if (generationPool != null && generationPool.scope() == WorldAwakenedEffectScope.MOB_MUTATION) {
                filtered.put(pool.id(), pool);
                continue;
            }
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                    "mutation_pools", pool.id(), null,
                    "effect_generation_pool must reference a mob_mutation generation pool: " + reference.get(),
                    "pool_disabled"));
            collector.incrementDisabled("mutation_pools");
        }
        return Map.copyOf(filtered);
    }

    /**
     * Rarity filters are deliberately eligibility-only at runtime, but their IDs still need the
     * same reload-time safety as every other cross-object reference.  A restrictive filter must
     * never quietly degrade into an unrestricted profile because a pack typo was ignored.
     */
    private static Map<ResourceLocation, LootProfileDefinition> dropLootProfilesMissingRarityReferences(
            Map<ResourceLocation, LootProfileDefinition> profiles,
            Map<ResourceLocation, RarityDefinition> rarities,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, LootProfileDefinition> filtered = new LinkedHashMap<>();
        for (LootProfileDefinition profile : profiles.values()) {
            List<ResourceLocation> references = extractRarityReferences(profile.mutationRarityFilters());
            ResourceLocation missing = references.stream()
                    .filter(reference -> !rarities.containsKey(reference) || !rarities.get(reference).enabled())
                    .findFirst()
                    .orElse(null);
            if (missing == null) {
                filtered.put(profile.id(), profile);
                continue;
            }
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR, WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                    "loot_profiles", profile.id(), null,
                    "Missing mutation_rarity_filters rarity reference: " + missing, "profile_disabled"));
            collector.incrementDisabled("loot_profiles");
        }
        return Map.copyOf(filtered);
    }

    private static Map<ResourceLocation, InvasionProfileDefinition> dropInvalidInvasionProfiles(
            Map<ResourceLocation, InvasionProfileDefinition> invasionProfiles,
            Map<ResourceLocation, LootProfileDefinition> lootProfiles,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, InvasionProfileDefinition> filtered = new LinkedHashMap<>();
        for (InvasionProfileDefinition profile : invasionProfiles.values()) {
            boolean invalid = false;
            if (profile.rewardProfile().isPresent() && !lootProfiles.containsKey(profile.rewardProfile().get())) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        "invasion_profiles",
                        profile.id(),
                        null,
                        "Missing reward_profile reference: " + profile.rewardProfile().get(),
                        "profile_disabled"));
                invalid = true;
            }
            if (invalid) {
                collector.incrementDisabled("invasion_profiles");
            } else {
                filtered.put(profile.id(), profile);
            }
        }
        return filtered;
    }

    private static List<ResourceLocation> extractMutatorRefs(List<JsonElement> entries) {
        List<ResourceLocation> refs = new ArrayList<>();
        for (JsonElement element : entries) {
            if (element.isJsonPrimitive()) {
                ResourceLocation mutatorId = ResourceLocation.tryParse(element.getAsString());
                if (mutatorId != null) {
                    refs.add(mutatorId);
                }
            } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                ResourceLocation mutatorId = ResourceLocation.tryParse(element.getAsJsonObject().get("id").getAsString());
                if (mutatorId != null) {
                    refs.add(mutatorId);
                }
            }
        }
        return refs;
    }

    private static boolean isValidTagSelector(String selector) {
        if (selector == null || selector.isBlank()) {
            return false;
        }
        String normalized = selector.startsWith("#") ? selector.substring(1) : selector;
        if (normalized.endsWith(":*")) {
            String namespace = normalized.substring(0, normalized.length() - 2);
            return ResourceLocation.tryParse(namespace + ":placeholder") != null;
        }
        return ResourceLocation.tryParse(normalized) != null;
    }

    private static boolean isApotheosisType(ResourceLocation typeId) {
        String namespace = typeId.getNamespace();
        String path = typeId.getPath();
        return "apotheosis".equals(namespace)
                || namespace.contains("apotheosis")
                || path.contains("apotheosis")
                || path.contains("world_tier");
    }

    private static Optional<Set<String>> supportedTypedPaths(String objectType, boolean condition) {
        if ("rules".equals(objectType)) {
            return Optional.of(condition
                    ? WorldAwakenedRuleEngine.supportedConditionPaths()
                    : WorldAwakenedRuleEngine.supportedActionPaths());
        }
        if ("trigger_rules".equals(objectType)) {
            return Optional.of(condition ? SUPPORTED_TRIGGER_CONDITION_PATHS : SUPPORTED_TRIGGER_ACTION_PATHS);
        }
        if (condition && ("mob_mutators".equals(objectType) || "ascension_rewards".equals(objectType))) {
            return Optional.of(WorldAwakenedRuleEngine.supportedConditionPaths());
        }
        return Optional.empty();
    }

    private static boolean scopeAllowsType(String objectType, boolean condition, String path, String scope) {
        String normalizedScope = scope.toLowerCase(Locale.ROOT);
        if ("rules".equals(objectType)) {
            return condition
                    ? ruleConditionAllowsScope(path, normalizedScope)
                    : ruleActionAllowsScope(path, normalizedScope);
        }
        if ("trigger_rules".equals(objectType)) {
            return condition
                    ? triggerConditionAllowsScope(path, normalizedScope)
                    : triggerActionAllowsScope(path, normalizedScope);
        }
        return true;
    }

    private static boolean ruleConditionAllowsScope(String path, String scope) {
        return switch (path) {
            case "stage_unlocked",
                    "stage_locked",
                    "current_dimension",
                    "world_day_gte",
                    "moon_phase",
                    "loaded_mod",
                    "config_toggle_enabled",
                    "apotheosis_world_tier_compare",
                    "random_chance",
                    "invasion_active" -> isOneOf(scope, "world", "player", "entity", "spawn_event");
            case "invasion_tag" -> "spawn_event".equals(scope);
            case "current_biome" -> isOneOf(scope, "player", "entity", "spawn_event");
            case "player_distance_from_spawn" -> isOneOf(scope, "player", "entity", "spawn_event");
            case "player_count_online" -> "world".equals(scope);
            case "ascension_reward_owned" -> isOneOf(scope, "player", "entity", "spawn_event");
            case "ascension_offer_pending" -> "player".equals(scope);
            case "entity_type", "entity_tag", "entity_not_boss", "entity_is_mutated" -> isOneOf(scope, "entity", "spawn_event");
            case "structure_context" -> "spawn_event".equals(scope);
            default -> true;
        };
    }

    private static boolean ruleActionAllowsScope(String path, String scope) {
        return switch (path) {
            case "unlock_stage", "lock_stage" -> isOneOf(scope, "world", "player");
            case "grant_ascension_offer" -> "player".equals(scope);
            case "set_world_scalar" -> "world".equals(scope);
            case "set_temp_invasion_modifier", "trigger_invasion_profile" -> "world".equals(scope);
            case "apply_mutator_pool", "apply_stat_profile" -> isOneOf(scope, "entity", "spawn_event");
            case "inject_loot_profile" -> "spawn_event".equals(scope);
            case "drop_reward_table" -> isOneOf(scope, "entity", "spawn_event");
            case "send_warning_message" -> isOneOf(scope, "world", "player");
            case "mark_rule_consumed" -> isOneOf(scope, "world", "player", "entity");
            default -> true;
        };
    }

    private static boolean triggerConditionAllowsScope(String path, String scope) {
        return switch (path) {
            case "stage_unlocked",
                    "stage_locked",
                    "current_dimension",
                    "world_day_gte",
                    "advancement_completed",
                    "entity_type",
                    "entity_tag",
                    "manual_trigger",
                    "boss_killed" -> isOneOf(scope, "world", "player");
            default -> true;
        };
    }

    private static boolean triggerActionAllowsScope(String path, String scope) {
        return switch (path) {
            case "unlock_stage", "lock_stage", "increment_counter", "record_unlock_condition", "emit_named_event" -> isOneOf(scope, "world", "player");
            case "send_warning_message", "grant_ascension_offer" -> "player".equals(scope);
            default -> true;
        };
    }

    private static boolean isOneOf(String value, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String joinResourceLocations(List<ResourceLocation> ids) {
        if (ids.isEmpty()) {
            return "<none>";
        }
        return ids.stream().map(ResourceLocation::toString).reduce((left, right) -> left + "," + right).orElse("<none>");
    }

    private static OptionalDouble readAnyDouble(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
                return OptionalDouble.of(object.get(key).getAsDouble());
            }
        }
        return OptionalDouble.empty();
    }

    private static List<ResourceLocation> mergeStageRefs(List<ResourceLocation> first, List<ResourceLocation> second) {
        LinkedHashSet<ResourceLocation> merged = new LinkedHashSet<>();
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static List<ResourceLocation> extractStageReferences(List<JsonElement> elements) {
        List<ResourceLocation> refs = new ArrayList<>();
        for (JsonElement element : elements) {
            collectStageReferences(element, null, false, refs);
        }
        return refs;
    }

    private static List<ResourceLocation> extractStageReferences(Optional<JsonElement> element) {
        if (element.isEmpty()) {
            return List.of();
        }
        List<ResourceLocation> refs = new ArrayList<>();
        collectStageReferences(element.get(), null, false, refs);
        return refs;
    }

    private static List<ResourceLocation> extractRarityReferences(Optional<JsonElement> element) {
        if (element.isEmpty()) return List.of();
        LinkedHashSet<ResourceLocation> references = new LinkedHashSet<>();
        collectRarityReferences(element.get(), false, references);
        return List.copyOf(references);
    }

    private static void collectRarityReferences(JsonElement element, boolean rarityContext, Set<ResourceLocation> references) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                boolean nextContext = rarityContext || key.equals("rarity") || key.equals("rarities")
                        || key.equals("any_of") || key.equals("one_of") || key.equals("none_of") || key.equals("excluded")
                        || key.equals("minimum_rarity") || key.equals("min_rarity") || key.equals("minimum");
                collectRarityReferences(entry.getValue(), nextContext, references);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectRarityReferences(child, rarityContext, references);
            return;
        }
        if (rarityContext && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) references.add(id);
        }
    }

    private static void collectStageReferences(
            JsonElement element,
            String currentKey,
            boolean stageContext,
            List<ResourceLocation> refs) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        boolean nextStageContext = stageContext;
        if (currentKey != null) {
            String normalized = currentKey.toLowerCase(Locale.ROOT);
            nextStageContext = stageContext || STAGE_CONTEXT_KEYS.contains(normalized) || normalized.contains("stage");
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectStageReferences(entry.getValue(), entry.getKey(), nextStageContext, refs);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectStageReferences(child, currentKey, nextStageContext, refs);
            }
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && nextStageContext) {
            ResourceLocation maybeId = ResourceLocation.tryParse(element.getAsString());
            if (maybeId != null) {
                refs.add(maybeId);
            }
        }
    }
}

