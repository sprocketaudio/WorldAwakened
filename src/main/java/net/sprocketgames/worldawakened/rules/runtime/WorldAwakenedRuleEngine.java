package net.sprocketgames.worldawakened.rules.runtime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SplittableRandom;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.ExecutionScope;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
public final class WorldAwakenedRuleEngine {
    private static final Comparator<CompiledRule> PRIORITY_ORDER = Comparator
            .comparingInt(CompiledRule::priority)
            .reversed()
            .thenComparing(rule -> rule.id().toString());
    private static final Comparator<CompiledAction> ACTION_ORDER = Comparator
            .comparingInt(CompiledAction::priority)
            .reversed()
            .thenComparingInt(CompiledAction::authoredIndex);
    private static final Set<String> SUPPORTED_CONDITION_PATHS = Set.of(
            "stage_unlocked",
            "stage_locked",
            "ascension_reward_owned",
            "ascension_offer_pending",
            "current_dimension",
            "current_biome",
            "world_day_gte",
            "player_distance_from_spawn",
            "loaded_mod",
            "config_toggle_enabled",
            "entity_type",
            "entity_tag",
            "entity_not_boss",
            "entity_is_mutated",
            "player_count_online",
            "apotheosis_world_tier_compare",
            "random_chance",
            "moon_phase",
            "structure_context",
            "invasion_active");
    private static final Set<String> SUPPORTED_ACTION_PATHS = Set.of(
            "unlock_stage",
            "lock_stage",
            "grant_ascension_offer",
            "apply_mutator_pool",
            "apply_stat_profile",
            "inject_loot_profile",
            "trigger_invasion_profile",
            "send_warning_message",
            "drop_reward_table",
            "mark_rule_consumed",
            "set_world_scalar",
            "set_temp_invasion_modifier");
    private WorldAwakenedRuleEngine() {
    }
    public static Set<String> supportedConditionPaths() {
        return SUPPORTED_CONDITION_PATHS;
    }
    public static Set<String> supportedActionPaths() {
        return SUPPORTED_ACTION_PATHS;
    }
    public static List<CompiledRule> compile(Collection<RuleDefinition> rules) {
        return rules.stream()
                .filter(RuleDefinition::enabled)
                .map(WorldAwakenedRuleEngine::compileRule)
                .sorted(PRIORITY_ORDER)
                .toList();
    }
    public static WorldAwakenedRuleEvaluation evaluate(
            Collection<CompiledRule> compiledRules,
            WorldAwakenedStageRegistry stageRegistry,
            WorldAwakenedRuleMatchContext context,
            boolean applyChance) {
        List<CompiledRule> ordered = compiledRules.stream()
                .filter(rule -> context.targetedRuleId().map(id -> id.equals(rule.id())).orElse(true))
                .sorted(PRIORITY_ORDER)
                .toList();
        List<WorldAwakenedRuleDecision> decisions = new ArrayList<>(ordered.size());
        List<WorldAwakenedMatchedRule> matched = new ArrayList<>();
        for (CompiledRule rule : ordered) {
            ScopeResolution scopeResolution = resolveScope(rule, context);
            if (!scopeResolution.valid()) {
                decisions.add(new WorldAwakenedRuleDecision(
                        rule,
                        false,
                        Optional.of(scopeResolution.rejectionReason()),
                        scopeResolution.detail(),
                        scopeResolution.stateKey(),
                        OptionalDouble.empty()));
                continue;
            }
            Optional<ConditionFailure> conditionFailure = evaluateConditions(rule, context, stageRegistry, scopeResolution);
            if (conditionFailure.isPresent()) {
                decisions.add(new WorldAwakenedRuleDecision(
                        rule,
                        false,
                        Optional.of(conditionFailure.get().reason()),
                        conditionFailure.get().detail(),
                        scopeResolution.stateKey(),
                        OptionalDouble.empty()));
                continue;
            }
            long cooldownUntil = scopeResolution.stateSnapshot().cooldowns().getOrDefault(scopeResolution.stateKey(), 0L);
            if (cooldownUntil > context.nowMillis()) {
                decisions.add(new WorldAwakenedRuleDecision(
                        rule,
                        false,
                        Optional.of(WorldAwakenedRejectionReason.COOLDOWN_ACTIVE),
                        "cooldown_until=" + cooldownUntil,
                        scopeResolution.stateKey(),
                        OptionalDouble.empty()));
                continue;
            }
            if (scopeResolution.stateSnapshot().consumedRules().contains(scopeResolution.stateKey())) {
                decisions.add(new WorldAwakenedRuleDecision(
                        rule,
                        false,
                        Optional.of(WorldAwakenedRejectionReason.ONE_SHOT_CONSUMED),
                        "rule_marked_consumed",
                        scopeResolution.stateKey(),
                        OptionalDouble.empty()));
                continue;
            }
            double chanceRoll = deterministicRoll(context, rule.id(), "rule_chance", -1);
            if (applyChance && chanceRoll > rule.chance()) {
                decisions.add(new WorldAwakenedRuleDecision(
                        rule,
                        false,
                        Optional.of(WorldAwakenedRejectionReason.CHANCE_ROLL_FAILED),
                        "roll=" + chanceRoll + " chance=" + rule.chance(),
                        scopeResolution.stateKey(),
                        OptionalDouble.of(chanceRoll)));
                continue;
            }
            decisions.add(new WorldAwakenedRuleDecision(
                    rule,
                    true,
                    Optional.empty(),
                    "eligible",
                    scopeResolution.stateKey(),
                    OptionalDouble.of(chanceRoll)));
            matched.add(new WorldAwakenedMatchedRule(rule, scopeResolution.scope(), scopeResolution.stateKey()));
        }
        return new WorldAwakenedRuleEvaluation(ordered.size(), List.copyOf(decisions), List.copyOf(matched));
    }
    private static CompiledRule compileRule(RuleDefinition definition) {
        List<CompiledCondition> conditions = new ArrayList<>(definition.conditions().size());
        for (JsonElement condition : definition.conditions()) {
            if (!condition.isJsonObject()) {
                continue;
            }
            JsonObject conditionNode = condition.getAsJsonObject();
            if (!isNodeEnabled(conditionNode)) {
                continue;
            }
            compileCondition(conditionNode).ifPresent(conditions::add);
        }
        List<CompiledAction> actions = new ArrayList<>(definition.actions().size());
        for (int index = 0; index < definition.actions().size(); index++) {
            JsonElement action = definition.actions().get(index);
            if (!action.isJsonObject()) {
                continue;
            }
            JsonObject actionNode = action.getAsJsonObject();
            if (!isNodeEnabled(actionNode)) {
                continue;
            }
            compileAction(actionNode, index).ifPresent(actions::add);
        }
        actions.sort(ACTION_ORDER);
        return new CompiledRule(
                definition.id(),
                definition.priority(),
                definition.executionScope(),
                definition.chance(),
                cooldownDurationMillis(definition.cooldown()),
                List.copyOf(conditions),
                List.copyOf(actions),
                List.copyOf(definition.tags()));
    }
    private static Optional<CompiledCondition> compileCondition(JsonObject node) {
        Optional<ResourceLocation> typeOpt = readResourceLocation(node, "type");
        if (typeOpt.isEmpty()) {
            return Optional.empty();
        }
        JsonObject parameters = readParametersObject(node);
        String path = typeOpt.get().getPath().toLowerCase(Locale.ROOT);
        ConditionKind kind = switch (path) {
            case "stage_unlocked" -> ConditionKind.STAGE_UNLOCKED;
            case "stage_locked" -> ConditionKind.STAGE_LOCKED;
            case "ascension_reward_owned" -> ConditionKind.ASCENSION_REWARD_OWNED;
            case "ascension_offer_pending" -> ConditionKind.ASCENSION_OFFER_PENDING;
            case "current_dimension" -> ConditionKind.CURRENT_DIMENSION;
            case "current_biome" -> ConditionKind.CURRENT_BIOME;
            case "world_day_gte" -> ConditionKind.WORLD_DAY_GTE;
            case "player_distance_from_spawn" -> ConditionKind.PLAYER_DISTANCE_FROM_SPAWN;
            case "loaded_mod" -> ConditionKind.LOADED_MOD;
            case "config_toggle_enabled" -> ConditionKind.CONFIG_TOGGLE_ENABLED;
            case "entity_type" -> ConditionKind.ENTITY_TYPE;
            case "entity_tag" -> ConditionKind.ENTITY_TAG;
            case "entity_not_boss" -> ConditionKind.ENTITY_NOT_BOSS;
            case "entity_is_mutated" -> ConditionKind.ENTITY_IS_MUTATED;
            case "player_count_online" -> ConditionKind.PLAYER_COUNT_ONLINE;
            case "apotheosis_world_tier_compare" -> ConditionKind.APOTHEOSIS_WORLD_TIER_COMPARE;
            case "random_chance" -> ConditionKind.RANDOM_CHANCE;
            case "moon_phase" -> ConditionKind.MOON_PHASE;
            case "structure_context" -> ConditionKind.STRUCTURE_CONTEXT;
            case "invasion_active" -> ConditionKind.INVASION_ACTIVE;
            default -> ConditionKind.UNSUPPORTED;
        };
        Optional<ResourceLocation> resourceRef = switch (kind) {
            case STAGE_UNLOCKED, STAGE_LOCKED -> readResourceLocation(parameters, "stage");
            case CURRENT_DIMENSION -> readResourceLocation(parameters, "dimension");
            case CURRENT_BIOME -> readResourceLocation(parameters, "biome");
            case ENTITY_TYPE -> readResourceLocation(parameters, "entity");
            case ENTITY_TAG -> readTag(parameters, "tag");
            case ASCENSION_REWARD_OWNED -> readResourceLocation(parameters, "reward");
            case ASCENSION_OFFER_PENDING -> readResourceLocation(parameters, "offer");
            default -> Optional.empty();
        };
        Optional<String> text = switch (kind) {
            case LOADED_MOD -> readString(parameters, "mod");
            case CONFIG_TOGGLE_ENABLED -> readString(parameters, "config_gate");
            case STRUCTURE_CONTEXT -> readString(parameters, "structure");
            case APOTHEOSIS_WORLD_TIER_COMPARE -> readString(parameters, "op");
            default -> Optional.empty();
        };
        OptionalDouble value = switch (kind) {
            case WORLD_DAY_GTE -> readDouble(parameters, "value");
            case RANDOM_CHANCE -> readDouble(parameters, "chance");
            case APOTHEOSIS_WORLD_TIER_COMPARE -> readDouble(parameters, "value");
            default -> OptionalDouble.empty();
        };
        OptionalDouble min = switch (kind) {
            case PLAYER_DISTANCE_FROM_SPAWN, PLAYER_COUNT_ONLINE -> readDouble(parameters, "min");
            default -> OptionalDouble.empty();
        };
        OptionalDouble max = switch (kind) {
            case PLAYER_DISTANCE_FROM_SPAWN, PLAYER_COUNT_ONLINE -> readDouble(parameters, "max");
            default -> OptionalDouble.empty();
        };
        Set<Integer> ints = kind == ConditionKind.MOON_PHASE ? parseMoonPhases(parameters) : Set.of();
        return Optional.of(new CompiledCondition(
                typeOpt.get(),
                kind,
                resourceRef,
                text,
                value,
                min,
                max,
                ints));
    }
    private static Optional<CompiledAction> compileAction(JsonObject node, int authoredIndex) {
        Optional<ResourceLocation> typeOpt = readResourceLocation(node, "type");
        if (typeOpt.isEmpty()) {
            return Optional.empty();
        }
        JsonObject parameters = readParametersObject(node);
        String path = typeOpt.get().getPath().toLowerCase(Locale.ROOT);
        WorldAwakenedRuleActionKind kind = switch (path) {
            case "unlock_stage" -> WorldAwakenedRuleActionKind.UNLOCK_STAGE;
            case "lock_stage" -> WorldAwakenedRuleActionKind.LOCK_STAGE;
            case "grant_ascension_offer" -> WorldAwakenedRuleActionKind.GRANT_ASCENSION_OFFER;
            case "apply_mutator_pool" -> WorldAwakenedRuleActionKind.APPLY_MUTATOR_POOL;
            case "apply_stat_profile" -> WorldAwakenedRuleActionKind.APPLY_STAT_PROFILE;
            case "inject_loot_profile" -> WorldAwakenedRuleActionKind.INJECT_LOOT_PROFILE;
            case "trigger_invasion_profile" -> WorldAwakenedRuleActionKind.TRIGGER_INVASION_PROFILE;
            case "send_warning_message" -> WorldAwakenedRuleActionKind.SEND_WARNING_MESSAGE;
            case "drop_reward_table" -> WorldAwakenedRuleActionKind.DROP_REWARD_TABLE;
            case "mark_rule_consumed" -> WorldAwakenedRuleActionKind.MARK_RULE_CONSUMED;
            case "set_world_scalar" -> WorldAwakenedRuleActionKind.SET_WORLD_SCALAR;
            case "set_temp_invasion_modifier" -> WorldAwakenedRuleActionKind.SET_TEMP_INVASION_MODIFIER;
            default -> WorldAwakenedRuleActionKind.UNSUPPORTED;
        };
        Optional<ResourceLocation> resourceRef = switch (kind) {
            case UNLOCK_STAGE, LOCK_STAGE -> readResourceLocation(parameters, "stage");
            case GRANT_ASCENSION_OFFER -> readResourceLocation(parameters, "offer");
            case APPLY_MUTATOR_POOL, APPLY_STAT_PROFILE, INJECT_LOOT_PROFILE, TRIGGER_INVASION_PROFILE, DROP_REWARD_TABLE, SET_TEMP_INVASION_MODIFIER ->
                readResourceLocation(parameters, "pool", "profile", "table");
            default -> Optional.empty();
        };
        Optional<String> text = switch (kind) {
            case SEND_WARNING_MESSAGE -> readMessageText(parameters);
            case SET_WORLD_SCALAR -> readString(parameters, "key");
            default -> Optional.empty();
        };
        Optional<String> operator = kind == WorldAwakenedRuleActionKind.SET_WORLD_SCALAR
                ? readString(parameters, "op")
                : Optional.empty();
        OptionalDouble value = kind == WorldAwakenedRuleActionKind.SET_WORLD_SCALAR
                ? readDouble(parameters, "value")
                : OptionalDouble.empty();
        int priority = readInt(node, "priority").orElse(0);
        return Optional.of(new CompiledAction(typeOpt.get(), kind, resourceRef, text, operator, value, priority, authoredIndex));
    }
    private static ScopeResolution resolveScope(CompiledRule rule, WorldAwakenedRuleMatchContext context) {
        ExecutionScope scope = rule.executionScope();
        return switch (scope) {
            case WORLD -> new ScopeResolution(
                    true,
                    scope,
                    context.worldStageSnapshot(),
                    context.worldRuleStateSnapshot(),
                    rule.id().toString(),
                    WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                    "eligible_scope_world");
            case PLAYER -> {
                if (!context.hasPlayerContext()) {
                    yield new ScopeResolution(
                            false,
                            scope,
                            Set.of(),
                            WorldAwakenedRuleStateSnapshot.empty(),
                            rule.id().toString(),
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "player_scope_without_player_context");
                }
                yield new ScopeResolution(
                        true,
                        scope,
                        context.playerStageSnapshot(),
                        context.playerRuleStateSnapshot(),
                        rule.id().toString(),
                        WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                        "eligible_scope_player");
            }
            case ENTITY, SPAWN_EVENT -> {
                if (!context.hasEntityContext()) {
                    yield new ScopeResolution(
                            false,
                            scope,
                            Set.of(),
                            WorldAwakenedRuleStateSnapshot.empty(),
                            rule.id().toString(),
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "entity_scope_without_entity_context");
                }
                String entityScopeKey = context.entityUuid()
                        .map(uuid -> rule.id() + "|entity:" + uuid)
                        .orElse(rule.id() + "|entity:unknown");
                yield new ScopeResolution(
                        true,
                        scope,
                        context.worldStageSnapshot(),
                        context.worldRuleStateSnapshot(),
                        entityScopeKey,
                        WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                        "eligible_scope_entity");
            }
        };
    }
    private static Optional<ConditionFailure> evaluateConditions(
            CompiledRule rule,
            WorldAwakenedRuleMatchContext context,
            WorldAwakenedStageRegistry stageRegistry,
            ScopeResolution scopeResolution) {
        for (int index = 0; index < rule.conditions().size(); index++) {
            CompiledCondition condition = rule.conditions().get(index);
            Optional<ConditionFailure> failure = evaluateCondition(
                    rule,
                    condition,
                    index,
                    context,
                    stageRegistry,
                    scopeResolution);
            if (failure.isPresent()) {
                return failure;
            }
        }
        return Optional.empty();
    }
    private static Optional<ConditionFailure> evaluateCondition(
            CompiledRule rule,
            CompiledCondition condition,
            int index,
            WorldAwakenedRuleMatchContext context,
            WorldAwakenedStageRegistry stageRegistry,
            ScopeResolution scopeResolution) {
        ConditionKind kind = condition.kind();
        return switch (kind) {
            case STAGE_UNLOCKED -> condition.resourceRef()
                    .map(stage -> hasStage(scopeResolution.stageSnapshot(), stageRegistry, stage)
                            ? Optional.<ConditionFailure>empty()
                            : Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.STAGE_CONDITION_FAILED,
                                    "stage_unlocked_failed:" + stage)))
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.STAGE_CONDITION_FAILED,
                            "stage_unlocked_missing_stage")));
            case STAGE_LOCKED -> condition.resourceRef()
                    .map(stage -> hasStage(scopeResolution.stageSnapshot(), stageRegistry, stage)
                            ? Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.STAGE_CONDITION_FAILED,
                                    "stage_locked_failed:" + stage))
                            : Optional.<ConditionFailure>empty())
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.STAGE_CONDITION_FAILED,
                            "stage_locked_missing_stage")));
            case ASCENSION_REWARD_OWNED -> condition.resourceRef()
                    .map(reward -> context.hasPlayerContext() && context.ownedAscensionRewards().contains(reward)
                            ? Optional.<ConditionFailure>empty()
                            : Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                    "ascension_reward_not_owned:" + reward)))
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "ascension_reward_missing_ref")));
            case ASCENSION_OFFER_PENDING -> condition.resourceRef()
                    .map(offer -> context.hasPlayerContext() && context.pendingAscensionOffers().contains(offer)
                            ? Optional.<ConditionFailure>empty()
                            : Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                    "ascension_offer_not_pending:" + offer)))
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "ascension_offer_missing_ref")));
            case CURRENT_DIMENSION -> condition.resourceRef()
                    .map(dimension -> context.dimensionId().equals(dimension)
                            ? Optional.<ConditionFailure>empty()
                            : Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                    "dimension_mismatch:" + context.dimensionId())))
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "current_dimension_missing_ref")));
            case CURRENT_BIOME -> {
                if (context.biomeId().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.WORLD_CONTEXT_CONDITION_UNAVAILABLE,
                            "biome_unavailable"));
                }
                if (condition.resourceRef().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "current_biome_missing_ref"));
                }
                yield context.biomeId().get().equals(condition.resourceRef().get())
                        ? Optional.empty()
                        : Optional.of(new ConditionFailure(
                                WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                "biome_mismatch:" + context.biomeId().get()));
            }
            case WORLD_DAY_GTE -> {
                if (context.worldDay().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.WORLD_CONTEXT_CONDITION_UNAVAILABLE,
                            "world_day_unavailable"));
                }
                if (condition.value().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "world_day_threshold_missing"));
                }
                long day = context.worldDay().getAsLong();
                long threshold = (long) condition.value().getAsDouble();
                yield day >= threshold ? Optional.empty()
                        : Optional.of(new ConditionFailure(
                                WorldAwakenedRejectionReason.STAGE_CONDITION_FAILED,
                                "world_day=" + day + " threshold=" + threshold));
            }
            case PLAYER_DISTANCE_FROM_SPAWN -> {
                if (context.playerDistanceFromSpawn().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.WORLD_CONTEXT_CONDITION_UNAVAILABLE,
                            "player_distance_unavailable"));
                }
                double distance = context.playerDistanceFromSpawn().getAsDouble();
                if (condition.min().isPresent() && distance < condition.min().getAsDouble()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "player_distance_below_min:" + distance));
                }
                if (condition.max().isPresent() && distance > condition.max().getAsDouble()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "player_distance_above_max:" + distance));
                }
                yield Optional.empty();
            }
            case LOADED_MOD -> condition.text()
                    .map(modId -> context.loadedMods().contains(modId.toLowerCase(Locale.ROOT))
                            ? Optional.<ConditionFailure>empty()
                            : Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.INTEGRATION_INACTIVE,
                                    "loaded_mod_missing:" + modId)))
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "loaded_mod_missing_mod_id")));
            case CONFIG_TOGGLE_ENABLED -> condition.text()
                    .map(toggle -> context.configToggles().getOrDefault(toggle, false)
                            ? Optional.<ConditionFailure>empty()
                            : Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.CONFIG_GATE_FAILED,
                                    "config_toggle_disabled:" + toggle)))
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "config_toggle_missing_key")));
            case ENTITY_TYPE -> {
                if (context.entityTypeId().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "entity_type_without_entity_context"));
                }
                if (condition.resourceRef().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "entity_type_missing_ref"));
                }
                yield context.entityTypeId().get().equals(condition.resourceRef().get())
                        ? Optional.empty()
                        : Optional.of(new ConditionFailure(
                                WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                "entity_type_mismatch:" + context.entityTypeId().get()));
            }
            case ENTITY_TAG -> condition.resourceRef()
                    .map(tag -> context.entityTags().contains(tag)
                            ? Optional.<ConditionFailure>empty()
                            : Optional.of(new ConditionFailure(
                                    WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                    "entity_tag_mismatch:" + tag)))
                    .orElseGet(() -> Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                            "entity_tag_missing_ref")));
            case ENTITY_NOT_BOSS -> context.hasEntityContext() && !context.entityIsBoss()
                    ? Optional.empty()
                    : Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "entity_is_boss_or_missing"));
            case ENTITY_IS_MUTATED -> context.hasEntityContext() && context.entityIsMutated()
                    ? Optional.empty()
                    : Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "entity_not_mutated_or_missing"));
            case PLAYER_COUNT_ONLINE -> {
                int online = context.playerCountOnline();
                if (condition.min().isPresent() && online < condition.min().getAsDouble()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "player_count_below_min:" + online));
                }
                if (condition.max().isPresent() && online > condition.max().getAsDouble()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "player_count_above_max:" + online));
                }
                yield Optional.empty();
            }
            case APOTHEOSIS_WORLD_TIER_COMPARE -> Optional.of(new ConditionFailure(
                    WorldAwakenedRejectionReason.INTEGRATION_INACTIVE,
                    "apotheosis_tier_provider_unavailable"));
            case RANDOM_CHANCE -> {
                double chance = condition.value().orElse(1.0D);
                double roll = deterministicRoll(context, rule.id(), "condition_random_chance", index);
                yield roll <= chance
                        ? Optional.empty()
                        : Optional.of(new ConditionFailure(
                                WorldAwakenedRejectionReason.CHANCE_ROLL_FAILED,
                                "random_chance_roll=" + roll + " threshold=" + chance));
            }
            case MOON_PHASE -> {
                if (context.worldDay().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.WORLD_CONTEXT_CONDITION_UNAVAILABLE,
                            "moon_phase_unavailable"));
                }
                long phase = Math.floorMod(context.worldDay().getAsLong(), 8L);
                yield condition.intValues().contains((int) phase)
                        ? Optional.empty()
                        : Optional.of(new ConditionFailure(
                                WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                "moon_phase_mismatch:" + phase));
            }
            case STRUCTURE_CONTEXT -> {
                if (context.structureContext().isEmpty()) {
                    yield Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.WORLD_CONTEXT_CONDITION_UNAVAILABLE,
                            "structure_context_unavailable"));
                }
                if (condition.text().isEmpty()) {
                    yield Optional.empty();
                }
                yield context.structureContext().get().equals(condition.text().get())
                        ? Optional.empty()
                        : Optional.of(new ConditionFailure(
                                WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                                "structure_context_mismatch"));
            }
            case INVASION_ACTIVE -> context.invasionActive()
                    ? Optional.empty()
                    : Optional.of(new ConditionFailure(
                            WorldAwakenedRejectionReason.SELECTOR_MISMATCH,
                            "invasion_not_active"));
            case UNSUPPORTED -> Optional.of(new ConditionFailure(
                    WorldAwakenedRejectionReason.INVALID_REFERENCED_OBJECT,
                    "unsupported_condition_type:" + condition.typeId()));
        };
    }
    private static boolean hasStage(
            Set<ResourceLocation> activeStages,
            WorldAwakenedStageRegistry stageRegistry,
            ResourceLocation requestedId) {
        Optional<ResourceLocation> canonical = stageRegistry.resolveCanonicalId(requestedId);
        if (canonical.isPresent()) {
            return activeStages.contains(canonical.get());
        }
        return activeStages.contains(requestedId);
    }
    private static double deterministicRoll(
            WorldAwakenedRuleMatchContext context,
            ResourceLocation ruleId,
            String channel,
            int extraIndex) {
        long seed = 0x9E3779B97F4A7C15L;
        seed = mix(seed, context.eventType());
        seed = mix(seed, context.dimensionId().toString());
        seed = mix(seed, context.playerUuid().orElse("<none>"));
        seed = mix(seed, context.entityUuid().orElse("<none>"));
        seed = mix(seed, context.tick());
        seed = mix(seed, ruleId.toString());
        seed = mix(seed, channel);
        seed = mix(seed, extraIndex);
        return new SplittableRandom(seed).nextDouble();
    }
    private static long mix(long seed, String value) {
        long result = seed;
        for (int i = 0; i < value.length(); i++) {
            result ^= value.charAt(i);
            result *= 0x100000001B3L;
            result ^= (result >>> 31);
        }
        return result;
    }
    private static long mix(long seed, long value) {
        long result = seed ^ value;
        result *= 0x100000001B3L;
        result ^= (result >>> 33);
        return result;
    }
    private static Optional<ResourceLocation> readResourceLocation(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            ResourceLocation parsed = parseResourceLocation(object.get(key).getAsString());
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }
    private static Optional<ResourceLocation> readTag(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            String value = object.get(key).getAsString();
            if (value.startsWith("#")) {
                value = value.substring(1);
            }
            ResourceLocation parsed = parseResourceLocation(value);
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }
    private static Optional<String> readString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            String value = object.get(key).getAsString();
            if (!value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
    private static OptionalDouble readDouble(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isNumber()) {
                continue;
            }
            return OptionalDouble.of(object.get(key).getAsDouble());
        }
        return OptionalDouble.empty();
    }
    private static Optional<String> readMessageText(JsonObject object) {
        if (!object.has("message")) {
            return Optional.empty();
        }
        JsonElement message = object.get("message");
        if (message.isJsonPrimitive()) {
            String raw = message.getAsString();
            if (!raw.isBlank()) {
                return Optional.of(raw);
            }
            return Optional.empty();
        }
        return Optional.of(message.toString());
    }
    private static Set<Integer> parseMoonPhases(JsonObject object) {
        Set<Integer> phases = new LinkedHashSet<>();
        if (object.has("phase") && object.get("phase").isJsonPrimitive()) {
            parseMoonPhaseValue(object.get("phase"), phases);
        }
        if (object.has("phases") && object.get("phases").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("phases")) {
                parseMoonPhaseValue(element, phases);
            }
        }
        if (phases.isEmpty()) {
            phases.add(0);
        }
        return Set.copyOf(phases);
    }
    private static void parseMoonPhaseValue(JsonElement raw, Set<Integer> phases) {
        if (raw == null || raw.isJsonNull()) {
            return;
        }
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
            phases.add(Math.floorMod(raw.getAsInt(), 8));
            return;
        }
        if (!raw.isJsonPrimitive()) {
            return;
        }
        String value = raw.getAsString().toLowerCase(Locale.ROOT);
        Integer mapped = switch (value) {
            case "full_moon", "full" -> 0;
            case "waning_gibbous" -> 1;
            case "last_quarter" -> 2;
            case "waning_crescent" -> 3;
            case "new_moon", "new" -> 4;
            case "waxing_crescent" -> 5;
            case "first_quarter" -> 6;
            case "waxing_gibbous" -> 7;
            default -> null;
        };
        if (mapped != null) {
            phases.add(mapped);
        }
    }
    private static long cooldownDurationMillis(Optional<JsonElement> cooldown) {
        if (cooldown.isEmpty()) {
            return 0L;
        }
        JsonElement raw = cooldown.get();
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
            return Math.max(0L, Math.round(raw.getAsDouble() * 1000.0D));
        }
        if (!raw.isJsonObject()) {
            return 0L;
        }
        JsonObject object = raw.getAsJsonObject();
        if (hasNumeric(object, "milliseconds", "millis", "ms")) {
            return Math.max(0L, readLong(object, "milliseconds", "millis", "ms").orElse(0L));
        }
        if (hasNumeric(object, "seconds", "sec", "s")) {
            return Math.max(0L, readLong(object, "seconds", "sec", "s").orElse(0L) * 1000L);
        }
        if (hasNumeric(object, "minutes", "min", "m")) {
            return Math.max(0L, readLong(object, "minutes", "min", "m").orElse(0L) * 60_000L);
        }
        if (hasNumeric(object, "ticks", "tick")) {
            return Math.max(0L, readLong(object, "ticks", "tick").orElse(0L) * 50L);
        }
        return 0L;
    }
    private static boolean hasNumeric(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
                return true;
            }
        }
        return false;
    }
    private static Optional<Long> readLong(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
                return Optional.of(object.get(key).getAsLong());
            }
        }
        return Optional.empty();
    }
    private static ResourceLocation parseResourceLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }
        if (!raw.contains(":")) {
            return ResourceLocation.tryParse("worldawakened:" + raw);
        }
        return null;
    }
    private static JsonObject readParametersObject(JsonObject node) {
        if (!node.has("parameters") || !node.get("parameters").isJsonObject()) {
            return new JsonObject();
        }
        return node.getAsJsonObject("parameters");
    }
    private static boolean isNodeEnabled(JsonObject node) {
        if (!node.has("enabled")) {
            return true;
        }
        JsonElement enabled = node.get("enabled");
        if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            return true;
        }
        return enabled.getAsBoolean();
    }
    private static Optional<Integer> readInt(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
                return Optional.of(object.get(key).getAsInt());
            }
        }
        return Optional.empty();
    }
    private enum ConditionKind {
        STAGE_UNLOCKED,
        STAGE_LOCKED,
        ASCENSION_REWARD_OWNED,
        ASCENSION_OFFER_PENDING,
        CURRENT_DIMENSION,
        CURRENT_BIOME,
        WORLD_DAY_GTE,
        PLAYER_DISTANCE_FROM_SPAWN,
        LOADED_MOD,
        CONFIG_TOGGLE_ENABLED,
        ENTITY_TYPE,
        ENTITY_TAG,
        ENTITY_NOT_BOSS,
        ENTITY_IS_MUTATED,
        PLAYER_COUNT_ONLINE,
        APOTHEOSIS_WORLD_TIER_COMPARE,
        RANDOM_CHANCE,
        MOON_PHASE,
        STRUCTURE_CONTEXT,
        INVASION_ACTIVE,
        UNSUPPORTED
    }
    public record CompiledRule(
            ResourceLocation id,
            int priority,
            ExecutionScope executionScope,
            double chance,
            long cooldownMillis,
            List<CompiledCondition> conditions,
            List<CompiledAction> actions,
            List<String> tags) implements WorldAwakenedConflictResolution.Prioritized {
    }
    public record CompiledCondition(
            ResourceLocation typeId,
            ConditionKind kind,
            Optional<ResourceLocation> resourceRef,
            Optional<String> text,
            OptionalDouble value,
            OptionalDouble min,
            OptionalDouble max,
            Set<Integer> intValues) {
    }
    public record CompiledAction(
            ResourceLocation typeId,
            WorldAwakenedRuleActionKind kind,
            Optional<ResourceLocation> resourceRef,
            Optional<String> text,
            Optional<String> operator,
            OptionalDouble value,
            int priority,
            int authoredIndex) {
    }
    private record ConditionFailure(
            WorldAwakenedRejectionReason reason,
            String detail) {
    }
    private record ScopeResolution(
            boolean valid,
            ExecutionScope scope,
            Set<ResourceLocation> stageSnapshot,
            WorldAwakenedRuleStateSnapshot stateSnapshot,
            String stateKey,
            WorldAwakenedRejectionReason rejectionReason,
            String detail) {
    }
}
