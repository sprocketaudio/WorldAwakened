package net.sprocketgames.worldawakened.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedMutableRuleState;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationResult;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationStatus;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedWorldProgressionSavedData;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRejectionReason;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRecursionGuard;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleEngine;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleDecision;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleEvaluation;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleMatchContext;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedMatchedRule;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleStateSnapshot;

public final class WorldAwakenedRuleService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final WorldAwakenedDatapackService datapackService;
    private final WorldAwakenedStageService stageService;
    private final WorldAwakenedAscensionService ascensionService;
    private final AtomicReference<CachedCompiledRules> cache = new AtomicReference<>(new CachedCompiledRules(0L, List.of()));
    private final AtomicLong traceCounter = new AtomicLong(0L);

    public WorldAwakenedRuleService(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedAscensionService ascensionService) {
        this.datapackService = datapackService;
        this.stageService = stageService;
        this.ascensionService = ascensionService;
    }

    public WorldAwakenedRuleRunResult evaluate(RuleEventContext context) {
        String traceId = nextTraceId();
        List<WorldAwakenedRuleEngine.CompiledRule> compiledRules = compiledRules();
        if (compiledRules.isEmpty()) {
            return WorldAwakenedRuleRunResult.empty(traceId);
        }

        WorldAwakenedStageRegistry stageRegistry = stageService.stageRegistry();
        RuleStateSnapshots snapshots = readSnapshots(context.level(), context.player());

        WorldAwakenedRuleMatchContext matchContext = buildMatchContext(context, snapshots);
        WorldAwakenedRuleEvaluation evaluation = WorldAwakenedRuleEngine.evaluate(
                compiledRules,
                stageRegistry,
                matchContext,
                true);

        if (evaluation.matchedRules().isEmpty()) {
            debugTrace(traceId, context.eventType(), evaluation);
            return new WorldAwakenedRuleRunResult(traceId, evaluation.evaluatedRules(), 0, 0, 0, 0, 0, 0, 0);
        }

        int executed = 0;
        int unlocks = 0;
        int locks = 0;
        int warnings = 0;
        int consumed = 0;
        int deferred = 0;

        WorldAwakenedRecursionGuard recursionGuard = new WorldAwakenedRecursionGuard(64);
        for (WorldAwakenedMatchedRule matched : evaluation.matchedRules()) {
            String ruleKey = matched.rule().id().toString();
            if (!recursionGuard.tryEnter(ruleKey)) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Trace {} blocked recursive rule branch for {}",
                        traceId,
                        ruleKey);
                continue;
            }

            LiveStateTarget target = resolveLiveState(context, matched.resolvedScope());
            if (target.ruleState() == null) {
                recursionGuard.exit(ruleKey);
                continue;
            }

            executed++;
            boolean markConsumed = false;
            for (WorldAwakenedRuleEngine.CompiledAction action : matched.rule().actions()) {
                switch (action.kind()) {
                    case UNLOCK_STAGE -> {
                        if (action.resourceRef().isEmpty()) {
                            continue;
                        }
                        WorldAwakenedStageMutationResult result = stageService.unlockStage(
                                context.level(),
                                target.scopedPlayer(),
                                action.resourceRef().get(),
                                "rule:" + matched.rule().id());
                        if (result.status() == WorldAwakenedStageMutationStatus.UNLOCKED) {
                            unlocks++;
                        }
                    }
                    case LOCK_STAGE -> {
                        if (action.resourceRef().isEmpty()) {
                            continue;
                        }
                        WorldAwakenedStageMutationResult result = stageService.lockStage(
                                context.level(),
                                target.scopedPlayer(),
                                action.resourceRef().get());
                        if (result.status() == WorldAwakenedStageMutationStatus.LOCKED) {
                            locks++;
                        }
                    }
                    case SEND_WARNING_MESSAGE -> {
                        if (target.scopedPlayer() == null || action.text().isEmpty()) {
                            continue;
                        }
                        target.scopedPlayer().sendSystemMessage(net.minecraft.network.chat.Component.literal(action.text().get()));
                        warnings++;
                    }
                    case MARK_RULE_CONSUMED -> {
                        markConsumed = true;
                        target.ruleState().consumedRules().add(matched.stateKey());
                        target.ruleState().markDirty();
                        consumed++;
                    }
                    case GRANT_ASCENSION_OFFER -> {
                        if (action.resourceRef().isEmpty() || context.player() == null) {
                            continue;
                        }
                        ascensionService.grantOfferFromRule(
                                context.level(),
                                context.player(),
                                action.resourceRef().get(),
                                matched.rule().id().toString());
                    }
                    case SET_WORLD_SCALAR -> {
                        if (action.text().isEmpty() || action.value().isEmpty()) {
                            continue;
                        }
                        applyWorldScalar(context.level(), action.text().get(), action.operator(), action.value().getAsDouble());
                    }
                    case APPLY_MUTATOR_POOL,
                            APPLY_STAT_PROFILE,
                            INJECT_LOOT_PROFILE,
                            TRIGGER_INVASION_PROFILE,
                            DROP_REWARD_TABLE,
                            SET_TEMP_INVASION_MODIFIER,
                            UNSUPPORTED -> deferred++;
                }
            }

            if (matched.rule().cooldownMillis() > 0L) {
                target.ruleState().ruleCooldowns().put(matched.stateKey(), context.nowMillis() + matched.rule().cooldownMillis());
                target.ruleState().markDirty();
            }
            if (markConsumed && !target.ruleState().consumedRules().contains(matched.stateKey())) {
                target.ruleState().consumedRules().add(matched.stateKey());
                target.ruleState().markDirty();
            }

            recursionGuard.exit(ruleKey);
        }

        debugTrace(traceId, context.eventType(), evaluation);
        return new WorldAwakenedRuleRunResult(
                traceId,
                evaluation.evaluatedRules(),
                evaluation.matchedCount(),
                executed,
                unlocks,
                locks,
                warnings,
                consumed,
                deferred);
    }

    public List<ActiveRuleView> inspectActiveRules(ServerLevel level, ServerPlayer player) {
        RuleEventContext inspectContext = new RuleEventContext(
                "command_dump_active_rules",
                level,
                player,
                null,
                level.dimension().location(),
                Set.of(),
                false,
                false,
                System.currentTimeMillis(),
                level.getGameTime(),
                stageService.getUnlockedStages(level),
                player == null ? Set.of() : stageService.getUnlockedStages(level, player),
                Optional.empty());

        RuleStateSnapshots snapshots = readSnapshots(level, player);
        WorldAwakenedRuleMatchContext matchContext = buildMatchContext(inspectContext, snapshots);
        WorldAwakenedRuleEvaluation evaluation = WorldAwakenedRuleEngine.evaluate(
                compiledRules(),
                stageService.stageRegistry(),
                matchContext,
                false);

        List<ActiveRuleView> views = new ArrayList<>(evaluation.decisions().size());
        for (WorldAwakenedRuleDecision decision : evaluation.decisions()) {
            WorldAwakenedRuleStateSnapshot snapshot = decision.rule().executionScope() == net.sprocketgames.worldawakened.data.definition.ExecutionScope.PLAYER
                    ? snapshots.playerSnapshot()
                    : snapshots.worldSnapshot();
            long cooldownUntil = snapshot.cooldowns().getOrDefault(decision.stateKey(), 0L);
            long cooldownRemaining = Math.max(0L, cooldownUntil - inspectContext.nowMillis());
            boolean consumed = snapshot.consumedRules().contains(decision.stateKey());
            views.add(new ActiveRuleView(
                    decision.rule().id(),
                    decision.rule().executionScope(),
                    decision.rule().priority(),
                    decision.matched(),
                    decision.rejectionReason(),
                    decision.detail(),
                    cooldownRemaining,
                    consumed,
                    decision.chanceRoll().isPresent() ? decision.chanceRoll().getAsDouble() : 0.0D));
        }
        return List.copyOf(views);
    }

    private List<WorldAwakenedRuleEngine.CompiledRule> compiledRules() {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        CachedCompiledRules cached = cache.get();
        if (cached.generation() == snapshot.generation()) {
            return cached.rules();
        }

        List<WorldAwakenedRuleEngine.CompiledRule> compiled = WorldAwakenedRuleEngine.compile(snapshot.data().rules().values());
        cache.set(new CachedCompiledRules(snapshot.generation(), compiled));
        return compiled;
    }

    private RuleStateSnapshots readSnapshots(ServerLevel level, ServerPlayer player) {
        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        WorldAwakenedRuleStateSnapshot worldSnapshot = new WorldAwakenedRuleStateSnapshot(
                Map.copyOf(worldData.ruleCooldowns()),
                Set.copyOf(worldData.consumedRules()));

        if (player == null) {
            return new RuleStateSnapshots(worldSnapshot, WorldAwakenedRuleStateSnapshot.empty());
        }

        WorldAwakenedPlayerProgressionSavedData playerData = WorldAwakenedPlayerProgressionSavedData.get(level);
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState playerState = playerData.getOrCreate(player.getUUID());
        WorldAwakenedRuleStateSnapshot playerSnapshot = new WorldAwakenedRuleStateSnapshot(
                Map.copyOf(playerState.ruleCooldowns()),
                Set.copyOf(playerState.consumedRules()));
        return new RuleStateSnapshots(worldSnapshot, playerSnapshot);
    }

    private WorldAwakenedRuleMatchContext buildMatchContext(
            RuleEventContext context,
            RuleStateSnapshots snapshots) {
        Optional<ResourceLocation> biomeId = resolveBiome(context.level(), context.player(), context.entity());
        OptionalDouble localDifficulty = resolveLocalDifficulty(context.level(), context.player(), context.entity());
        OptionalDouble playerDistanceFromSpawn = resolvePlayerDistanceFromSpawn(context.level(), context.player());
        Optional<ResourceLocation> entityTypeId = context.entity() == null
                ? Optional.empty()
                : Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(context.entity().getType()));

        Set<ResourceLocation> ownedRewards = Set.of();
        Set<ResourceLocation> pendingOffers = Set.of();
        if (context.player() != null) {
            WorldAwakenedPlayerProgressionSavedData playerData = WorldAwakenedPlayerProgressionSavedData.get(context.level());
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerData.getOrCreate(context.player().getUUID());
            ownedRewards = Set.copyOf(state.chosenAscensionRewards());
            pendingOffers = Set.copyOf(state.pendingAscensionOffers());
        }

        return new WorldAwakenedRuleMatchContext(
                context.eventType(),
                context.targetedRuleId(),
                context.dimensionId(),
                biomeId,
                entityTypeId,
                Set.copyOf(context.entityTags()),
                context.player() == null ? Optional.empty() : Optional.of(context.player().getUUID().toString()),
                context.entity() == null ? Optional.empty() : Optional.of(context.entity().getStringUUID()),
                context.player() != null,
                context.entity() != null,
                context.entityIsBoss(),
                context.entityIsMutated(),
                OptionalLong.of(Math.max(0L, context.level().getDayTime() / 24000L)),
                playerDistanceFromSpawn,
                context.level().getServer().getPlayerCount(),
                localDifficulty,
                context.nowMillis(),
                context.gameTime(),
                Set.copyOf(context.worldStageSnapshot()),
                Set.copyOf(context.playerStageSnapshot()),
                snapshots.worldSnapshot(),
                snapshots.playerSnapshot(),
                ownedRewards,
                pendingOffers,
                loadedMods(),
                configToggles(),
                false,
                Optional.empty());
    }

    private static Optional<ResourceLocation> resolveBiome(ServerLevel level, ServerPlayer player, Entity entity) {
        BlockPos pos = resolveContextPos(player, entity);
        if (pos == null) {
            return Optional.empty();
        }
        Optional<ResourceKey<Biome>> key = level.getBiome(pos).unwrapKey();
        return key.map(ResourceKey::location);
    }

    private static OptionalDouble resolveLocalDifficulty(ServerLevel level, ServerPlayer player, Entity entity) {
        BlockPos pos = resolveContextPos(player, entity);
        if (pos == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(level.getCurrentDifficultyAt(pos).getEffectiveDifficulty());
    }

    private static OptionalDouble resolvePlayerDistanceFromSpawn(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            return OptionalDouble.empty();
        }
        BlockPos spawn = level.getSharedSpawnPos();
        double distance = player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(spawn));
        return OptionalDouble.of(distance);
    }

    private static BlockPos resolveContextPos(ServerPlayer player, Entity entity) {
        if (entity != null) {
            return entity.blockPosition();
        }
        if (player != null) {
            return player.blockPosition();
        }
        return null;
    }

    private static Set<String> loadedMods() {
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        ModList.get().getMods().forEach(modInfo -> mods.add(modInfo.getModId().toLowerCase(Locale.ROOT)));
        return Set.copyOf(mods);
    }

    private static Map<String, Boolean> configToggles() {
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        toggles.put("general.enable_mod", WorldAwakenedCommonConfig.ENABLE_MOD.get());
        toggles.put("general.debug_logging", WorldAwakenedCommonConfig.DEBUG_LOGGING.get());
        toggles.put("general.enable_debug_commands", WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get());
        toggles.put("general.validation_logging", WorldAwakenedCommonConfig.VALIDATION_LOGGING.get());
        toggles.put("ascension.enable_ascension", WorldAwakenedCommonConfig.ENABLE_ASCENSION.get());
        toggles.put("compat.apotheosis.enabled", WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get());
        toggles.put("compat.apotheosis.allow_world_tier_conditions", WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_CONDITIONS.get());
        toggles.put("compat.apotheosis.allow_world_tier_stage_unlocks", WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_STAGE_UNLOCKS.get());
        toggles.put("compat.apotheosis.allow_world_tier_loot_scaling", WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_LOOT_SCALING.get());
        toggles.put("compat.apotheosis.allow_world_tier_invasion_scaling", WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_INVASION_SCALING.get());
        toggles.put("compat.apotheosis.allow_world_tier_mutator_scaling", WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_MUTATOR_SCALING.get());
        return Map.copyOf(toggles);
    }

    private static void applyWorldScalar(ServerLevel level, String key, Optional<String> operator, double value) {
        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        String op = operator.map(raw -> raw.trim().toLowerCase(Locale.ROOT)).orElse("set");
        double previous = data.worldScalars().getOrDefault(normalized, 1.0D);
        double next = switch (op) {
            case "multiply", "mul", "*" -> previous * value;
            case "add", "+" -> previous + value;
            default -> value;
        };
        data.worldScalars().put(normalized, next);
        data.markDirty();
    }

    private static LiveStateTarget resolveLiveState(RuleEventContext context, net.sprocketgames.worldawakened.data.definition.ExecutionScope scope) {
        if (scope == net.sprocketgames.worldawakened.data.definition.ExecutionScope.PLAYER) {
            if (context.player() == null) {
                return LiveStateTarget.empty();
            }
            WorldAwakenedPlayerProgressionSavedData playerData = WorldAwakenedPlayerProgressionSavedData.get(context.level());
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState playerState = playerData.getOrCreate(context.player().getUUID());
            return new LiveStateTarget(playerState, playerState, context.player());
        }
        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(context.level());
        return new LiveStateTarget(worldData, null, null);
    }

    private String nextTraceId() {
        long value = traceCounter.incrementAndGet();
        return "WA-R" + Long.toHexString(value).toUpperCase(Locale.ROOT);
    }

    private static void debugTrace(
            String traceId,
            String eventType,
            WorldAwakenedRuleEvaluation evaluation) {
        if (!WorldAwakenedFeatureGates.debugLoggingEnabled()) {
            return;
        }
        WorldAwakenedLog.debug(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Trace {} rule_eval event={} evaluated={} matched={}",
                traceId,
                eventType,
                evaluation.evaluatedRules(),
                evaluation.matchedCount());
        for (WorldAwakenedRuleDecision decision : evaluation.decisions()) {
            if (decision.matched()) {
                WorldAwakenedLog.debug(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Trace {} rule={} accepted detail={}",
                        traceId,
                        decision.rule().id(),
                        decision.detail());
            } else {
                WorldAwakenedLog.debug(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Trace {} rule={} rejected reason={} detail={}",
                        traceId,
                        decision.rule().id(),
                        decision.rejectionReason().map(Enum::name).orElse("none"),
                        decision.detail());
            }
        }
    }

    public record RuleEventContext(
            String eventType,
            ServerLevel level,
            ServerPlayer player,
            Entity entity,
            ResourceLocation dimensionId,
            Set<ResourceLocation> entityTags,
            boolean entityIsBoss,
            boolean entityIsMutated,
            long nowMillis,
            long gameTime,
            Set<ResourceLocation> worldStageSnapshot,
            Set<ResourceLocation> playerStageSnapshot,
            Optional<ResourceLocation> targetedRuleId) {
    }

    public record ActiveRuleView(
            ResourceLocation ruleId,
            net.sprocketgames.worldawakened.data.definition.ExecutionScope executionScope,
            int priority,
            boolean eligible,
            Optional<WorldAwakenedRejectionReason> rejectionReason,
            String detail,
            long cooldownRemainingMillis,
            boolean consumed,
            double chanceRoll) {
    }

    private record RuleStateSnapshots(
            WorldAwakenedRuleStateSnapshot worldSnapshot,
            WorldAwakenedRuleStateSnapshot playerSnapshot) {
    }

    private record CachedCompiledRules(
            long generation,
            List<WorldAwakenedRuleEngine.CompiledRule> rules) {
    }

    private record LiveStateTarget(
            WorldAwakenedMutableRuleState ruleState,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState playerState,
            ServerPlayer scopedPlayer) {
        private static LiveStateTarget empty() {
            return new LiveStateTarget(null, null, null);
        }
    }
}
