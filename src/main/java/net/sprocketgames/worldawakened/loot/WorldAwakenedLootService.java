package net.sprocketgames.worldawakened.loot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootReplaceMode;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutationProvenance;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedEntityContextView;

public final class WorldAwakenedLootService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation EVENT_ENTITY_KILLED = id("entity_killed");
    private static final ResourceLocation EVENT_INVASION_COMPLETED = id("invasion_completed");
    private static final ResourceLocation EVENT_DEBUG_EVALUATE = id("debug_loot_evaluate");
    private static final Set<ResourceLocation> LIVE_REWARD_EVENTS = Set.of(
            EVENT_ENTITY_KILLED,
            EVENT_INVASION_COMPLETED);
    private static final Comparator<CompiledLootProfile> PROFILE_ORDER =
            Comparator.comparing(profile -> profile.id().toString());

    private final WorldAwakenedDatapackService datapackService;
    private final WorldAwakenedStageService stageService;
    private final AtomicReference<CachedCompiledLoot> cache =
            new AtomicReference<>(new CachedCompiledLoot(0L, CompiledLootGraph.empty()));
    private final AtomicLong traceCounter = new AtomicLong(0L);

    public WorldAwakenedLootService(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService) {
        this.datapackService = datapackService;
        this.stageService = stageService;
    }

    public List<ResourceLocation> loadedLootProfileIds() {
        return compiledLoot().orderedProfileIds();
    }

    public LootRunResult onEntityKilledDrops(
            ServerLevel level,
            LivingEntity target,
            ServerPlayer killer,
            Collection<ItemEntity> drops) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_LOOT_EVOLUTION.get()) {
            return LootRunResult.skipped(
                    nextTraceId(),
                    EVENT_ENTITY_KILLED,
                    LootContextSnapshot.empty(),
                    WorldAwakenedDiagnosticCodes.REWARD_EVENT_UNSUPPORTED,
                    "loot_evolution_disabled");
        }

        LootContextSnapshot context = buildEntityKillContext(level, target, killer, false);
        LootRunResult result = evaluate(context, Optional.empty(), collectDropSummary(drops), false);
        if (result.skipped()) {
            return result;
        }

        boolean changed = applyResolvedDrops(level, target, drops, result);
        List<RewardItem> appliedSummary = collectDropSummary(drops);
        return result.withLiveApplied(changed, appliedSummary);
    }

    public LootRunResult debugEvaluate(
            ServerLevel level,
            LootTargetType targetType,
            ResourceLocation targetId,
            ServerPlayer player) {
        Optional<LootContextSnapshot> context = buildDebugContext(level, targetType, targetId, player);
        if (context.isEmpty()) {
            return LootRunResult.skipped(
                    nextTraceId(),
                    EVENT_DEBUG_EVALUATE,
                    LootContextSnapshot.empty(),
                    WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID,
                    "invalid_debug_target");
        }
        return evaluate(context.get(), Optional.empty(), List.of(), true);
    }

    public LootRunResult debugForceProfile(
            ServerLevel level,
            ResourceLocation profileId,
            LootTargetType targetType,
            ResourceLocation targetId,
            ServerPlayer player) {
        Optional<LootContextSnapshot> context = buildDebugContext(level, targetType, targetId, player);
        if (context.isEmpty()) {
            return LootRunResult.skipped(
                    nextTraceId(),
                    EVENT_DEBUG_EVALUATE,
                    LootContextSnapshot.empty(),
                    WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID,
                    "invalid_debug_target");
        }
        return evaluate(context.get(), Optional.of(profileId), List.of(), true);
    }

    LootRunResult evaluateDebugContext(
            LootContextSnapshot context,
            Optional<ResourceLocation> forcedProfileId,
            List<RewardItem> baselineDrops) {
        return evaluate(context, forcedProfileId, baselineDrops, true);
    }

    private LootRunResult evaluate(
            LootContextSnapshot context,
            Optional<ResourceLocation> forcedProfileId,
            List<RewardItem> baselineDrops,
            boolean debugContext) {
        String traceId = nextTraceId();
        CompiledLootGraph compiled = compiledLoot();

        if (!debugContext && !LIVE_REWARD_EVENTS.contains(context.sourceEventId())) {
            return LootRunResult.skipped(
                    traceId,
                    context.sourceEventId(),
                    context,
                    WorldAwakenedDiagnosticCodes.REWARD_EVENT_UNSUPPORTED,
                    "unsupported_reward_event:" + context.sourceEventId());
        }

        List<CompiledLootProfile> candidates = candidateProfiles(compiled, context, forcedProfileId);
        if (forcedProfileId.isPresent() && candidates.isEmpty()) {
            return LootRunResult.skipped(
                    traceId,
                    context.sourceEventId(),
                    context,
                    WorldAwakenedDiagnosticCodes.DEBUG_LOOT_PROFILE_NOT_FOUND,
                    "forced_profile_not_found:" + forcedProfileId.get());
        }

        List<ResourceLocation> candidateIds = candidates.stream()
                .map(CompiledLootProfile::id)
                .toList();

        boolean sensitiveTarget = context.lootTableId().isPresent()
                && isApotheosisSensitiveTarget(compiled, context.lootTableId().get());
        List<ProfileDecision> decisions = new ArrayList<>();
        List<ResolvedOperation> resolvedOperations = new ArrayList<>();
        Set<ResourceLocation> seenProfiles = new LinkedHashSet<>();

        for (CompiledLootProfile profile : candidates) {
            if (!seenProfiles.add(profile.id())) {
                decisions.add(ProfileDecision.rejected(
                        profile.id(),
                        "duplicate_blocked",
                        "same profile selected more than once in one resolver pass",
                        WorldAwakenedDiagnosticCodes.REWARD_CONTRIBUTOR_DUPLICATE_BLOCKED,
                        profile.replaceMode(),
                        profile.replaceMode(),
                        sensitiveTarget,
                        "blocked_duplicate"));
                continue;
            }

            Optional<ProfileDecision> hardRejection = validateProfileAgainstContext(profile, context, sensitiveTarget);
            if (hardRejection.isPresent()) {
                decisions.add(hardRejection.get());
                continue;
            }

            ModeResolution modeResolution = resolveMode(profile.replaceMode(), context, sensitiveTarget);
            if (modeResolution.blocked()) {
                decisions.add(ProfileDecision.rejected(
                        profile.id(),
                        "policy_blocked",
                        modeResolution.detail(),
                        modeResolution.diagnosticCode(),
                        profile.replaceMode(),
                        modeResolution.resolvedMode(),
                        sensitiveTarget,
                        modeResolution.fallbackAction()));
                continue;
            }

            Optional<RewardItem> selected = selectReward(profile, context, traceId);
            if (selected.isEmpty()) {
                decisions.add(ProfileDecision.rejected(
                        profile.id(),
                        "entry_invalid",
                        "no valid weighted loot entry could be selected",
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        profile.replaceMode(),
                        modeResolution.resolvedMode(),
                        sensitiveTarget,
                        modeResolution.fallbackAction()));
                continue;
            }

            decisions.add(ProfileDecision.matched(
                    profile.id(),
                    modeResolution.resolvedMode(),
                    profile.replaceMode(),
                    selected.get(),
                    sensitiveTarget,
                    modeResolution.fallbackAction(),
                    modeResolution.diagnosticCode(),
                    modeResolution.detail()));
            resolvedOperations.add(new ResolvedOperation(
                    profile.id(),
                    modeResolution.resolvedMode(),
                    selected.get(),
                    modeResolution.fallbackAction()));
        }

        resolvedOperations.sort(Comparator.comparing(operation -> operation.profileId().toString()));
        List<RewardItem> finalOutcome = simulateOutcome(baselineDrops, resolvedOperations);

        if (WorldAwakenedFeatureGates.debugLoggingEnabled()) {
            WorldAwakenedLog.debug(
                    LOGGER,
                    WorldAwakenedLogCategory.PIPELINE,
                    "trace={} event={} target_type={} target_id={} candidates={} matched={} final_items={}",
                    traceId,
                    context.sourceEventId(),
                    context.targetType().serialized(),
                    context.targetId(),
                    candidateIds.size(),
                    decisions.stream().filter(ProfileDecision::matched).count(),
                    formatRewardItems(finalOutcome));
        }

        return new LootRunResult(
                traceId,
                context.sourceEventId(),
                context,
                candidateIds,
                List.copyOf(decisions),
                List.copyOf(resolvedOperations),
                List.copyOf(finalOutcome),
                true,
                false,
                false,
                "",
                "");
    }

    private Optional<ProfileDecision> validateProfileAgainstContext(
            CompiledLootProfile profile,
            LootContextSnapshot context,
            boolean sensitiveTarget) {
        if (profile.configGate().isPresent()) {
            String gate = profile.configGate().get();
            if (!context.configToggles().getOrDefault(gate, false)) {
                return Optional.of(ProfileDecision.rejected(
                        profile.id(),
                        "config_gate_failed",
                        "config gate disabled: " + gate,
                        WorldAwakenedDiagnosticCodes.CONFIG_GATE_INVALID,
                        profile.replaceMode(),
                        profile.replaceMode(),
                        sensitiveTarget,
                        "config_gate_disabled"));
            }
        }

        if (!profile.stageFilter().matches(activeStageSnapshot(context), stageService.stageRegistry())) {
            return Optional.of(ProfileDecision.rejected(
                    profile.id(),
                    "stage_filter_failed",
                    "stage_filters rejected current context",
                    WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                    profile.replaceMode(),
                    profile.replaceMode(),
                    sensitiveTarget,
                    "stage_filter_rejected"));
        }

        if (profile.hasApotheosisTierFilter()) {
            return Optional.of(ProfileDecision.rejected(
                    profile.id(),
                    "apotheosis_filter_unavailable",
                    "apotheosis_tier_filters present but no runtime tier provider exists in Phase 7",
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    profile.replaceMode(),
                    profile.replaceMode(),
                    sensitiveTarget,
                    "fail_closed"));
        }

        for (CompiledCondition condition : profile.conditions()) {
            if (!conditionMatches(condition, context, profile.id(), "conditions")) {
                return Optional.of(ProfileDecision.rejected(
                        profile.id(),
                        "condition_failed",
                        "condition failed: " + condition.typeId(),
                        WorldAwakenedDiagnosticCodes.REWARD_CONTEXT_MISSING,
                        profile.replaceMode(),
                        profile.replaceMode(),
                        sensitiveTarget,
                        "condition_failed"));
            }
        }

        for (CompiledCondition condition : profile.modConditions()) {
            if (!conditionMatches(condition, context, profile.id(), "mod_conditions")) {
                return Optional.of(ProfileDecision.rejected(
                        profile.id(),
                        "mod_condition_failed",
                        "mod condition failed: " + condition.typeId(),
                        WorldAwakenedDiagnosticCodes.REWARD_CONTEXT_MISSING,
                        profile.replaceMode(),
                        profile.replaceMode(),
                        sensitiveTarget,
                        "mod_condition_failed"));
            }
        }

        return Optional.empty();
    }

    private ModeResolution resolveMode(
            LootReplaceMode requestedMode,
            LootContextSnapshot context,
            boolean sensitiveTarget) {
        boolean destructiveMode = requestedMode == LootReplaceMode.REMOVE_ENTRIES
                || requestedMode == LootReplaceMode.REPLACE_ENTRIES;
        if (!destructiveMode) {
            return ModeResolution.allowed(requestedMode, "", "", "none");
        }

        if (WorldAwakenedCommonConfig.INJECT_ONLY.get() || !WorldAwakenedCommonConfig.ALLOW_ENTRY_REPLACEMENT.get()) {
            return ModeResolution.blocked(
                    requestedMode,
                    WorldAwakenedDiagnosticCodes.REWARD_POLICY_DESTRUCTIVE_MODE_BLOCKED,
                    "destructive loot mode is disabled by loot.inject_only or loot.allow_entry_replacement policy",
                    "blocked");
        }

        if (!context.apotheosisCompatActive() || !sensitiveTarget) {
            return ModeResolution.allowed(requestedMode, "", "", "none");
        }

        String policy = WorldAwakenedCommonConfig.APOTHEOSIS_LOOT_UNSAFE_MODE_POLICY.get()
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (policy) {
            case "downgrade_additive", "downgrade" -> ModeResolution.allowed(
                    LootReplaceMode.INJECT,
                    WorldAwakenedDiagnosticCodes.APOTHEOSIS_LOOT_MODE_UNSAFE,
                    "unsafe destructive mode downgraded to inject on Apotheosis-sensitive target",
                    "downgraded_to_inject");
            case "disable_profile_branch", "disable" -> ModeResolution.blocked(
                    requestedMode,
                    WorldAwakenedDiagnosticCodes.APOTHEOSIS_LOOT_OVERRIDE_BLOCKED,
                    "unsafe destructive mode disabled on Apotheosis-sensitive target",
                    "disabled_branch");
            case "block" -> ModeResolution.blocked(
                    requestedMode,
                    WorldAwakenedDiagnosticCodes.APOTHEOSIS_LOOT_OVERRIDE_BLOCKED,
                    "unsafe destructive mode blocked on Apotheosis-sensitive target",
                    "blocked");
            default -> ModeResolution.blocked(
                    requestedMode,
                    WorldAwakenedDiagnosticCodes.APOTHEOSIS_LOOT_OVERRIDE_BLOCKED,
                    "unknown loot_unsafe_mode_policy value blocked destructive mode on Apotheosis-sensitive target",
                    "blocked");
        };
    }

    private static List<RewardItem> simulateOutcome(
            List<RewardItem> baselineDrops,
            List<ResolvedOperation> operations) {
        List<RewardItem> working = new ArrayList<>(baselineDrops);
        List<ResolvedOperation> replacements = operations.stream()
                .filter(operation -> operation.mode() == LootReplaceMode.REPLACE_ENTRIES)
                .toList();
        List<ResolvedOperation> removals = operations.stream()
                .filter(operation -> operation.mode() == LootReplaceMode.REMOVE_ENTRIES)
                .toList();
        List<ResolvedOperation> injects = operations.stream()
                .filter(operation -> operation.mode() == LootReplaceMode.INJECT
                        || operation.mode() == LootReplaceMode.ADD_BONUS_POOL)
                .toList();

        if (!replacements.isEmpty()) {
            working.clear();
            for (ResolvedOperation operation : replacements) {
                working.add(operation.selectedReward());
            }
        }

        if (!removals.isEmpty()) {
            Set<ResourceLocation> removeIds = removals.stream()
                    .map(operation -> operation.selectedReward().itemId())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            working = new ArrayList<>(working.stream()
                    .filter(item -> !removeIds.contains(item.itemId()))
                    .toList());
        }

        for (ResolvedOperation inject : injects) {
            working.add(inject.selectedReward());
        }
        return List.copyOf(working);
    }

    private static boolean applyResolvedDrops(
            ServerLevel level,
            LivingEntity target,
            Collection<ItemEntity> drops,
            LootRunResult result) {
        List<ResolvedOperation> replacements = result.operations().stream()
                .filter(operation -> operation.mode() == LootReplaceMode.REPLACE_ENTRIES)
                .toList();
        List<ResolvedOperation> removals = result.operations().stream()
                .filter(operation -> operation.mode() == LootReplaceMode.REMOVE_ENTRIES)
                .toList();
        List<ResolvedOperation> injects = result.operations().stream()
                .filter(operation -> operation.mode() == LootReplaceMode.INJECT
                        || operation.mode() == LootReplaceMode.ADD_BONUS_POOL)
                .toList();

        boolean changed = false;
        if (!replacements.isEmpty()) {
            drops.clear();
            changed = true;
            for (ResolvedOperation replacement : replacements) {
                spawnDrop(level, target, drops, replacement.selectedReward());
            }
        }

        if (!removals.isEmpty()) {
            Set<ResourceLocation> removeIds = removals.stream()
                    .map(operation -> operation.selectedReward().itemId())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!removeIds.isEmpty()) {
                boolean removed = drops.removeIf(drop -> removeIds.contains(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem())));
                changed = changed || removed;
            }
        }

        if (!injects.isEmpty()) {
            for (ResolvedOperation inject : injects) {
                if (spawnDrop(level, target, drops, inject.selectedReward())) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean spawnDrop(
            ServerLevel level,
            LivingEntity target,
            Collection<ItemEntity> drops,
            RewardItem reward) {
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(reward.itemId());
        if (item.isEmpty() || reward.count() <= 0) {
            return false;
        }
        ItemStack stack = new ItemStack(item.get(), reward.count());
        ItemEntity entity = new ItemEntity(level, target.getX(), target.getY(), target.getZ(), stack);
        return drops.add(entity);
    }

    private static List<RewardItem> collectDropSummary(Collection<ItemEntity> drops) {
        List<RewardItem> summary = new ArrayList<>();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            summary.add(new RewardItem(itemId, stack.getCount()));
        }
        return List.copyOf(summary);
    }

    private Optional<RewardItem> selectReward(
            CompiledLootProfile profile,
            LootContextSnapshot context,
            String traceId) {
        if (profile.entries().isEmpty()) {
            return Optional.empty();
        }
        int totalWeight = profile.entries().stream()
                .mapToInt(CompiledLootEntry::weight)
                .filter(weight -> weight > 0)
                .sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }

        double selectionRoll = deterministicRoll(context, profile.id(), traceId, "entry_select", -1);
        int threshold = Math.min(totalWeight - 1, (int) Math.floor(selectionRoll * totalWeight));
        int cursor = 0;
        CompiledLootEntry selected = null;
        for (CompiledLootEntry entry : profile.entries()) {
            if (entry.weight() <= 0) {
                continue;
            }
            cursor += entry.weight();
            if (threshold < cursor) {
                selected = entry;
                break;
            }
        }
        if (selected == null) {
            selected = profile.entries().get(0);
        }

        int count = selected.minCount();
        if (selected.maxCount() > selected.minCount()) {
            double countRoll = deterministicRoll(context, profile.id(), traceId, "entry_count", selected.authoredIndex());
            int span = selected.maxCount() - selected.minCount() + 1;
            count = selected.minCount() + Math.min(span - 1, (int) Math.floor(countRoll * span));
        }
        if (count <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RewardItem(selected.itemId(), count));
    }

    private static boolean conditionMatches(
            CompiledCondition condition,
            LootContextSnapshot context,
            ResourceLocation ownerId,
            String channelSuffix) {
        return switch (condition.kind()) {
            case STAGE_UNLOCKED -> condition.resourceRef()
                    .map(stage -> containsStage(activeStageSnapshot(context), context.stageRegistry(), stage))
                    .orElse(false);
            case STAGE_LOCKED -> condition.resourceRef()
                    .map(stage -> !containsStage(activeStageSnapshot(context), context.stageRegistry(), stage))
                    .orElse(false);
            case CURRENT_DIMENSION -> condition.resourceRef()
                    .map(context.dimensionId()::equals)
                    .orElse(false);
            case CURRENT_BIOME -> condition.resourceRef()
                    .map(biome -> context.biomeId().map(biome::equals).orElse(false))
                    .orElse(false);
            case WORLD_DAY_GTE -> condition.value().isPresent()
                    && context.worldDay().isPresent()
                    && context.worldDay().getAsLong() >= (long) condition.value().getAsDouble();
            case ENTITY_TYPE -> condition.resourceRef()
                    .map(entity -> context.entityTypeId().map(entity::equals).orElse(false))
                    .orElse(false);
            case ENTITY_TAG -> condition.tagMatcher()
                    .map(matcher -> matcher.matches(context.entityTags()))
                    .orElse(false);
            case ENTITY_NOT_BOSS -> context.entityTypeId().isPresent() && !context.entityIsBoss();
            case ENTITY_IS_MUTATED -> context.entityIsMutated();
            case LOADED_MOD -> condition.text()
                    .map(modId -> context.loadedMods().contains(modId.toLowerCase(Locale.ROOT)))
                    .orElse(false);
            case CONFIG_TOGGLE_ENABLED -> condition.text()
                    .map(toggle -> context.configToggles().getOrDefault(toggle, false))
                    .orElse(false);
            case RANDOM_CHANCE -> {
                double chance = condition.value().orElse(1.0D);
                double roll = deterministicRoll(
                        context,
                        ownerId,
                        context.traceSeed(),
                        "condition_random_chance:" + channelSuffix,
                        condition.conditionIndex());
                yield roll <= chance;
            }
            case MOON_PHASE -> context.worldDay().isPresent()
                    && condition.intValues().contains((int) Math.floorMod(context.worldDay().getAsLong(), 8L));
            case STRUCTURE_CONTEXT -> {
                if (condition.text().isEmpty()) {
                    yield context.structureContext().isPresent();
                }
                yield context.structureContext().map(value -> value.equals(condition.text().get())).orElse(false);
            }
            case INVASION_ACTIVE -> {
                if (!context.invasionActive()) {
                    yield false;
                }
                if (condition.resourceRef().isEmpty()) {
                    yield true;
                }
                yield context.invasionProfileId().map(condition.resourceRef().get()::equals).orElse(false);
            }
            case INVASION_TAG -> condition.text()
                    .map(context.invasionTags()::contains)
                    .orElse(false);
            case LOOT_TABLE -> condition.resourceRef()
                    .map(table -> context.lootTableId().map(table::equals).orElse(false))
                    .orElse(false);
            case EVENT_TYPE -> condition.text()
                    .map(expected -> expected.equals(context.sourceEventId().toString()))
                    .orElse(false);
            case UNSUPPORTED -> false;
        };
    }

    private List<CompiledLootProfile> candidateProfiles(
            CompiledLootGraph compiled,
            LootContextSnapshot context,
            Optional<ResourceLocation> forcedProfileId) {
        if (forcedProfileId.isPresent()) {
            CompiledLootProfile forced = compiled.profilesById().get(forcedProfileId.get());
            return forced == null ? List.of() : List.of(forced);
        }
        if (context.lootTableId().isEmpty()) {
            return List.of();
        }
        return compiled.profilesByTargetLootTable().getOrDefault(context.lootTableId().get(), List.of());
    }

    private LootContextSnapshot buildEntityKillContext(
            ServerLevel level,
            LivingEntity target,
            ServerPlayer killer,
            boolean debug) {
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        Optional<ResourceLocation> biomeId = resolveBiome(level, target.blockPosition());
        Set<ResourceLocation> entityTags = collectEntityTags(target.getType());
        boolean entityIsBoss = datapackService.currentSnapshot()
                .data()
                .bossClassifier()
                .isBoss(new EntityContext(entityTypeId, entityTags, mobCategory(target)));
        WorldAwakenedMutationProvenance.MutationProvenanceView provenance =
                WorldAwakenedMutationProvenance.read(target.getPersistentData());
        Set<ResourceLocation> mutationTags = new LinkedHashSet<>(provenance.mutationIds());
        Set<ResourceLocation> worldStages = stageService.getUnlockedStages(level);
        Set<ResourceLocation> playerStages = killer == null ? Set.of() : stageService.getUnlockedStages(level, killer);
        ResourceLocation lootTableId = target.getLootTable().location();
        Map<String, Boolean> toggles = configToggles();

        return new LootContextSnapshot(
                debug ? EVENT_DEBUG_EVALUATE : EVENT_ENTITY_KILLED,
                LootTargetType.ENTITY,
                entityTypeId,
                Optional.ofNullable(lootTableId),
                Optional.of(entityTypeId),
                entityTags,
                entityIsBoss,
                provenance.hasProvenance(),
                Set.copyOf(mutationTags),
                killer == null ? Optional.empty() : Optional.of(killer.getUUID().toString()),
                killer == null ? Optional.empty() : Optional.of(killer.getGameProfile().getName()),
                level.dimension().location(),
                biomeId,
                Optional.empty(),
                OptionalLong.of(Math.max(0L, level.getDayTime() / 24000L)),
                Set.copyOf(worldStages),
                Set.copyOf(playerStages),
                stageService.stageRegistry(),
                false,
                Optional.empty(),
                Set.of(),
                apotheosisCompatActive(),
                loadedMods(),
                toggles,
                Long.toUnsignedString(level.getGameTime()),
                debug);
    }

    private Optional<LootContextSnapshot> buildDebugContext(
            ServerLevel level,
            LootTargetType targetType,
            ResourceLocation targetId,
            ServerPlayer player) {
        Set<ResourceLocation> worldStages = stageService.getUnlockedStages(level);
        Set<ResourceLocation> playerStages = player == null ? Set.of() : stageService.getUnlockedStages(level, player);
        Optional<ResourceLocation> playerBiomeId = player == null
                ? Optional.empty()
                : resolveBiome(level, player.blockPosition());
        Map<String, Boolean> toggles = configToggles();
        ResourceLocation eventType = targetType == LootTargetType.INVASION_REWARD
                ? EVENT_INVASION_COMPLETED
                : EVENT_DEBUG_EVALUATE;

        return switch (targetType) {
            case ENTITY -> {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(targetId)) {
                    yield Optional.empty();
                }
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(targetId);
                Set<ResourceLocation> entityTags = collectEntityTags(entityType);
                boolean isBoss = datapackService.currentSnapshot()
                        .data()
                        .bossClassifier()
                        .isBoss(new EntityContext(targetId, entityTags, "debug"));
                Optional<ResourceLocation> lootTable = Optional.empty();
                try {
                    lootTable = Optional.ofNullable(entityType.getDefaultLootTable().location());
                } catch (Exception ignored) {
                }
                yield Optional.of(new LootContextSnapshot(
                        eventType,
                        targetType,
                        targetId,
                        lootTable,
                        Optional.of(targetId),
                        entityTags,
                        isBoss,
                        false,
                        Set.of(),
                        player == null ? Optional.empty() : Optional.of(player.getUUID().toString()),
                        player == null ? Optional.empty() : Optional.of(player.getGameProfile().getName()),
                        level.dimension().location(),
                        playerBiomeId,
                        Optional.empty(),
                        OptionalLong.of(Math.max(0L, level.getDayTime() / 24000L)),
                        Set.copyOf(worldStages),
                        Set.copyOf(playerStages),
                        stageService.stageRegistry(),
                        false,
                        Optional.empty(),
                        Set.of(),
                        apotheosisCompatActive(),
                        loadedMods(),
                        toggles,
                        "debug:" + level.getGameTime(),
                        true));
            }
            case LOOT_TABLE, CHEST, STRUCTURE -> Optional.of(new LootContextSnapshot(
                    eventType,
                    targetType,
                    targetId,
                    Optional.of(targetId),
                    Optional.empty(),
                    Set.of(),
                    false,
                    false,
                    Set.of(),
                    player == null ? Optional.empty() : Optional.of(player.getUUID().toString()),
                    player == null ? Optional.empty() : Optional.of(player.getGameProfile().getName()),
                    level.dimension().location(),
                    playerBiomeId,
                    targetType == LootTargetType.STRUCTURE ? Optional.of(targetId.toString()) : Optional.empty(),
                    OptionalLong.of(Math.max(0L, level.getDayTime() / 24000L)),
                    Set.copyOf(worldStages),
                    Set.copyOf(playerStages),
                    stageService.stageRegistry(),
                    false,
                    Optional.empty(),
                    Set.of(),
                    apotheosisCompatActive(),
                    loadedMods(),
                    toggles,
                    "debug:" + level.getGameTime(),
                    true));
            case INVASION_REWARD -> Optional.of(new LootContextSnapshot(
                    eventType,
                    targetType,
                    targetId,
                    Optional.of(targetId),
                    Optional.empty(),
                    Set.of(),
                    false,
                    false,
                    Set.of(),
                    player == null ? Optional.empty() : Optional.of(player.getUUID().toString()),
                    player == null ? Optional.empty() : Optional.of(player.getGameProfile().getName()),
                    level.dimension().location(),
                    playerBiomeId,
                    Optional.empty(),
                    OptionalLong.of(Math.max(0L, level.getDayTime() / 24000L)),
                    Set.copyOf(worldStages),
                    Set.copyOf(playerStages),
                    stageService.stageRegistry(),
                    true,
                    Optional.of(targetId),
                    Set.of(),
                    apotheosisCompatActive(),
                    loadedMods(),
                    toggles,
                    "debug:" + level.getGameTime(),
                    true));
        };
    }

    private static String mobCategory(LivingEntity entity) {
        return entity.getType().getCategory().getName();
    }

    private static Optional<ResourceLocation> resolveBiome(ServerLevel level, BlockPos position) {
        return level.getBiome(position)
                .unwrapKey()
                .map(ResourceKey::location);
    }

    private static Set<ResourceLocation> collectEntityTags(EntityType<?> entityType) {
        LinkedHashSet<ResourceLocation> tags = new LinkedHashSet<>();
        entityType.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location()));
        return Set.copyOf(tags);
    }

    private CompiledLootGraph compiledLoot() {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        CachedCompiledLoot cached = cache.get();
        if (cached.generation() == snapshot.generation()) {
            return cached.graph();
        }

        CompiledLootGraph compiled = compileGraph(snapshot);
        cache.set(new CachedCompiledLoot(snapshot.generation(), compiled));
        return compiled;
    }

    private CompiledLootGraph compileGraph(WorldAwakenedDatapackSnapshot snapshot) {
        List<LootProfileDefinition> definitions = snapshot.data().lootProfiles().values().stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .toList();
        Map<ResourceLocation, CompiledLootProfile> byId = new LinkedHashMap<>();
        Map<ResourceLocation, List<CompiledLootProfile>> byTarget = new LinkedHashMap<>();

        for (LootProfileDefinition definition : definitions) {
            if (!definition.enabled()) {
                continue;
            }
            CompiledLootProfile profile = compileProfile(definition);
            if (profile.entries().isEmpty() || profile.targetLootTables().isEmpty()) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.VALIDATION,
                        "Loot profile {} compiled with no valid entries or targets and will be ignored",
                        definition.id());
                continue;
            }
            byId.put(profile.id(), profile);
            for (ResourceLocation target : profile.targetLootTables()) {
                byTarget.computeIfAbsent(target, ignored -> new ArrayList<>()).add(profile);
            }
        }

        for (List<CompiledLootProfile> profiles : byTarget.values()) {
            profiles.sort(PROFILE_ORDER);
        }

        Set<ResourceLocation> apotheosisSensitiveTargets = compileApotheosisSensitiveTargets(snapshot.data().integrationProfiles().values());
        return new CompiledLootGraph(
                Map.copyOf(byId),
                freezeTargetIndex(byTarget),
                List.copyOf(byId.keySet()),
                Set.copyOf(apotheosisSensitiveTargets));
    }

    private static Map<ResourceLocation, List<CompiledLootProfile>> freezeTargetIndex(
            Map<ResourceLocation, List<CompiledLootProfile>> byTarget) {
        Map<ResourceLocation, List<CompiledLootProfile>> frozen = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<CompiledLootProfile>> entry : byTarget.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static CompiledLootProfile compileProfile(LootProfileDefinition definition) {
        List<CompiledCondition> conditions = compileConditions(definition.conditions());
        List<CompiledCondition> modConditions = compileConditions(definition.modConditions());
        StageFilter stageFilter = StageFilter.compile(definition.stageFilters());
        List<CompiledLootEntry> entries = compileEntries(definition.entries());
        LinkedHashSet<ResourceLocation> targets = new LinkedHashSet<>(definition.targetLootTables());

        return new CompiledLootProfile(
                definition.id(),
                Set.copyOf(targets),
                List.copyOf(conditions),
                List.copyOf(modConditions),
                stageFilter,
                definition.apotheosisTierFilters().isPresent(),
                definition.replaceMode(),
                entries,
                definition.configGate().map(value -> value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank()));
    }

    private static List<CompiledLootEntry> compileEntries(List<JsonElement> entries) {
        List<CompiledLootEntry> compiled = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            JsonElement raw = entries.get(index);
            if (!raw.isJsonObject()) {
                continue;
            }
            JsonObject object = raw.getAsJsonObject();
            if (!isNodeEnabled(object)) {
                continue;
            }
            String entryType = readString(object, "type").orElse("item").toLowerCase(Locale.ROOT);
            if (!"item".equals(entryType)) {
                continue;
            }
            Optional<ResourceLocation> itemId = readResourceLocation(object, "item", "id");
            if (itemId.isEmpty()) {
                continue;
            }
            int weight = Math.max(1, readInt(object, "weight").orElse(1));
            int min = Math.max(1, readInt(object, "min", "count").orElse(1));
            int max = Math.max(min, readInt(object, "max").orElse(min));
            compiled.add(new CompiledLootEntry(itemId.get(), weight, min, max, index));
        }
        return List.copyOf(compiled);
    }

    private static List<CompiledCondition> compileConditions(List<JsonElement> conditions) {
        List<CompiledCondition> compiled = new ArrayList<>();
        for (int index = 0; index < conditions.size(); index++) {
            JsonElement node = conditions.get(index);
            compileCondition(node, index).ifPresent(compiled::add);
        }
        return List.copyOf(compiled);
    }

    private static Optional<CompiledCondition> compileCondition(JsonElement node, int conditionIndex) {
        if (!node.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = node.getAsJsonObject();
        if (!isNodeEnabled(object)) {
            return Optional.empty();
        }
        Optional<ResourceLocation> typeId = readResourceLocation(object, "type");
        if (typeId.isEmpty()) {
            return Optional.empty();
        }
        JsonObject parameters = readParametersObject(object);
        String path = typeId.get().getPath().toLowerCase(Locale.ROOT);
        ConditionKind kind = switch (path) {
            case "stage_unlocked" -> ConditionKind.STAGE_UNLOCKED;
            case "stage_locked" -> ConditionKind.STAGE_LOCKED;
            case "current_dimension" -> ConditionKind.CURRENT_DIMENSION;
            case "current_biome" -> ConditionKind.CURRENT_BIOME;
            case "world_day_gte" -> ConditionKind.WORLD_DAY_GTE;
            case "entity_type" -> ConditionKind.ENTITY_TYPE;
            case "entity_tag" -> ConditionKind.ENTITY_TAG;
            case "entity_not_boss" -> ConditionKind.ENTITY_NOT_BOSS;
            case "entity_is_mutated" -> ConditionKind.ENTITY_IS_MUTATED;
            case "loaded_mod" -> ConditionKind.LOADED_MOD;
            case "config_toggle_enabled" -> ConditionKind.CONFIG_TOGGLE_ENABLED;
            case "random_chance" -> ConditionKind.RANDOM_CHANCE;
            case "moon_phase" -> ConditionKind.MOON_PHASE;
            case "structure_context" -> ConditionKind.STRUCTURE_CONTEXT;
            case "invasion_active" -> ConditionKind.INVASION_ACTIVE;
            case "invasion_tag" -> ConditionKind.INVASION_TAG;
            case "loot_table" -> ConditionKind.LOOT_TABLE;
            case "event_type" -> ConditionKind.EVENT_TYPE;
            default -> ConditionKind.UNSUPPORTED;
        };

        Optional<ResourceLocation> resourceRef = switch (kind) {
            case STAGE_UNLOCKED, STAGE_LOCKED -> readResourceLocation(parameters, "stage");
            case CURRENT_DIMENSION -> readResourceLocation(parameters, "dimension");
            case CURRENT_BIOME -> readResourceLocation(parameters, "biome");
            case ENTITY_TYPE -> readResourceLocation(parameters, "entity");
            case INVASION_ACTIVE -> readResourceLocation(parameters, "profile_id", "profile");
            case LOOT_TABLE -> readResourceLocation(parameters, "id");
            default -> Optional.empty();
        };
        Optional<TagMatcher> tagMatcher = kind == ConditionKind.ENTITY_TAG
                ? readTagMatcher(parameters, "tag")
                : Optional.empty();
        Optional<String> text = switch (kind) {
            case LOADED_MOD -> readString(parameters, "mod").map(value -> value.toLowerCase(Locale.ROOT));
            case CONFIG_TOGGLE_ENABLED -> readString(parameters, "config_gate").map(value -> value.toLowerCase(Locale.ROOT));
            case STRUCTURE_CONTEXT -> readString(parameters, "structure");
            case INVASION_TAG -> readString(parameters, "tag");
            case EVENT_TYPE -> readString(parameters, "event");
            default -> Optional.empty();
        };
        OptionalDouble value = switch (kind) {
            case WORLD_DAY_GTE -> readDouble(parameters, "value");
            case RANDOM_CHANCE -> readDouble(parameters, "chance");
            default -> OptionalDouble.empty();
        };
        Set<Integer> moonPhases = kind == ConditionKind.MOON_PHASE ? parseMoonPhases(parameters) : Set.of();
        return Optional.of(new CompiledCondition(
                typeId.get(),
                kind,
                resourceRef,
                tagMatcher,
                text,
                value,
                moonPhases,
                conditionIndex));
    }

    private static Set<ResourceLocation> compileApotheosisSensitiveTargets(
            Collection<IntegrationProfileDefinition> integrationProfiles) {
        LinkedHashSet<ResourceLocation> targets = new LinkedHashSet<>();
        for (IntegrationProfileDefinition profile : integrationProfiles) {
            if (!"apotheosis".equals(profile.modId().toLowerCase(Locale.ROOT))) {
                continue;
            }
            targets.addAll(profile.lootTargets());
        }
        return Set.copyOf(targets);
    }

    private static Set<ResourceLocation> activeStageSnapshot(LootContextSnapshot context) {
        if (context.playerUuid().isPresent() && !context.playerStageSnapshot().isEmpty()) {
            return context.playerStageSnapshot();
        }
        return context.worldStageSnapshot();
    }

    private static boolean containsStage(
            Set<ResourceLocation> activeStages,
            WorldAwakenedStageRegistry stageRegistry,
            ResourceLocation requestedId) {
        Optional<ResourceLocation> canonical = stageRegistry.resolveCanonicalId(requestedId);
        return activeStages.contains(canonical.orElse(requestedId));
    }

    private boolean isApotheosisSensitiveTarget(CompiledLootGraph graph, ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return false;
        }
        if (graph.apotheosisSensitiveTargets().contains(lootTableId)) {
            return true;
        }
        return "apotheosis".equals(lootTableId.getNamespace());
    }

    private static Set<String> loadedMods() {
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        ModList.get().getMods().forEach(modInfo -> mods.add(modInfo.getModId().toLowerCase(Locale.ROOT)));
        return Set.copyOf(mods);
    }

    private static boolean apotheosisCompatActive() {
        return ModList.get().isLoaded("apotheosis")
                && WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get();
    }

    private static Map<String, Boolean> configToggles() {
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        toggles.put("general.enable_mod", WorldAwakenedCommonConfig.ENABLE_MOD.get());
        toggles.put("general.debug_logging", WorldAwakenedCommonConfig.DEBUG_LOGGING.get());
        toggles.put("general.enable_debug_commands", WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get());
        toggles.put("loot.enable_loot_evolution", WorldAwakenedCommonConfig.ENABLE_LOOT_EVOLUTION.get());
        toggles.put("loot.inject_only", WorldAwakenedCommonConfig.INJECT_ONLY.get());
        toggles.put("loot.allow_entry_replacement", WorldAwakenedCommonConfig.ALLOW_ENTRY_REPLACEMENT.get());
        toggles.put("compat.apotheosis.enabled", WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get());
        toggles.put("compat.apotheosis.allow_world_tier_loot_scaling", WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_LOOT_SCALING.get());
        return Map.copyOf(toggles);
    }

    private String nextTraceId() {
        long value = traceCounter.incrementAndGet();
        return "WA-L" + Long.toHexString(value).toUpperCase(Locale.ROOT);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("worldawakened", path);
    }

    private static String formatRewardItems(List<RewardItem> items) {
        if (items.isEmpty()) {
            return "<none>";
        }
        return items.stream()
                .map(item -> item.itemId() + "x" + item.count())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static double deterministicRoll(
            LootContextSnapshot context,
            ResourceLocation ownerId,
            String traceSeed,
            String channel,
            int extraIndex) {
        long seed = 0x9E3779B97F4A7C15L;
        seed = mix(seed, context.sourceEventId().toString());
        seed = mix(seed, context.targetType().serialized());
        seed = mix(seed, context.targetId().toString());
        seed = mix(seed, context.lootTableId().map(ResourceLocation::toString).orElse("<none>"));
        seed = mix(seed, context.entityTypeId().map(ResourceLocation::toString).orElse("<none>"));
        seed = mix(seed, context.playerUuid().orElse("<none>"));
        seed = mix(seed, context.dimensionId().toString());
        seed = mix(seed, context.worldDay().isPresent() ? context.worldDay().getAsLong() : 0L);
        seed = mix(seed, traceSeed);
        seed = mix(seed, ownerId.toString());
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

    private static JsonObject readParametersObject(JsonObject node) {
        if (!node.has("parameters") || !node.get("parameters").isJsonObject()) {
            return new JsonObject();
        }
        return node.getAsJsonObject("parameters");
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
            if (!object.has(key)
                    || !object.get(key).isJsonPrimitive()
                    || !object.get(key).getAsJsonPrimitive().isNumber()) {
                continue;
            }
            return OptionalDouble.of(object.get(key).getAsDouble());
        }
        return OptionalDouble.empty();
    }

    private static Optional<Integer> readInt(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key)
                    || !object.get(key).isJsonPrimitive()
                    || !object.get(key).getAsJsonPrimitive().isNumber()) {
                continue;
            }
            return Optional.of(object.get(key).getAsInt());
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

    private static Optional<TagMatcher> readTagMatcher(JsonObject object, String key) {
        return readString(object, key).flatMap(TagMatcher::compile);
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

    public enum LootTargetType {
        LOOT_TABLE("loot_table"),
        ENTITY("entity"),
        CHEST("chest"),
        STRUCTURE("structure"),
        INVASION_REWARD("invasion_reward");

        private final String serialized;

        LootTargetType(String serialized) {
            this.serialized = serialized;
        }

        public String serialized() {
            return serialized;
        }

        public static Optional<LootTargetType> fromString(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (LootTargetType value : values()) {
                if (value.serialized.equals(normalized)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        public static List<String> serializedValues() {
            return List.of(
                    LOOT_TABLE.serialized,
                    ENTITY.serialized,
                    CHEST.serialized,
                    STRUCTURE.serialized,
                    INVASION_REWARD.serialized);
        }
    }

    public record LootRunResult(
            String traceId,
            ResourceLocation sourceEventId,
            LootContextSnapshot context,
            List<ResourceLocation> candidateProfiles,
            List<ProfileDecision> profileDecisions,
            List<ResolvedOperation> operations,
            List<RewardItem> finalOutcome,
            boolean appliedOnce,
            boolean liveApplied,
            boolean skipped,
            String skipCode,
            String skipDetail) {
        static LootRunResult skipped(
                String traceId,
                ResourceLocation sourceEventId,
                LootContextSnapshot context,
                String code,
                String detail) {
            return new LootRunResult(
                    traceId,
                    sourceEventId,
                    context,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    false,
                    true,
                    code,
                    detail);
        }

        LootRunResult withLiveApplied(boolean changed, List<RewardItem> updatedOutcome) {
            return new LootRunResult(
                    traceId,
                    sourceEventId,
                    context,
                    candidateProfiles,
                    profileDecisions,
                    operations,
                    List.copyOf(updatedOutcome),
                    appliedOnce,
                    changed,
                    skipped,
                    skipCode,
                    skipDetail);
        }
    }

    public record LootContextSnapshot(
            ResourceLocation sourceEventId,
            LootTargetType targetType,
            ResourceLocation targetId,
            Optional<ResourceLocation> lootTableId,
            Optional<ResourceLocation> entityTypeId,
            Set<ResourceLocation> entityTags,
            boolean entityIsBoss,
            boolean entityIsMutated,
            Set<ResourceLocation> mutationTags,
            Optional<String> playerUuid,
            Optional<String> playerName,
            ResourceLocation dimensionId,
            Optional<ResourceLocation> biomeId,
            Optional<String> structureContext,
            OptionalLong worldDay,
            Set<ResourceLocation> worldStageSnapshot,
            Set<ResourceLocation> playerStageSnapshot,
            WorldAwakenedStageRegistry stageRegistry,
            boolean invasionActive,
            Optional<ResourceLocation> invasionProfileId,
            Set<String> invasionTags,
            boolean apotheosisCompatActive,
            Set<String> loadedMods,
            Map<String, Boolean> configToggles,
            String traceSeed,
            boolean debugEvaluation) {
        static LootContextSnapshot empty() {
            return new LootContextSnapshot(
                    EVENT_DEBUG_EVALUATE,
                    LootTargetType.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath("worldawakened", "empty"),
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(),
                    false,
                    false,
                    Set.of(),
                    Optional.empty(),
                    Optional.empty(),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                    Optional.empty(),
                    Optional.empty(),
                    OptionalLong.empty(),
                    Set.of(),
                    Set.of(),
                    WorldAwakenedStageRegistry.empty(),
                    false,
                    Optional.empty(),
                    Set.of(),
                    false,
                    Set.of(),
                    Map.of(),
                    "none",
                    true);
        }
    }

    public record ProfileDecision(
            ResourceLocation profileId,
            boolean matched,
            String reasonCategory,
            String detail,
            String diagnosticCode,
            LootReplaceMode requestedMode,
            LootReplaceMode resolvedMode,
            boolean apotheosisSensitiveTarget,
            Optional<RewardItem> selectedReward,
            String fallbackAction) {
        static ProfileDecision rejected(
                ResourceLocation profileId,
                String reasonCategory,
                String detail,
                String diagnosticCode,
                LootReplaceMode requestedMode,
                LootReplaceMode resolvedMode,
                boolean sensitiveTarget,
                String fallbackAction) {
            return new ProfileDecision(
                    profileId,
                    false,
                    reasonCategory,
                    detail,
                    diagnosticCode,
                    requestedMode,
                    resolvedMode,
                    sensitiveTarget,
                    Optional.empty(),
                    fallbackAction);
        }

        static ProfileDecision matched(
                ResourceLocation profileId,
                LootReplaceMode resolvedMode,
                LootReplaceMode requestedMode,
                RewardItem selectedReward,
                boolean sensitiveTarget,
                String fallbackAction,
                String diagnosticCode,
                String detail) {
            return new ProfileDecision(
                    profileId,
                    true,
                    "matched",
                    detail,
                    diagnosticCode,
                    requestedMode,
                    resolvedMode,
                    sensitiveTarget,
                    Optional.of(selectedReward),
                    fallbackAction);
        }
    }

    public record RewardItem(ResourceLocation itemId, int count) {
    }

    public record ResolvedOperation(
            ResourceLocation profileId,
            LootReplaceMode mode,
            RewardItem selectedReward,
            String fallbackAction) {
    }

    private record ModeResolution(
            boolean blocked,
            LootReplaceMode resolvedMode,
            String diagnosticCode,
            String detail,
            String fallbackAction) {
        static ModeResolution allowed(
                LootReplaceMode resolvedMode,
                String diagnosticCode,
                String detail,
                String fallbackAction) {
            return new ModeResolution(false, resolvedMode, diagnosticCode, detail, fallbackAction);
        }

        static ModeResolution blocked(
                LootReplaceMode requestedMode,
                String diagnosticCode,
                String detail,
                String fallbackAction) {
            return new ModeResolution(true, requestedMode, diagnosticCode, detail, fallbackAction);
        }
    }

    private record CompiledLootGraph(
            Map<ResourceLocation, CompiledLootProfile> profilesById,
            Map<ResourceLocation, List<CompiledLootProfile>> profilesByTargetLootTable,
            List<ResourceLocation> orderedProfileIds,
            Set<ResourceLocation> apotheosisSensitiveTargets) {
        static CompiledLootGraph empty() {
            return new CompiledLootGraph(Map.of(), Map.of(), List.of(), Set.of());
        }
    }

    private record CachedCompiledLoot(
            long generation,
            CompiledLootGraph graph) {
    }

    private record CompiledLootProfile(
            ResourceLocation id,
            Set<ResourceLocation> targetLootTables,
            List<CompiledCondition> conditions,
            List<CompiledCondition> modConditions,
            StageFilter stageFilter,
            boolean hasApotheosisTierFilter,
            LootReplaceMode replaceMode,
            List<CompiledLootEntry> entries,
            Optional<String> configGate) {
    }

    private record CompiledLootEntry(
            ResourceLocation itemId,
            int weight,
            int minCount,
            int maxCount,
            int authoredIndex) {
    }

    private record CompiledCondition(
            ResourceLocation typeId,
            ConditionKind kind,
            Optional<ResourceLocation> resourceRef,
            Optional<TagMatcher> tagMatcher,
            Optional<String> text,
            OptionalDouble value,
            Set<Integer> intValues,
            int conditionIndex) {
    }

    private enum ConditionKind {
        STAGE_UNLOCKED,
        STAGE_LOCKED,
        CURRENT_DIMENSION,
        CURRENT_BIOME,
        WORLD_DAY_GTE,
        ENTITY_TYPE,
        ENTITY_TAG,
        ENTITY_NOT_BOSS,
        ENTITY_IS_MUTATED,
        LOADED_MOD,
        CONFIG_TOGGLE_ENABLED,
        RANDOM_CHANCE,
        MOON_PHASE,
        STRUCTURE_CONTEXT,
        INVASION_ACTIVE,
        INVASION_TAG,
        LOOT_TABLE,
        EVENT_TYPE,
        UNSUPPORTED
    }

    private record EntityContext(
            ResourceLocation entityId,
            Set<ResourceLocation> entityTags,
            String mobCategory) implements WorldAwakenedEntityContextView {
    }

    private static final class StageFilter {
        private final Set<ResourceLocation> requiredAll;
        private final Set<ResourceLocation> requiredAny;
        private final Set<ResourceLocation> excluded;

        private StageFilter(Set<ResourceLocation> requiredAll, Set<ResourceLocation> requiredAny, Set<ResourceLocation> excluded) {
            this.requiredAll = requiredAll;
            this.requiredAny = requiredAny;
            this.excluded = excluded;
        }

        static StageFilter compile(Optional<JsonElement> raw) {
            if (raw.isEmpty()) {
                return empty();
            }
            JsonElement element = raw.get();
            LinkedHashSet<ResourceLocation> requiredAll = new LinkedHashSet<>();
            LinkedHashSet<ResourceLocation> requiredAny = new LinkedHashSet<>();
            LinkedHashSet<ResourceLocation> excluded = new LinkedHashSet<>();

            if (element.isJsonPrimitive()) {
                ResourceLocation stageId = ResourceLocation.tryParse(element.getAsString());
                if (stageId != null) {
                    requiredAll.add(stageId);
                }
            } else if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) {
                    if (!child.isJsonPrimitive()) {
                        continue;
                    }
                    ResourceLocation stageId = ResourceLocation.tryParse(child.getAsString());
                    if (stageId != null) {
                        requiredAll.add(stageId);
                    }
                }
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                readStageSet(object, requiredAll, "stage", "required_stage", "required_stages", "stages", "all_of");
                readStageSet(object, requiredAny, "required_any", "any_of", "one_of");
                readStageSet(object, excluded, "excluded", "excluded_stages", "none_of");
            }
            return new StageFilter(Set.copyOf(requiredAll), Set.copyOf(requiredAny), Set.copyOf(excluded));
        }

        static StageFilter empty() {
            return new StageFilter(Set.of(), Set.of(), Set.of());
        }

        boolean matches(Set<ResourceLocation> activeStages, WorldAwakenedStageRegistry stageRegistry) {
            for (ResourceLocation requiredStage : requiredAll) {
                if (!containsStage(activeStages, stageRegistry, requiredStage)) {
                    return false;
                }
            }
            if (!requiredAny.isEmpty()) {
                boolean anyMatched = false;
                for (ResourceLocation stage : requiredAny) {
                    if (containsStage(activeStages, stageRegistry, stage)) {
                        anyMatched = true;
                        break;
                    }
                }
                if (!anyMatched) {
                    return false;
                }
            }
            for (ResourceLocation stage : excluded) {
                if (containsStage(activeStages, stageRegistry, stage)) {
                    return false;
                }
            }
            return true;
        }

        private static void readStageSet(JsonObject object, Set<ResourceLocation> collector, String... keys) {
            for (String key : keys) {
                if (!object.has(key)) {
                    continue;
                }
                JsonElement raw = object.get(key);
                if (raw.isJsonPrimitive()) {
                    ResourceLocation parsed = ResourceLocation.tryParse(raw.getAsString());
                    if (parsed != null) {
                        collector.add(parsed);
                    }
                    continue;
                }
                if (!raw.isJsonArray()) {
                    continue;
                }
                for (JsonElement element : raw.getAsJsonArray()) {
                    if (!element.isJsonPrimitive()) {
                        continue;
                    }
                    ResourceLocation parsed = ResourceLocation.tryParse(element.getAsString());
                    if (parsed != null) {
                        collector.add(parsed);
                    }
                }
            }
        }
    }

    private record TagMatcher(
            Optional<ResourceLocation> exactTag,
            Optional<String> namespaceWildcard) {
        static Optional<TagMatcher> compile(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.endsWith(":*")) {
                String namespace = value.substring(0, value.length() - 2).toLowerCase(Locale.ROOT);
                if (namespace.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new TagMatcher(Optional.empty(), Optional.of(namespace)));
            }
            ResourceLocation exact = ResourceLocation.tryParse(value);
            if (exact == null) {
                return Optional.empty();
            }
            return Optional.of(new TagMatcher(Optional.of(exact), Optional.empty()));
        }

        boolean matches(Set<ResourceLocation> entityTags) {
            if (exactTag.isPresent()) {
                return entityTags.contains(exactTag.get());
            }
            if (namespaceWildcard.isPresent()) {
                String namespace = namespaceWildcard.get();
                for (ResourceLocation tag : entityTags) {
                    if (tag.getNamespace().equals(namespace)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
