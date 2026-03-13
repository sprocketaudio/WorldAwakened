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
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
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
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

public final class WorldAwakenedDatapackLoader {
    private static final Pattern CONFIG_GATE_PATTERN = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+)*$");
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
                if (definition.candidateRewards().isEmpty() && definition.candidateRewardTags().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.ASCENSION_REWARD_INVALID,
                            "ascension_offers",
                            definition.id(),
                            sourcePath,
                            "Offer must include candidate_rewards or candidate_reward_tags",
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
                if (definition.componentBudget().isPresent() && definition.componentBudget().get() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.COMPONENT_BUDGET_EXCEEDED,
                            "mob_mutators",
                            definition.id(),
                            sourcePath,
                            "component_budget must be >= 1",
                            "disabled_object"));
                }
                validateMutationComponents(definition, sourcePath, collector);
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
                if (definition.maxMutatorsPerEntity().isPresent() && definition.maxMutatorsPerEntity().get() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.POOL_SELECTION_IMPOSSIBLE,
                            "mutation_pools",
                            definition.id(),
                            sourcePath,
                            "max_mutators_per_entity must be >= 1",
                            "disabled_object"));
                }
                validateTypedNodes("mutation_pools", definition.id(), sourcePath, "conditions", definition.conditions(), true, collector);
                validateOptionalApotheosisFilter("mutation_pools", definition.id(), sourcePath, definition.apotheosisTierFilters(), collector);
            });

    private static final WorldAwakenedObjectType<LootProfileDefinition> LOOT_PROFILES = new WorldAwakenedObjectType<>(
            "loot_profiles",
            "loot_profiles",
            LootProfileDefinition.CODEC,
            (sourcePath, definition, collector) -> {
                if (definition.targetLootTables().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "loot_profiles",
                            definition.id(),
                            sourcePath,
                            "Loot profile must include target_loot_tables",
                            "disabled_object"));
                }
                if (definition.entries().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "loot_profiles",
                            definition.id(),
                            sourcePath,
                            "Loot profile must include entries",
                            "disabled_object"));
                }
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
                if (definition.spawnComposition().isEmpty()) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "Invasion profile must include spawn_composition entries",
                            "disabled_object"));
                }
                if (definition.waveCount() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "wave_count must be >= 1",
                            "disabled_object"));
                }
                if (definition.spawnBudget() < 1) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.MISSING_REQUIRED_FIELD,
                            "invasion_profiles",
                            definition.id(),
                            sourcePath,
                            "spawn_budget must be >= 1",
                            "disabled_object"));
                }
                validateInvasionCompositionEntries(definition, sourcePath, collector);
                validateTypedNodes("invasion_profiles", definition.id(), sourcePath, "conditions", definition.conditions(), true, collector);
                validateOptionalApotheosisFilter("invasion_profiles", definition.id(), sourcePath, definition.apotheosisTierFilters(), collector);
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

        mutationPools = dropInvalidMutationPools(mutationPools, mobMutators, collector);
        ascensionOffers = dropInvalidAscensionOffers(ascensionOffers, ascensionRewards, collector);
        invasionProfiles = dropInvalidInvasionProfiles(invasionProfiles, lootProfiles, mutationPools, collector);

        WorldAwakenedValidationSummary summary = collector.build();
        if (WorldAwakenedFeatureGates.validationLoggingEnabled()) {
            WorldAwakenedLog.info(logger, WorldAwakenedLogCategory.VALIDATION, "Validation summary: {}", summary.toCompactString());
            for (WorldAwakenedDiagnostic diagnostic : summary.diagnostics()) {
                WorldAwakenedLog.warn(logger, WorldAwakenedLogCategory.VALIDATION, diagnostic.asLogLine());
            }
        }

        return new WorldAwakenedDatapackSnapshot(
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
                        WorldAwakenedDataDrivenBossClassifier.fromMaps(entityBossFlags.values().stream().toList())),
                summary);
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
                WorldAwakenedMutationComponentValidation.validate(definition.components(), definition.componentBudget());
        for (WorldAwakenedMutationComponentValidation.Issue issue : result.issues()) {
            String code = switch (issue.kind()) {
                case EMPTY_COMPONENT_LIST -> WorldAwakenedDiagnosticCodes.COMPONENT_ARRAY_EMPTY;
                case UNKNOWN_COMPONENT_TYPE -> WorldAwakenedDiagnosticCodes.COMPONENT_TYPE_UNKNOWN;
                case INVALID_COMPONENT_PARAMETERS -> WorldAwakenedDiagnosticCodes.COMPONENT_PARAMETERS_INVALID;
                case INCOMPATIBLE_COMPONENT_COMPOSITION, IMPOSSIBLE_COMPONENT_COMPOSITION -> WorldAwakenedDiagnosticCodes.COMPONENT_COMPOSITION_INVALID;
                case NO_RUNTIME_RESULT -> WorldAwakenedDiagnosticCodes.COMPONENT_NO_RUNTIME_RESULT;
                case COMPONENT_BUDGET_EXCEEDED -> WorldAwakenedDiagnosticCodes.COMPONENT_BUDGET_EXCEEDED;
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

    private static void validateInvasionCompositionEntries(
            InvasionProfileDefinition definition,
            String sourcePath,
            WorldAwakenedValidationSummary.Builder collector) {
        int valid = 0;
        for (int i = 0; i < definition.spawnComposition().size(); i++) {
            JsonElement entry = definition.spawnComposition().get(i);
            if (!entry.isJsonObject()) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.INVASION_COMPOSITION_INVALID,
                        "invasion_profiles",
                        definition.id(),
                        sourcePath,
                        "spawn_composition[" + i + "] must be an object",
                        "disabled_object"));
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            boolean hasSelector = object.has("entity") || object.has("entity_tag") || object.has("entities") || object.has("selector");
            if (!hasSelector) {
                collector.addDiagnostic(new WorldAwakenedDiagnostic(
                        WorldAwakenedDiagnosticSeverity.ERROR,
                        WorldAwakenedDiagnosticCodes.INVASION_COMPOSITION_INVALID,
                        "invasion_profiles",
                        definition.id(),
                        sourcePath,
                        "spawn_composition[" + i + "] requires entity/entity_tag/entities/selector",
                        "disabled_object"));
                continue;
            }
            valid++;
        }
        if (valid == 0) {
            collector.addDiagnostic(new WorldAwakenedDiagnostic(
                    WorldAwakenedDiagnosticSeverity.ERROR,
                    WorldAwakenedDiagnosticCodes.INVASION_COMPOSITION_INVALID,
                    "invasion_profiles",
                    definition.id(),
                    sourcePath,
                    "Invasion profile has zero valid spawn_composition entries",
                    "disabled_object"));
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

    private static Map<ResourceLocation, StageDefinition> dropConflictingStages(
            Map<ResourceLocation, StageDefinition> stages,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<String, List<StageDefinition>> byGroup = new LinkedHashMap<>();
        for (StageDefinition stage : stages.values()) {
            stage.progressionGroup().ifPresent(group -> byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(stage));
        }

        Set<ResourceLocation> blocked = new LinkedHashSet<>();
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

    private static <T extends WorldAwakenedDataDefinition> Map<ResourceLocation, T> dropObjectsMissingStageRefs(
            String objectType,
            Map<ResourceLocation, T> objects,
            Map<ResourceLocation, StageDefinition> stages,
            Function<T, ResourceLocation> idGetter,
            Function<T, List<ResourceLocation>> stageRefExtractor,
            WorldAwakenedValidationSummary.Builder collector) {
        Map<ResourceLocation, T> filtered = new LinkedHashMap<>();
        for (T definition : objects.values()) {
            boolean invalid = false;
            ResourceLocation objectId = idGetter.apply(definition);
            for (ResourceLocation stageRef : stageRefExtractor.apply(definition)) {
                if (!stages.containsKey(stageRef)) {
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
            boolean invalid = false;
            for (ResourceLocation mutatorId : extractMutatorRefs(pool.mutators())) {
                if (!mutators.containsKey(mutatorId)) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "mutation_pools",
                            pool.id(),
                            null,
                            "Missing mutator reference: " + mutatorId,
                            "pool_disabled"));
                    invalid = true;
                }
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

    private static Map<ResourceLocation, InvasionProfileDefinition> dropInvalidInvasionProfiles(
            Map<ResourceLocation, InvasionProfileDefinition> invasionProfiles,
            Map<ResourceLocation, LootProfileDefinition> lootProfiles,
            Map<ResourceLocation, MutationPoolDefinition> mutationPools,
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
            for (ResourceLocation poolId : profile.mutatorPoolRefs()) {
                if (!mutationPools.containsKey(poolId)) {
                    collector.addDiagnostic(new WorldAwakenedDiagnostic(
                            WorldAwakenedDiagnosticSeverity.ERROR,
                            WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                            "invasion_profiles",
                            profile.id(),
                            null,
                            "Missing mutator_pool_refs reference: " + poolId,
                            "profile_disabled"));
                    invalid = true;
                }
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
            case "unlock_stage", "lock_stage", "increment_counter", "emit_named_event" -> isOneOf(scope, "world", "player");
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

