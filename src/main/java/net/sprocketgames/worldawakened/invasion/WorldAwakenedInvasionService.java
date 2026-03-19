
package net.sprocketgames.worldawakened.invasion;

import java.util.ArrayList;
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
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.InvasionTriggerMode;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedWorldProgressionSavedData;

public final class WorldAwakenedInvasionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SCHEDULER_INTERVAL_TICKS = 20L;

    private final WorldAwakenedDatapackService datapackService;
    private final WorldAwakenedStageService stageService;
    private final AtomicReference<CachedProfiles> cache =
            new AtomicReference<>(new CachedProfiles(0L, Map.of(), List.of(), List.of()));
    private final AtomicLong traceCounter = new AtomicLong(0L);

    public WorldAwakenedInvasionService(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService) {
        this.datapackService = datapackService;
        this.stageService = stageService;
    }

    public List<ResourceLocation> loadedProfileIds() {
        return compiledProfiles().orderedIds();
    }

    public void onLevelTick(ServerLevel level) {
        if (!isSchedulerLevel(level)) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        expireActiveInvasionIfNeeded(level, nowMillis);

        if (!WorldAwakenedFeatureGates.modEnabled()
                || !WorldAwakenedCommonConfig.ENABLE_INVASIONS.get()
                || WorldAwakenedCommonConfig.MAX_CONCURRENT_INVASIONS.get() < 1) {
            return;
        }
        if (level.getGameTime() % SCHEDULER_INTERVAL_TICKS != 0L) {
            return;
        }

        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        if (data.invasionActive()) {
            return;
        }
        if (data.invasionGlobalCooldownUntilMillis() > nowMillis) {
            return;
        }

        CachedProfiles compiled = compiledProfiles();
        if (compiled.randomPeriodicProfiles().isEmpty()) {
            return;
        }

        InvasionContextSnapshot activeContext = contextSnapshot(level, nowMillis, false);
        InvasionMatchContext matchContext = buildMatchContext(
                level,
                level.dimension().location(),
                level.getSharedSpawnPos(),
                activeContext,
                nowMillis);
        List<CompiledProfile> eligible = new ArrayList<>();
        for (CompiledProfile profile : compiled.randomPeriodicProfiles()) {
            ProfileEligibility eligibility = evaluateProfileEligibility(
                    profile,
                    matchContext,
                    data,
                    false,
                    true);
            if (eligibility.eligible()) {
                eligible.add(profile);
            }
        }
        if (eligible.isEmpty()) {
            return;
        }

        long seed = deterministicSeed(matchContext, "scheduler_select", -1, null);
        int selectedIndex = Math.floorMod((int) new SplittableRandom(seed).nextLong(), eligible.size());
        activateProfile(level, eligible.get(selectedIndex), nowMillis, "scheduler_random_periodic");
    }

    public InvasionContextSnapshot contextSnapshot(ServerLevel level) {
        return contextSnapshot(level, System.currentTimeMillis(), true);
    }

    public InvasionStartResult startInvasionFromCommand(ServerLevel level, ResourceLocation profileId) {
        long nowMillis = System.currentTimeMillis();
        String traceId = nextTraceId();
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_INVASIONS.get()) {
            return InvasionStartResult.rejected(
                    traceId,
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    "invasion_system_disabled");
        }
        if (WorldAwakenedCommonConfig.MAX_CONCURRENT_INVASIONS.get() < 1) {
            return InvasionStartResult.rejected(
                    traceId,
                    WorldAwakenedDiagnosticCodes.DEBUG_INVASION_STATE_INVALID,
                    "max_concurrent_invasions=0");
        }

        InvasionContextSnapshot activeContext = contextSnapshot(level, nowMillis, true);
        if (activeContext.invasionActive()) {
            return InvasionStartResult.rejected(
                    traceId,
                    WorldAwakenedDiagnosticCodes.DEBUG_INVASION_STATE_INVALID,
                    "already_active:" + activeContext.profileId().map(ResourceLocation::toString).orElse("<unknown>"));
        }

        CompiledProfile profile = compiledProfiles().profilesById().get(profileId);
        if (profile == null) {
            return InvasionStartResult.rejected(
                    traceId,
                    WorldAwakenedDiagnosticCodes.DEBUG_INVASION_PROFILE_NOT_FOUND,
                    "profile_not_found:" + profileId);
        }

        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        InvasionMatchContext matchContext = buildMatchContext(
                level,
                level.dimension().location(),
                level.getSharedSpawnPos(),
                activeContext,
                nowMillis);
        ProfileEligibility eligibility = evaluateProfileEligibility(
                profile,
                matchContext,
                data,
                true,
                false);
        if (!eligibility.eligible()) {
            return InvasionStartResult.rejected(
                    traceId,
                    WorldAwakenedDiagnosticCodes.DEBUG_INVASION_STATE_INVALID,
                    "ineligible:" + String.join("|", eligibility.rejectionReasons()));
        }

        activateProfile(level, profile, nowMillis, "command_forced");
        InvasionContextSnapshot updatedContext = contextSnapshot(level, nowMillis, false);
        return InvasionStartResult.started(traceId, updatedContext);
    }

    public InvasionStopResult stopActiveInvasionFromCommand(ServerLevel level) {
        long nowMillis = System.currentTimeMillis();
        String traceId = nextTraceId();
        InvasionContextSnapshot context = contextSnapshot(level, nowMillis, true);
        if (!context.invasionActive()) {
            return InvasionStopResult.rejected(
                    traceId,
                    WorldAwakenedDiagnosticCodes.DEBUG_INVASION_STATE_INVALID,
                    "no_active_invasion");
        }
        stopActiveInvasion(level, nowMillis, "command_stop");
        return InvasionStopResult.stopped(traceId, context);
    }

    public InvasionEvaluateResult debugEvaluateProfile(
            ServerLevel level,
            ResourceLocation profileId,
            ResourceLocation dimensionId,
            BlockPos position) {
        long nowMillis = System.currentTimeMillis();
        String traceId = nextTraceId();
        InvasionContextSnapshot activeContext = contextSnapshot(level, nowMillis, true);
        CachedProfiles compiled = compiledProfiles();
        CompiledProfile profile = compiled.profilesById().get(profileId);
        if (profile == null) {
            return InvasionEvaluateResult.missingProfile(
                    traceId,
                    profileId,
                    activeContext,
                    WorldAwakenedDiagnosticCodes.DEBUG_INVASION_PROFILE_NOT_FOUND,
                    "profile_not_found:" + profileId);
        }

        InvasionMatchContext matchContext = buildMatchContext(level, dimensionId, position, activeContext, nowMillis);
        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        ProfileEligibility eligibility = evaluateProfileEligibility(
                profile,
                matchContext,
                data,
                false,
                true);
        InvasionPoolSummary poolSummary = evaluateInvasionPoolSummary(level, matchContext, profile);
        return InvasionEvaluateResult.evaluated(
                traceId,
                profile.id(),
                activeContext,
                eligibility,
                poolSummary);
    }

    private void activateProfile(
            ServerLevel level,
            CompiledProfile profile,
            long nowMillis,
            String source) {
        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        int warningSeconds = profile.warningSeconds().orElse(WorldAwakenedCommonConfig.WARNING_SECONDS.get());
        int durationSeconds = profile.durationSeconds();
        long warningUntil = nowMillis + (Math.max(0L, warningSeconds) * 1000L);
        long endsAt = warningUntil + (Math.max(0L, durationSeconds) * 1000L);
        long instanceId = data.nextInvasionInstanceId();

        data.setActiveInvasionProfileId(profile.id().toString());
        data.setActiveInvasionDisplayName(profile.displayNamePlain());
        data.activeInvasionTags().clear();
        data.activeInvasionTags().addAll(profile.tags());
        data.setActiveInvasionInstanceId(instanceId);
        data.setActiveInvasionStartedAtMillis(nowMillis);
        data.setActiveInvasionWarningUntilMillis(warningUntil);
        data.setActiveInvasionEndsAtMillis(endsAt);
        data.setActiveInvasionDurationSeconds(durationSeconds);
        data.setActiveInvasionWarningSeconds(warningSeconds);
        data.setActiveInvasionPressureModifier(profile.pressureModifier());
        data.setActiveInvasionRewardProfileId(profile.rewardProfile().map(ResourceLocation::toString).orElse(""));
        data.markDirty();

        WorldAwakenedLog.info(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Invasion activated: trace={} profile={} source={} warning_seconds={} duration_seconds={} pressure_modifier={}",
                nextTraceId(),
                profile.id(),
                source,
                warningSeconds,
                durationSeconds,
                profile.pressureModifier());
    }

    private void expireActiveInvasionIfNeeded(ServerLevel level, long nowMillis) {
        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        if (!data.invasionActive()) {
            return;
        }
        if (data.activeInvasionEndsAtMillis() > nowMillis) {
            return;
        }
        stopActiveInvasion(level, nowMillis, "duration_elapsed");
    }

    private void stopActiveInvasion(
            ServerLevel level,
            long nowMillis,
            String reason) {
        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        ResourceLocation activeProfileId = parseResourceLocationOptional(data.activeInvasionProfileId()).orElse(null);
        int profileCooldownSeconds = 0;
        if (activeProfileId != null) {
            CompiledProfile profile = compiledProfiles().profilesById().get(activeProfileId);
            if (profile != null) {
                profileCooldownSeconds = Math.max(0, profile.cooldownSeconds().orElse(0));
            }
        }

        if (activeProfileId != null && profileCooldownSeconds > 0) {
            data.invasionCooldownTrackers().put(
                    activeProfileId.toString(),
                    nowMillis + (profileCooldownSeconds * 1000L));
        }

        long globalCooldownMillis = Math.max(0L, WorldAwakenedCommonConfig.GLOBAL_COOLDOWN_MINUTES.get()) * 60_000L;
        data.setInvasionGlobalCooldownUntilMillis(nowMillis + globalCooldownMillis);
        data.clearActiveInvasionState();
        data.markDirty();

        WorldAwakenedLog.info(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Invasion stopped: profile={} reason={} profile_cooldown_seconds={} global_cooldown_minutes={}",
                activeProfileId == null ? "<unknown>" : activeProfileId,
                reason,
                profileCooldownSeconds,
                WorldAwakenedCommonConfig.GLOBAL_COOLDOWN_MINUTES.get());
    }

    private InvasionContextSnapshot contextSnapshot(
            ServerLevel level,
            long nowMillis,
            boolean expireIfNeeded) {
        if (expireIfNeeded) {
            expireActiveInvasionIfNeeded(level, nowMillis);
        }
        WorldAwakenedWorldProgressionSavedData data = WorldAwakenedWorldProgressionSavedData.get(level);
        if (!data.invasionActive()) {
            return InvasionContextSnapshot.inactive(
                    Math.max(0L, data.invasionGlobalCooldownUntilMillis() - nowMillis));
        }

        Optional<ResourceLocation> profileId = parseResourceLocationOptional(data.activeInvasionProfileId());
        boolean warningActive = nowMillis < data.activeInvasionWarningUntilMillis();
        long remainingSeconds = Math.max(0L, (data.activeInvasionEndsAtMillis() - nowMillis) / 1000L);
        double pressureModifier = warningActive ? 1.0D : data.activeInvasionPressureModifier();
        Optional<ResourceLocation> rewardProfile = parseResourceLocationOptional(data.activeInvasionRewardProfileId());
        long profileCooldownRemainingMillis = profileId
                .map(id -> Math.max(0L, data.invasionCooldownTrackers().getOrDefault(id.toString(), 0L) - nowMillis))
                .orElse(0L);

        return new InvasionContextSnapshot(
                true,
                profileId,
                data.activeInvasionDisplayName().isBlank()
                        ? profileId.map(ResourceLocation::toString).orElse("<unknown>")
                        : data.activeInvasionDisplayName(),
                Set.copyOf(data.activeInvasionTags()),
                data.activeInvasionInstanceId(),
                data.activeInvasionStartedAtMillis(),
                warningActive,
                remainingSeconds,
                pressureModifier,
                rewardProfile,
                Math.max(0L, data.invasionGlobalCooldownUntilMillis() - nowMillis),
                profileCooldownRemainingMillis);
    }

    private InvasionPoolSummary evaluateInvasionPoolSummary(
            ServerLevel level,
            InvasionMatchContext baseContext,
            CompiledProfile profile) {
        List<ResourceLocation> newlyEligible = new ArrayList<>();
        List<ResourceLocation> alreadyEligible = new ArrayList<>();
        List<PoolDecision> rejected = new ArrayList<>();

        InvasionMatchContext activeContext = baseContext.withInvasion(
                true,
                Optional.of(profile.id()),
                profile.tags());
        InvasionMatchContext inactiveContext = baseContext.withInvasion(
                false,
                Optional.empty(),
                Set.of());

        List<MutationPoolDefinition> pools = new ArrayList<>(datapackService.currentSnapshot().data().mutationPools().values());
        pools.sort(Comparator.comparing(pool -> pool.id().toString()));
        for (MutationPoolDefinition pool : pools) {
            if (!pool.enabled()) {
                continue;
            }
            StageFilter stageFilter = StageFilter.compile(pool.stageFilters());
            List<CompiledCondition> conditions = compileConditions(pool.conditions());
            boolean invasionGated = conditions.stream().anyMatch(condition ->
                    condition.kind() == ConditionKind.INVASION_ACTIVE
                            || condition.kind() == ConditionKind.INVASION_TAG);
            if (!invasionGated) {
                continue;
            }

            Optional<String> activeFailure = firstPoolFailure(pool, stageFilter, conditions, activeContext);
            Optional<String> inactiveFailure = firstPoolFailure(pool, stageFilter, conditions, inactiveContext);
            if (activeFailure.isPresent()) {
                rejected.add(new PoolDecision(
                        pool.id(),
                        false,
                        activeFailure.get()));
                continue;
            }

            if (inactiveFailure.isPresent()) {
                newlyEligible.add(pool.id());
            } else {
                alreadyEligible.add(pool.id());
            }
        }

        newlyEligible.sort(Comparator.comparing(ResourceLocation::toString));
        alreadyEligible.sort(Comparator.comparing(ResourceLocation::toString));
        rejected.sort(Comparator.comparing(decision -> decision.poolId().toString()));
        return new InvasionPoolSummary(
                List.copyOf(newlyEligible),
                List.copyOf(alreadyEligible),
                List.copyOf(rejected));
    }

    private Optional<String> firstPoolFailure(
            MutationPoolDefinition pool,
            StageFilter stageFilter,
            List<CompiledCondition> conditions,
            InvasionMatchContext context) {
        if (!pool.eligibleDimensions().isEmpty()
                && !pool.eligibleDimensions().contains(context.dimensionId())) {
            return Optional.of("dimension_rejected");
        }
        if (!pool.eligibleBiomes().isEmpty()) {
            if (context.biomeId().isEmpty() || !pool.eligibleBiomes().contains(context.biomeId().get())) {
                return Optional.of("biome_rejected");
            }
        }
        if (!stageFilter.matches(context.worldStageSnapshot(), context.stageRegistry())) {
            return Optional.of("stage_filters_rejected");
        }
        for (CompiledCondition condition : conditions) {
            Optional<String> conditionFailure = conditionFailure(condition, context, pool.id(), "pool_condition");
            if (conditionFailure.isPresent()) {
                return conditionFailure;
            }
        }
        return Optional.empty();
    }

    private ProfileEligibility evaluateProfileEligibility(
            CompiledProfile profile,
            InvasionMatchContext context,
            WorldAwakenedWorldProgressionSavedData data,
            boolean bypassCooldown,
            boolean requireSchedulerMode) {
        List<String> rejections = new ArrayList<>();
        long nowMillis = context.nowMillis();

        if (requireSchedulerMode && profile.triggerMode() != InvasionTriggerMode.RANDOM_PERIODIC) {
            rejections.add("trigger_mode_not_random_periodic:" + profile.triggerMode().name().toLowerCase(Locale.ROOT));
        }
        if (profile.minPlayers() > context.playerCountOnline()) {
            rejections.add("player_count_below_min:" + context.playerCountOnline() + "<" + profile.minPlayers());
        }
        if (!profile.dimensions().isEmpty() && !profile.dimensions().contains(context.dimensionId())) {
            rejections.add("dimension_not_allowed:" + context.dimensionId());
        }
        if (!profile.biomeFilters().isEmpty()) {
            if (context.biomeId().isEmpty()) {
                rejections.add("biome_unavailable");
            } else if (!profile.biomeFilters().contains(context.biomeId().get())) {
                rejections.add("biome_not_allowed:" + context.biomeId().get());
            }
        }
        if (!profile.stageFilter().matches(context.worldStageSnapshot(), context.stageRegistry())) {
            rejections.add("stage_filters_rejected");
        }

        if (!bypassCooldown) {
            long globalCooldownRemaining = Math.max(0L, data.invasionGlobalCooldownUntilMillis() - nowMillis);
            if (globalCooldownRemaining > 0L) {
                rejections.add("global_cooldown_active_millis:" + globalCooldownRemaining);
            }
            long profileCooldownUntil = data.invasionCooldownTrackers().getOrDefault(profile.id().toString(), 0L);
            long profileCooldownRemaining = Math.max(0L, profileCooldownUntil - nowMillis);
            if (profileCooldownRemaining > 0L) {
                rejections.add("profile_cooldown_active_millis:" + profileCooldownRemaining);
            }
        }

        for (CompiledCondition condition : profile.conditions()) {
            Optional<String> conditionFailure = conditionFailure(condition, context, profile.id(), "profile_condition");
            if (conditionFailure.isPresent()) {
                rejections.add(conditionFailure.get());
                break;
            }
        }

        long globalCooldownRemaining = Math.max(0L, data.invasionGlobalCooldownUntilMillis() - nowMillis);
        long profileCooldownRemaining = Math.max(
                0L,
                data.invasionCooldownTrackers().getOrDefault(profile.id().toString(), 0L) - nowMillis);
        return new ProfileEligibility(
                rejections.isEmpty(),
                profile.id(),
                profile.triggerMode(),
                profile.minPlayers(),
                context.playerCountOnline(),
                globalCooldownRemaining,
                profileCooldownRemaining,
                List.copyOf(rejections));
    }

    private Optional<String> conditionFailure(
            CompiledCondition condition,
            InvasionMatchContext context,
            ResourceLocation ownerId,
            String channel) {
        return switch (condition.kind()) {
            case STAGE_UNLOCKED -> condition.resourceRef()
                    .map(stageId -> containsStage(context.worldStageSnapshot(), context.stageRegistry(), stageId)
                            ? Optional.<String>empty()
                            : Optional.of("stage_unlocked_failed:" + stageId))
                    .orElseGet(() -> Optional.of("stage_unlocked_missing_ref"));
            case STAGE_LOCKED -> condition.resourceRef()
                    .map(stageId -> !containsStage(context.worldStageSnapshot(), context.stageRegistry(), stageId)
                            ? Optional.<String>empty()
                            : Optional.of("stage_locked_failed:" + stageId))
                    .orElseGet(() -> Optional.of("stage_locked_missing_ref"));
            case CURRENT_DIMENSION -> condition.resourceRef()
                    .map(dimension -> context.dimensionId().equals(dimension)
                            ? Optional.<String>empty()
                            : Optional.of("dimension_mismatch:" + context.dimensionId()))
                    .orElseGet(() -> Optional.of("current_dimension_missing_ref"));
            case CURRENT_BIOME -> condition.resourceRef()
                    .map(biome -> context.biomeId().map(biome::equals).orElse(false)
                            ? Optional.<String>empty()
                            : Optional.of("biome_mismatch"))
                    .orElseGet(() -> Optional.of("current_biome_missing_ref"));
            case WORLD_DAY_GTE -> {
                if (context.worldDay().isEmpty()) {
                    yield Optional.of("world_day_unavailable");
                }
                if (condition.value().isEmpty()) {
                    yield Optional.of("world_day_threshold_missing");
                }
                long threshold = Math.max(0L, (long) condition.value().getAsDouble());
                yield context.worldDay().getAsLong() >= threshold
                        ? Optional.empty()
                        : Optional.of("world_day_below_threshold");
            }
            case PLAYER_DISTANCE_FROM_SPAWN -> {
                if (context.playerDistanceFromSpawn().isEmpty()) {
                    yield Optional.of("player_distance_unavailable");
                }
                double distance = context.playerDistanceFromSpawn().getAsDouble();
                if (condition.min().isPresent() && distance < condition.min().getAsDouble()) {
                    yield Optional.of("player_distance_below_min");
                }
                if (condition.max().isPresent() && distance > condition.max().getAsDouble()) {
                    yield Optional.of("player_distance_above_max");
                }
                yield Optional.empty();
            }
            case PLAYER_COUNT_ONLINE -> {
                int online = context.playerCountOnline();
                if (condition.min().isPresent() && online < condition.min().getAsDouble()) {
                    yield Optional.of("player_count_below_min");
                }
                if (condition.max().isPresent() && online > condition.max().getAsDouble()) {
                    yield Optional.of("player_count_above_max");
                }
                yield Optional.empty();
            }
            case LOADED_MOD -> condition.text()
                    .map(modId -> context.loadedMods().contains(modId)
                            ? Optional.<String>empty()
                            : Optional.of("loaded_mod_missing:" + modId))
                    .orElseGet(() -> Optional.of("loaded_mod_missing_key"));
            case CONFIG_TOGGLE_ENABLED -> condition.text()
                    .map(toggle -> context.configToggles().getOrDefault(toggle, false)
                            ? Optional.<String>empty()
                            : Optional.of("config_toggle_disabled:" + toggle))
                    .orElseGet(() -> Optional.of("config_toggle_missing_key"));
            case RANDOM_CHANCE -> {
                double chance = condition.value().orElse(1.0D);
                double roll = deterministicRoll(context, channel, condition.conditionIndex(), ownerId);
                yield roll <= chance
                        ? Optional.empty()
                        : Optional.of("random_chance_failed:roll=" + roll + " threshold=" + chance);
            }
            case MOON_PHASE -> {
                if (context.worldDay().isEmpty()) {
                    yield Optional.of("moon_phase_unavailable");
                }
                long phase = Math.floorMod(context.worldDay().getAsLong(), 8L);
                yield condition.intValues().contains((int) phase)
                        ? Optional.empty()
                        : Optional.of("moon_phase_mismatch:" + phase);
            }
            case INVASION_ACTIVE -> {
                if (!context.invasionActive()) {
                    yield Optional.of("invasion_not_active");
                }
                if (condition.resourceRef().isPresent()) {
                    if (context.invasionProfileId().isEmpty()
                            || !context.invasionProfileId().get().equals(condition.resourceRef().get())) {
                        yield Optional.of("invasion_profile_mismatch");
                    }
                }
                yield Optional.empty();
            }
            case INVASION_TAG -> {
                if (!context.invasionActive()) {
                    yield Optional.of("invasion_not_active");
                }
                if (condition.text().isEmpty()) {
                    yield Optional.of("invasion_tag_missing");
                }
                yield context.invasionTags().contains(condition.text().get())
                        ? Optional.empty()
                        : Optional.of("invasion_tag_missing:" + condition.text().get());
            }
            case UNSUPPORTED -> Optional.of("unsupported_condition:" + condition.typeId());
        };
    }
    private InvasionMatchContext buildMatchContext(
            ServerLevel level,
            ResourceLocation dimensionId,
            BlockPos position,
            InvasionContextSnapshot activeContext,
            long nowMillis) {
        Optional<ResourceLocation> biomeId = resolveBiome(level, position);
        OptionalDouble distanceFromSpawn = nearestPlayerDistanceFromSpawn(level);
        Set<ResourceLocation> worldStages = stageService.getUnlockedStages(level);
        return new InvasionMatchContext(
                dimensionId,
                biomeId,
                Set.copyOf(worldStages),
                stageService.stageRegistry(),
                OptionalLong.of(Math.max(0L, level.getDayTime() / 24000L)),
                distanceFromSpawn,
                level.getServer() == null ? 0 : level.getServer().getPlayerCount(),
                activeContext.invasionActive(),
                activeContext.profileId(),
                activeContext.tags(),
                loadedMods(),
                configToggles(),
                nowMillis,
                level.getGameTime());
    }

    private static OptionalDouble nearestPlayerDistanceFromSpawn(ServerLevel level) {
        if (level.getServer() == null || level.getServer().getPlayerList() == null) {
            return OptionalDouble.empty();
        }
        BlockPos spawn = level.getSharedSpawnPos();
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            double distance = player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(spawn));
            if (distance < nearest) {
                nearest = distance;
            }
        }
        if (nearest == Double.MAX_VALUE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(nearest);
    }

    private CachedProfiles compiledProfiles() {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        CachedProfiles cached = cache.get();
        if (cached.generation() == snapshot.generation()) {
            return cached;
        }

        Map<ResourceLocation, CompiledProfile> byId = new LinkedHashMap<>();
        List<CompiledProfile> randomPeriodic = new ArrayList<>();
        List<ResourceLocation> orderedIds = new ArrayList<>();
        for (InvasionProfileDefinition definition : snapshot.data().invasionProfiles().values()) {
            if (!definition.enabled()) {
                continue;
            }
            CompiledProfile compiled = compileProfile(definition);
            byId.put(compiled.id(), compiled);
            orderedIds.add(compiled.id());
            if (compiled.triggerMode() == InvasionTriggerMode.RANDOM_PERIODIC) {
                randomPeriodic.add(compiled);
            }
        }

        orderedIds.sort(Comparator.comparing(ResourceLocation::toString));
        randomPeriodic.sort(Comparator.comparing(profile -> profile.id().toString()));
        CachedProfiles refreshed = new CachedProfiles(
                snapshot.generation(),
                Map.copyOf(byId),
                List.copyOf(randomPeriodic),
                List.copyOf(orderedIds));
        cache.set(refreshed);
        return refreshed;
    }

    private static CompiledProfile compileProfile(InvasionProfileDefinition definition) {
        List<CompiledCondition> conditions = compileConditions(definition.conditions());
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String rawTag : definition.tags()) {
            if (rawTag == null) {
                continue;
            }
            String normalized = rawTag.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                tags.add(normalized);
            }
        }
        String displayName = displayNamePlain(definition.displayName(), definition.id());
        return new CompiledProfile(
                definition.id(),
                displayName,
                definition.triggerMode(),
                List.copyOf(conditions),
                StageFilter.compile(definition.stageFilters()),
                Set.copyOf(definition.dimensions()),
                Set.copyOf(definition.biomeFilters()),
                Math.max(1, definition.minPlayers()),
                definition.cooldownSeconds().map(value -> Math.max(0, value)),
                definition.warningSeconds().map(value -> Math.max(0, value)),
                Math.max(1, definition.durationSeconds()),
                Double.isFinite(definition.pressureModifier()) && definition.pressureModifier() > 0.0D
                        ? definition.pressureModifier()
                        : 1.0D,
                definition.rewardProfile(),
                Set.copyOf(tags));
    }

    private static List<CompiledCondition> compileConditions(List<JsonElement> conditions) {
        List<CompiledCondition> compiled = new ArrayList<>();
        for (int index = 0; index < conditions.size(); index++) {
            JsonElement condition = conditions.get(index);
            compileCondition(condition, index).ifPresent(compiled::add);
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
        Optional<ResourceLocation> typeOpt = readResourceLocation(object, "type");
        if (typeOpt.isEmpty()) {
            return Optional.empty();
        }
        JsonObject parameters = readParametersObject(object);
        String path = typeOpt.get().getPath().toLowerCase(Locale.ROOT);
        ConditionKind kind = switch (path) {
            case "stage_unlocked" -> ConditionKind.STAGE_UNLOCKED;
            case "stage_locked" -> ConditionKind.STAGE_LOCKED;
            case "current_dimension" -> ConditionKind.CURRENT_DIMENSION;
            case "current_biome" -> ConditionKind.CURRENT_BIOME;
            case "world_day_gte" -> ConditionKind.WORLD_DAY_GTE;
            case "player_distance_from_spawn" -> ConditionKind.PLAYER_DISTANCE_FROM_SPAWN;
            case "player_count_online" -> ConditionKind.PLAYER_COUNT_ONLINE;
            case "loaded_mod" -> ConditionKind.LOADED_MOD;
            case "config_toggle_enabled" -> ConditionKind.CONFIG_TOGGLE_ENABLED;
            case "random_chance" -> ConditionKind.RANDOM_CHANCE;
            case "moon_phase" -> ConditionKind.MOON_PHASE;
            case "invasion_active" -> ConditionKind.INVASION_ACTIVE;
            case "invasion_tag" -> ConditionKind.INVASION_TAG;
            default -> ConditionKind.UNSUPPORTED;
        };
        Optional<ResourceLocation> resourceRef = switch (kind) {
            case STAGE_UNLOCKED, STAGE_LOCKED -> readResourceLocation(parameters, "stage");
            case CURRENT_DIMENSION -> readResourceLocation(parameters, "dimension");
            case CURRENT_BIOME -> readResourceLocation(parameters, "biome");
            case INVASION_ACTIVE -> readResourceLocation(parameters, "profile_id", "profile");
            default -> Optional.empty();
        };
        Optional<String> text = switch (kind) {
            case LOADED_MOD -> readString(parameters, "mod").map(value -> value.toLowerCase(Locale.ROOT));
            case CONFIG_TOGGLE_ENABLED -> readString(parameters, "config_gate").map(value -> value.toLowerCase(Locale.ROOT));
            case INVASION_TAG -> readString(parameters, "tag").map(value -> value.toLowerCase(Locale.ROOT));
            default -> Optional.empty();
        };
        OptionalDouble value = switch (kind) {
            case WORLD_DAY_GTE -> readDouble(parameters, "value");
            case RANDOM_CHANCE -> readDouble(parameters, "chance");
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
        Set<Integer> intValues = kind == ConditionKind.MOON_PHASE ? parseMoonPhases(parameters) : Set.of();
        return Optional.of(new CompiledCondition(
                typeOpt.get(),
                kind,
                resourceRef,
                text,
                value,
                min,
                max,
                intValues,
                conditionIndex));
    }

    private static boolean containsStage(
            Set<ResourceLocation> activeStages,
            WorldAwakenedStageRegistry stageRegistry,
            ResourceLocation requestedId) {
        Optional<ResourceLocation> canonical = stageRegistry.resolveCanonicalId(requestedId);
        return activeStages.contains(canonical.orElse(requestedId));
    }

    private static Optional<ResourceLocation> resolveBiome(ServerLevel level, BlockPos position) {
        Optional<ResourceKey<Biome>> key = level.getBiome(position).unwrapKey();
        return key.map(ResourceKey::location);
    }

    private static String displayNamePlain(JsonElement displayName, ResourceLocation fallbackId) {
        if (displayName == null || displayName.isJsonNull()) {
            return fallbackId.toString();
        }
        if (displayName.isJsonPrimitive()) {
            String value = displayName.getAsString();
            if (!value.isBlank()) {
                return value;
            }
            return fallbackId.toString();
        }
        return displayName.toString();
    }

    private static Map<String, Boolean> configToggles() {
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        toggles.put("general.enable_mod", WorldAwakenedCommonConfig.ENABLE_MOD.get());
        toggles.put("general.debug_logging", WorldAwakenedCommonConfig.DEBUG_LOGGING.get());
        toggles.put("general.enable_debug_commands", WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get());
        toggles.put("invasions.enable_invasions", WorldAwakenedCommonConfig.ENABLE_INVASIONS.get());
        toggles.put("compat.apotheosis.enabled", WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get());
        return Map.copyOf(toggles);
    }

    private static Set<String> loadedMods() {
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        ModList.get().getMods().forEach(modInfo -> mods.add(modInfo.getModId().toLowerCase(Locale.ROOT)));
        return Set.copyOf(mods);
    }

    private static long deterministicSeed(
            InvasionMatchContext context,
            String channel,
            int extraIndex,
            ResourceLocation ownerId) {
        long seed = 0x9E3779B97F4A7C15L;
        seed = mix(seed, context.dimensionId().toString());
        seed = mix(seed, context.gameTime());
        seed = mix(seed, context.nowMillis());
        seed = mix(seed, channel);
        seed = mix(seed, extraIndex);
        if (ownerId != null) {
            seed = mix(seed, ownerId.toString());
        }
        return seed;
    }

    private static double deterministicRoll(
            InvasionMatchContext context,
            String channel,
            int extraIndex,
            ResourceLocation ownerId) {
        return new SplittableRandom(deterministicSeed(context, channel, extraIndex, ownerId)).nextDouble();
    }

    private String nextTraceId() {
        long value = traceCounter.incrementAndGet();
        return "WA-I" + Long.toHexString(value).toUpperCase(Locale.ROOT);
    }

    private static long mix(long seed, long value) {
        long result = seed ^ value;
        result *= 0x100000001B3L;
        result ^= (result >>> 33);
        return result;
    }

    private static long mix(long seed, String value) {
        long result = seed;
        for (int index = 0; index < value.length(); index++) {
            result ^= value.charAt(index);
            result *= 0x100000001B3L;
            result ^= (result >>> 31);
        }
        return result;
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            ResourceLocation parsed = parseResourceLocationRaw(object.get(key).getAsString());
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
            if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) {
                continue;
            }
            return OptionalDouble.of(object.get(key).getAsDouble());
        }
        return OptionalDouble.empty();
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
        return !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean() || enabled.getAsBoolean();
    }

    private static ResourceLocation parseResourceLocationRaw(String raw) {
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

    private static Optional<ResourceLocation> parseResourceLocationOptional(String raw) {
        return Optional.ofNullable(parseResourceLocationRaw(raw));
    }

    private static boolean isSchedulerLevel(ServerLevel level) {
        return level.getServer() != null && level.getServer().overworld() == level;
    }

    public record InvasionContextSnapshot(
            boolean invasionActive,
            Optional<ResourceLocation> profileId,
            String displayName,
            Set<String> tags,
            long instanceId,
            long startedAtMillis,
            boolean warningActive,
            long remainingDurationSeconds,
            double pressureModifier,
            Optional<ResourceLocation> rewardProfile,
            long globalCooldownRemainingMillis,
            long profileCooldownRemainingMillis) {
        static InvasionContextSnapshot inactive(long globalCooldownRemainingMillis) {
            return new InvasionContextSnapshot(
                    false,
                    Optional.empty(),
                    "",
                    Set.of(),
                    0L,
                    0L,
                    false,
                    0L,
                    1.0D,
                    Optional.empty(),
                    Math.max(0L, globalCooldownRemainingMillis),
                    0L);
        }
    }

    public record InvasionStartResult(
            boolean success,
            String code,
            String detail,
            String traceId,
            Optional<InvasionContextSnapshot> context) {
        static InvasionStartResult rejected(String traceId, String code, String detail) {
            return new InvasionStartResult(false, code, detail, traceId, Optional.empty());
        }

        static InvasionStartResult started(String traceId, InvasionContextSnapshot context) {
            return new InvasionStartResult(true, "", "", traceId, Optional.of(context));
        }
    }

    public record InvasionStopResult(
            boolean success,
            String code,
            String detail,
            String traceId,
            Optional<InvasionContextSnapshot> stoppedContext) {
        static InvasionStopResult rejected(String traceId, String code, String detail) {
            return new InvasionStopResult(false, code, detail, traceId, Optional.empty());
        }

        static InvasionStopResult stopped(String traceId, InvasionContextSnapshot context) {
            return new InvasionStopResult(true, "", "", traceId, Optional.of(context));
        }
    }

    public record InvasionEvaluateResult(
            String traceId,
            ResourceLocation profileId,
            boolean profileFound,
            InvasionContextSnapshot activeContext,
            Optional<ProfileEligibility> eligibility,
            Optional<InvasionPoolSummary> poolSummary,
            String code,
            String detail) {
        static InvasionEvaluateResult missingProfile(
                String traceId,
                ResourceLocation profileId,
                InvasionContextSnapshot activeContext,
                String code,
                String detail) {
            return new InvasionEvaluateResult(
                    traceId,
                    profileId,
                    false,
                    activeContext,
                    Optional.empty(),
                    Optional.empty(),
                    code,
                    detail);
        }

        static InvasionEvaluateResult evaluated(
                String traceId,
                ResourceLocation profileId,
                InvasionContextSnapshot activeContext,
                ProfileEligibility eligibility,
                InvasionPoolSummary poolSummary) {
            return new InvasionEvaluateResult(
                    traceId,
                    profileId,
                    true,
                    activeContext,
                    Optional.of(eligibility),
                    Optional.of(poolSummary),
                    "",
                    "");
        }
    }

    public record ProfileEligibility(
            boolean eligible,
            ResourceLocation profileId,
            InvasionTriggerMode triggerMode,
            int minPlayers,
            int onlinePlayers,
            long globalCooldownRemainingMillis,
            long profileCooldownRemainingMillis,
            List<String> rejectionReasons) {
    }

    public record InvasionPoolSummary(
            List<ResourceLocation> newlyEligiblePools,
            List<ResourceLocation> alreadyEligiblePools,
            List<PoolDecision> rejectedPools) {
    }

    public record PoolDecision(
            ResourceLocation poolId,
            boolean eligible,
            String detail) {
    }

    private record CachedProfiles(
            long generation,
            Map<ResourceLocation, CompiledProfile> profilesById,
            List<CompiledProfile> randomPeriodicProfiles,
            List<ResourceLocation> orderedIds) {
    }

    private record CompiledProfile(
            ResourceLocation id,
            String displayNamePlain,
            InvasionTriggerMode triggerMode,
            List<CompiledCondition> conditions,
            StageFilter stageFilter,
            Set<ResourceLocation> dimensions,
            Set<ResourceLocation> biomeFilters,
            int minPlayers,
            Optional<Integer> cooldownSeconds,
            Optional<Integer> warningSeconds,
            int durationSeconds,
            double pressureModifier,
            Optional<ResourceLocation> rewardProfile,
            Set<String> tags) {
    }

    private record CompiledCondition(
            ResourceLocation typeId,
            ConditionKind kind,
            Optional<ResourceLocation> resourceRef,
            Optional<String> text,
            OptionalDouble value,
            OptionalDouble min,
            OptionalDouble max,
            Set<Integer> intValues,
            int conditionIndex) {
    }

    private enum ConditionKind {
        STAGE_UNLOCKED,
        STAGE_LOCKED,
        CURRENT_DIMENSION,
        CURRENT_BIOME,
        WORLD_DAY_GTE,
        PLAYER_DISTANCE_FROM_SPAWN,
        PLAYER_COUNT_ONLINE,
        LOADED_MOD,
        CONFIG_TOGGLE_ENABLED,
        RANDOM_CHANCE,
        MOON_PHASE,
        INVASION_ACTIVE,
        INVASION_TAG,
        UNSUPPORTED
    }

    private record InvasionMatchContext(
            ResourceLocation dimensionId,
            Optional<ResourceLocation> biomeId,
            Set<ResourceLocation> worldStageSnapshot,
            WorldAwakenedStageRegistry stageRegistry,
            OptionalLong worldDay,
            OptionalDouble playerDistanceFromSpawn,
            int playerCountOnline,
            boolean invasionActive,
            Optional<ResourceLocation> invasionProfileId,
            Set<String> invasionTags,
            Set<String> loadedMods,
            Map<String, Boolean> configToggles,
            long nowMillis,
            long gameTime) {
        InvasionMatchContext withInvasion(
                boolean invasionActive,
                Optional<ResourceLocation> invasionProfileId,
                Set<String> invasionTags) {
            return new InvasionMatchContext(
                    dimensionId,
                    biomeId,
                    worldStageSnapshot,
                    stageRegistry,
                    worldDay,
                    playerDistanceFromSpawn,
                    playerCountOnline,
                    invasionActive,
                    invasionProfileId,
                    Set.copyOf(invasionTags),
                    loadedMods,
                    configToggles,
                    nowMillis,
                    gameTime);
        }
    }

    private static final class StageFilter {
        private final Set<ResourceLocation> requiredAll;
        private final Set<ResourceLocation> requiredAny;
        private final Set<ResourceLocation> excluded;

        private StageFilter(
                Set<ResourceLocation> requiredAll,
                Set<ResourceLocation> requiredAny,
                Set<ResourceLocation> excluded) {
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
                for (ResourceLocation stageId : requiredAny) {
                    if (containsStage(activeStages, stageRegistry, stageId)) {
                        anyMatched = true;
                        break;
                    }
                }
                if (!anyMatched) {
                    return false;
                }
            }
            for (ResourceLocation excludedStage : excluded) {
                if (containsStage(activeStages, stageRegistry, excludedStage)) {
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
                for (JsonElement entry : raw.getAsJsonArray()) {
                    if (!entry.isJsonPrimitive()) {
                        continue;
                    }
                    ResourceLocation parsed = ResourceLocation.tryParse(entry.getAsString());
                    if (parsed != null) {
                        collector.add(parsed);
                    }
                }
            }
        }
    }
}
