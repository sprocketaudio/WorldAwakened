package net.sprocketgames.worldawakened.difficulty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;
import net.sprocketgames.worldawakened.progression.WorldAwakenedWorldProgressionSavedData;

public final class WorldAwakenedEffectiveDifficultyScalarService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BigDecimal BIG_DECIMAL_ZERO = BigDecimal.ZERO;

    private static final String CODE_BOUNDS = "bounds";
    private static final String CODE_SCOPE_INVALID = "scope_invalid";
    private static final String CODE_POLICY_DISALLOWS_CHANGE = "policy_disallows_change";
    private static final String CODE_COOLDOWN_ACTIVE = "cooldown_active";
    private static final String CODE_USAGE_EXHAUSTED = "usage_exhausted";
    private static final String CODE_STEP_INVALID = "step_invalid";
    private static final String CODE_UNAUTHORIZED = "unauthorized";
    private static final String CODE_VOTE_REQUIRED = "vote_required";
    private static final String CODE_VOTE_INACTIVE = "vote_inactive";
    private static final String CODE_VOTE_ACTIVE = "vote_active";

    private final AtomicReference<DimensionBaselineCache> dimensionBaselineCache =
            new AtomicReference<>(new DimensionBaselineCache(List.of(), Map.of()));

    public GlobalModifierState globalState(ServerLevel level) {
        PolicySnapshot policy = policySnapshot();
        if (!policy.globalEnabled()) {
            return new GlobalModifierState(
                    false,
                    1.0D,
                    policy.globalDefaultValue(),
                    policy.globalMinValue(),
                    policy.globalMaxValue(),
                    policy.globalDiagnosticCode(),
                    policy.globalDiagnosticDetail());
        }
        return new GlobalModifierState(
                true,
                sanitizeGlobalValue(level, policy),
                policy.globalDefaultValue(),
                policy.globalMinValue(),
                policy.globalMaxValue(),
                "",
                "");
    }

    public MutationResult setGlobalModifier(ServerLevel level, double requestedValue, String actor) {
        PolicySnapshot policy = policySnapshot();
        if (!policy.globalEnabled()) {
            return MutationResult.rejected(
                    policy.globalDiagnosticCode().isBlank() ? WorldAwakenedDiagnosticCodes.DIFFICULTY_GLOBAL_INVALID : policy.globalDiagnosticCode(),
                    policy.globalDiagnosticDetail().isBlank() ? "global_modifier_disabled_or_invalid" : policy.globalDiagnosticDetail());
        }
        if (!Double.isFinite(requestedValue)
                || requestedValue < policy.globalMinValue()
                || requestedValue > policy.globalMaxValue()) {
            return MutationResult.rejected(
                    CODE_BOUNDS,
                    "global modifier must be within ["
                            + format(policy.globalMinValue())
                            + ", "
                            + format(policy.globalMaxValue())
                            + "]");
        }

        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        double previous = sanitizeGlobalValue(level, policy);
        if (previous == requestedValue) {
            return MutationResult.committed(previous, previous, Optional.empty());
        }
        worldData.setGlobalDifficultyModifier(requestedValue);
        worldData.setGlobalDifficultyUpdatedAtMillis(System.currentTimeMillis());
        worldData.setGlobalDifficultyUpdatedBy(actor);
        worldData.markDirty();
        return MutationResult.committed(previous, requestedValue, Optional.empty());
    }

    public MutationResult resetGlobalModifier(ServerLevel level, String actor) {
        PolicySnapshot policy = policySnapshot();
        if (!policy.globalEnabled()) {
            return MutationResult.rejected(
                    policy.globalDiagnosticCode().isBlank() ? WorldAwakenedDiagnosticCodes.DIFFICULTY_GLOBAL_INVALID : policy.globalDiagnosticCode(),
                    policy.globalDiagnosticDetail().isBlank() ? "global_modifier_disabled_or_invalid" : policy.globalDiagnosticDetail());
        }
        double resetValue = clamp(policy.globalDefaultValue(), policy.globalMinValue(), policy.globalMaxValue());
        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        double previous = sanitizeGlobalValue(level, policy);
        worldData.setGlobalDifficultyModifier(resetValue);
        worldData.setGlobalDifficultyUpdatedAtMillis(System.currentTimeMillis());
        worldData.setGlobalDifficultyUpdatedBy(actor);
        worldData.markDirty();
        return MutationResult.committed(previous, resetValue, Optional.empty());
    }

    public ChallengeReadResult readChallengeState(
            ServerLevel level,
            ServerPlayer player,
            ChallengeScope requestedScope) {
        PolicySnapshot policy = policySnapshot();
        ScopeResolution scopeResolution = resolveScope(policy, requestedScope, player != null);
        if (!scopeResolution.valid()) {
            return ChallengeReadResult.rejected(
                    scopeResolution.code(),
                    scopeResolution.detail(),
                    ChallengeModifierState.disabled(
                            scopeResolution.resolvedScope(),
                            policy.challengeDefaultValue(),
                            policy.challengeMinValue(),
                            policy.challengeMaxValue(),
                            policy.challengeStep(),
                            policy.challengeAllowRaise(),
                            policy.challengeAllowLower(),
                            policy.challengeAllowPlayerAdjustment(),
                            requiresVote(policy)));
        }
        if (!policy.challengeEnabled()) {
            return ChallengeReadResult.rejected(
                    policy.challengeDiagnosticCode().isBlank()
                            ? WorldAwakenedDiagnosticCodes.CHALLENGE_MODE_UNSUPPORTED
                            : policy.challengeDiagnosticCode(),
                    policy.challengeDiagnosticDetail().isBlank() ? "challenge_disabled_or_invalid" : policy.challengeDiagnosticDetail(),
                    ChallengeModifierState.disabled(
                            scopeResolution.resolvedScope(),
                            policy.challengeDefaultValue(),
                            policy.challengeMinValue(),
                            policy.challengeMaxValue(),
                            policy.challengeStep(),
                            policy.challengeAllowRaise(),
                            policy.challengeAllowLower(),
                            policy.challengeAllowPlayerAdjustment(),
                            requiresVote(policy)));
        }

        long now = System.currentTimeMillis();
        if (scopeResolution.scope() == ChallengeScope.PLAYER) {
            if (player == null) {
                return ChallengeReadResult.rejected(
                        WorldAwakenedDiagnosticCodes.CHALLENGE_SCOPE_INVALID,
                        "player scope requires player context",
                        ChallengeModifierState.disabled(
                                scopeResolution.resolvedScope(),
                                policy.challengeDefaultValue(),
                                policy.challengeMinValue(),
                                policy.challengeMaxValue(),
                                policy.challengeStep(),
                                policy.challengeAllowRaise(),
                                policy.challengeAllowLower(),
                                policy.challengeAllowPlayerAdjustment(),
                                requiresVote(policy)));
            }
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state =
                    WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(player.getUUID());
            double value = sanitizePlayerChallengeValue(level, player, state, policy);
            long cooldownRemaining = Math.max(0L, state.challengeCooldownUntilMillis() - now);
            return ChallengeReadResult.allowed(new ChallengeModifierState(
                    true,
                    scopeResolution.resolvedScope(),
                    value,
                    policy.challengeDefaultValue(),
                    policy.challengeMinValue(),
                    policy.challengeMaxValue(),
                    policy.challengeStep(),
                    cooldownRemaining,
                    state.challengeChangeCount(),
                    policy.challengeMaxChangesPerPlayer(),
                    policy.challengeAllowRaise(),
                    policy.challengeAllowLower(),
                    policy.challengeAllowPlayerAdjustment(),
                    requiresVote(policy),
                    Optional.empty(),
                    "",
                    ""));
        }

        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        expireTimedOutVote(worldData, now);
        double value = sanitizeWorldChallengeValue(level, policy);
        long cooldownRemaining = Math.max(0L, worldData.challengeWorldCooldownUntilMillis() - now);
        return ChallengeReadResult.allowed(new ChallengeModifierState(
                true,
                scopeResolution.resolvedScope(),
                value,
                policy.challengeDefaultValue(),
                policy.challengeMinValue(),
                policy.challengeMaxValue(),
                policy.challengeStep(),
                cooldownRemaining,
                worldData.challengeWorldChangeCount(),
                policy.challengeMaxWorldChanges(),
                policy.challengeAllowRaise(),
                policy.challengeAllowLower(),
                policy.challengeAllowPlayerAdjustment(),
                requiresVote(policy),
                activeVoteState(worldData, policy),
                "",
                ""));
    }

    public MutationResult setChallengeModifier(
            ServerLevel level,
            ServerPlayer actorPlayer,
            boolean actorIsOperator,
            ChallengeScope requestedScope,
            double requestedValue,
            String actorName) {
        PolicySnapshot policy = policySnapshot();
        ScopeResolution scopeResolution = resolveScope(policy, requestedScope, actorPlayer != null);
        if (!scopeResolution.valid()) {
            return MutationResult.rejected(scopeResolution.code(), scopeResolution.detail());
        }
        if (!policy.challengeEnabled()) {
            return MutationResult.rejected(
                    policy.challengeDiagnosticCode().isBlank()
                            ? WorldAwakenedDiagnosticCodes.CHALLENGE_MODE_UNSUPPORTED
                            : policy.challengeDiagnosticCode(),
                    policy.challengeDiagnosticDetail().isBlank() ? "challenge_disabled_or_invalid" : policy.challengeDiagnosticDetail());
        }
        if (!Double.isFinite(requestedValue)) {
            return MutationResult.rejected(CODE_BOUNDS, "value must be finite");
        }
        if (requestedValue < policy.challengeMinValue() || requestedValue > policy.challengeMaxValue()) {
            return MutationResult.rejected(
                    CODE_BOUNDS,
                    "challenge modifier must be within ["
                            + format(policy.challengeMinValue())
                            + ", "
                            + format(policy.challengeMaxValue())
                            + "]");
        }
        if (!isStepAligned(requestedValue, policy.challengeDefaultValue(), policy.challengeStep())) {
            return MutationResult.rejected(
                    CODE_STEP_INVALID,
                    "challenge modifier must align to step "
                            + format(policy.challengeStep())
                            + " from anchor "
                            + format(policy.challengeDefaultValue()));
        }
        if (requestedScope == ChallengeScope.PLAYER && actorPlayer == null) {
            return MutationResult.rejected(CODE_SCOPE_INVALID, "personal scope requires a player source");
        }
        if (!actorIsOperator && !policy.challengeAllowPlayerAdjustment()) {
            return MutationResult.rejected(CODE_POLICY_DISALLOWS_CHANGE, "player adjustment is disabled by policy");
        }

        if (requestedScope == ChallengeScope.PLAYER) {
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state =
                    WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(actorPlayer.getUUID());
            double current = sanitizePlayerChallengeValue(level, actorPlayer, state, policy);
            Optional<MutationResult> transitionRejection = validateChallengeTransition(
                    current,
                    requestedValue,
                    policy.challengeAllowRaise(),
                    policy.challengeAllowLower());
            if (transitionRejection.isPresent()) {
                return transitionRejection.get();
            }
            long now = System.currentTimeMillis();
            if (state.challengeCooldownUntilMillis() > now) {
                return MutationResult.rejected(
                        CODE_COOLDOWN_ACTIVE,
                        "cooldown_remaining_millis=" + (state.challengeCooldownUntilMillis() - now));
            }
            if (policy.challengeMaxChangesPerPlayer() > 0
                    && state.challengeChangeCount() >= policy.challengeMaxChangesPerPlayer()) {
                return MutationResult.rejected(CODE_USAGE_EXHAUSTED, "player challenge usage exhausted");
            }
            state.setChallengeModifier(requestedValue);
            state.setChallengeUpdatedAtMillis(now);
            state.setChallengeUpdatedBy(actorName);
            state.setChallengeCooldownUntilMillis(now + cooldownMillis(policy.challengeCooldownMinutes()));
            state.setChallengeChangeCount(state.challengeChangeCount() + 1);
            state.markDirty();
            return MutationResult.committed(current, requestedValue, Optional.empty());
        }

        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        long now = System.currentTimeMillis();
        expireTimedOutVote(worldData, now);
        double current = sanitizeWorldChallengeValue(level, policy);
        Optional<MutationResult> transitionRejection = validateChallengeTransition(
                current,
                requestedValue,
                policy.challengeAllowRaise(),
                policy.challengeAllowLower());
        if (transitionRejection.isPresent()) {
            return transitionRejection.get();
        }
        if (worldData.challengeWorldCooldownUntilMillis() > now) {
            return MutationResult.rejected(
                    CODE_COOLDOWN_ACTIVE,
                    "cooldown_remaining_millis=" + (worldData.challengeWorldCooldownUntilMillis() - now));
        }
        if (policy.challengeMaxWorldChanges() > 0
                && worldData.challengeWorldChangeCount() >= policy.challengeMaxWorldChanges()) {
            return MutationResult.rejected(CODE_USAGE_EXHAUSTED, "world challenge usage exhausted");
        }

        if (requiresVote(policy) && !(actorIsOperator && policy.challengeAdminOverride())) {
            if (!policy.voteConfigValid()) {
                return MutationResult.rejected(
                        WorldAwakenedDiagnosticCodes.CHALLENGE_VOTE_CONFIG_INVALID,
                        "vote policy is required but vote config is invalid");
            }
            if (worldData.challengeWorldVoteActive()) {
                return MutationResult.rejected(CODE_VOTE_ACTIVE, "a challenge vote is already active");
            }
            startVote(worldData, level, actorPlayer, requestedValue, now);
            return MutationResult.voteStarted(current, activeVoteState(worldData, policy));
        }

        return commitWorldChallenge(worldData, policy, current, requestedValue, now, actorName);
    }

    public MutationResult submitVote(
            ServerLevel level,
            ServerPlayer voter,
            boolean voteYes,
            String actorName) {
        PolicySnapshot policy = policySnapshot();
        if (!policy.challengeEnabled()) {
            return MutationResult.rejected(
                    policy.challengeDiagnosticCode().isBlank()
                            ? WorldAwakenedDiagnosticCodes.CHALLENGE_MODE_UNSUPPORTED
                            : policy.challengeDiagnosticCode(),
                    policy.challengeDiagnosticDetail().isBlank() ? "challenge_disabled_or_invalid" : policy.challengeDiagnosticDetail());
        }
        if (!requiresVote(policy)) {
            return MutationResult.rejected(CODE_VOTE_INACTIVE, "vote flow is not active for current policy");
        }
        if (!policy.voteConfigValid()) {
            return MutationResult.rejected(
                    WorldAwakenedDiagnosticCodes.CHALLENGE_VOTE_CONFIG_INVALID,
                    "vote policy is invalid");
        }

        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        long now = System.currentTimeMillis();
        expireTimedOutVote(worldData, now);
        if (!worldData.challengeWorldVoteActive()) {
            return MutationResult.rejected(CODE_VOTE_INACTIVE, "no active challenge vote");
        }
        String voterId = voter.getUUID().toString();
        if (!worldData.challengeWorldVoteEligible().contains(voterId)) {
            return MutationResult.rejected(CODE_UNAUTHORIZED, "you are not eligible for this vote");
        }

        worldData.challengeWorldVoteYes().remove(voterId);
        worldData.challengeWorldVoteNo().remove(voterId);
        if (voteYes) {
            worldData.challengeWorldVoteYes().add(voterId);
        } else {
            worldData.challengeWorldVoteNo().add(voterId);
        }
        worldData.markDirty();

        int eligible = worldData.challengeWorldVoteEligible().size();
        int requiredYes = Math.max(1, (int) Math.ceil(eligible * policy.challengeVoteThreshold()));
        int yes = worldData.challengeWorldVoteYes().size();
        int no = worldData.challengeWorldVoteNo().size();
        if (yes >= requiredYes) {
            double current = sanitizeWorldChallengeValue(level, policy);
            MutationResult commit = commitWorldChallenge(
                    worldData,
                    policy,
                    current,
                    worldData.challengeWorldVoteTarget(),
                    now,
                    "vote:" + actorName);
            clearVote(worldData);
            return commit;
        }
        int remaining = eligible - yes - no;
        if (yes + remaining < requiredYes) {
            clearVote(worldData);
            return MutationResult.voteRecorded(
                    sanitizeWorldChallengeValue(level, policy),
                    Optional.empty(),
                    "vote_failed",
                    "vote threshold can no longer be reached");
        }
        return MutationResult.voteRecorded(
                sanitizeWorldChallengeValue(level, policy),
                activeVoteState(worldData, policy),
                "",
                "");
    }

    public ScalarBreakdown resolveSpawnPressureScalar(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation dimensionId,
            double baseValue,
            Map<String, Double> integrationScalars,
            double clampMin,
            double clampMax,
            SpawnPressureContext pressureContext) {
        PolicySnapshot policy = policySnapshot();
        return resolveScalar(
                level,
                player,
                dimensionId,
                baseValue,
                integrationScalars,
                clampMin,
                clampMax,
                true,
                pressureContext,
                policy);
    }

    public ScalarBreakdown resolveDifficultyScalar(
            ServerLevel level,
            ServerPlayer player,
            double baseValue,
            Map<String, Double> integrationScalars,
            double clampMin,
            double clampMax,
            String sourceKey) {
        PolicySnapshot policy = policySnapshot();
        return resolveScalar(
                level,
                player,
                level.dimension().location(),
                baseValue,
                integrationScalars,
                clampMin,
                clampMax,
                false,
                new SpawnPressureContext(true, true, false, sourceKey),
                policy);
    }

    private ScalarBreakdown resolveScalar(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation dimensionId,
            double baseValue,
            Map<String, Double> integrationScalars,
            double clampMin,
            double clampMax,
            boolean includeDimensionBaseline,
            SpawnPressureContext pressureContext,
            PolicySnapshot policy) {
        List<String> policyGates = new ArrayList<>();
        Map<String, String> provenance = new LinkedHashMap<>();
        Map<String, Double> normalizedIntegration = normalizeIntegrationScalars(integrationScalars, policyGates);

        double sanitizedBase = Double.isFinite(baseValue) ? baseValue : 1.0D;
        if (!Double.isFinite(baseValue)) {
            policyGates.add("base_value_non_finite_defaulted");
        }

        double dimensionBaseline = includeDimensionBaseline ? dimensionBaseline(dimensionId) : 1.0D;
        double globalModifier = resolveGlobalModifier(level, policy, policyGates);
        ResolvedChallenge challenge = resolveChallengeModifier(level, player, policy, policyGates);
        double integrationProduct = normalizedIntegration.values().stream().reduce(1.0D, (left, right) -> left * right);

        provenance.put("source_key", pressureContext.sourceKey().isBlank() ? "<unspecified>" : pressureContext.sourceKey());
        provenance.put("dimension_source", includeDimensionBaseline ? "config.spawning.pressure_dimension_baselines" : "<none>");
        provenance.put("global_source", "world_saved_data.global_difficulty_modifier");
        provenance.put("challenge_source", challenge.challengeSource());
        provenance.put("integration_source", normalizedIntegration.isEmpty() ? "<none>" : "runtime.integration_scalars");

        double composed = sanitizedBase
                * dimensionBaseline
                * globalModifier
                * challenge.value()
                * integrationProduct;

        boolean categoryGateBlocked = false;
        if (includeDimensionBaseline) {
            if (!WorldAwakenedCommonConfig.ENABLE_SPAWN_SCALING.get()) {
                policyGates.add("spawn_scaling_disabled");
                composed = sanitizedBase;
            } else if (pressureContext.peacefulBlocked()) {
                policyGates.add("peaceful_gate_blocked");
                composed = sanitizedBase;
            } else if (!pressureContext.categoryRestrictionDataAvailable()) {
                policyGates.add("category_restriction_missing_fail_closed");
                categoryGateBlocked = true;
                composed = sanitizedBase;
            } else if (!pressureContext.categoryAllowed()) {
                policyGates.add("category_restriction_blocked");
                categoryGateBlocked = true;
                composed = sanitizedBase;
            }
        }

        double safeClampMin = Double.isFinite(clampMin) ? clampMin : 0.0D;
        double safeClampMax = Double.isFinite(clampMax) ? clampMax : Math.max(1.0D, safeClampMin);
        if (safeClampMax < safeClampMin) {
            double swap = safeClampMin;
            safeClampMin = safeClampMax;
            safeClampMax = swap;
            policyGates.add("invalid_clamp_range_swapped");
        }
        double clamped = clamp(composed, safeClampMin, safeClampMax);
        String clampReason = clamped == composed
                ? ""
                : "clamped_to_"
                        + (clamped <= safeClampMin ? "min" : "max")
                        + "_range[min="
                        + format(safeClampMin)
                        + ",max="
                        + format(safeClampMax)
                        + "]";

        return new ScalarBreakdown(
                sanitizedBase,
                dimensionBaseline,
                globalModifier,
                challenge.value(),
                normalizedIntegration,
                composed,
                clamped,
                clampReason,
                challenge.scopeUsed(),
                List.copyOf(policyGates),
                provenance,
                pressureContext.peacefulBlocked(),
                categoryGateBlocked,
                pressureContext.categoryRestrictionDataAvailable());
    }

    private MutationResult commitWorldChallenge(
            WorldAwakenedWorldProgressionSavedData worldData,
            PolicySnapshot policy,
            double current,
            double next,
            long now,
            String actorName) {
        worldData.setChallengeWorldModifier(next);
        worldData.setChallengeWorldUpdatedAtMillis(now);
        worldData.setChallengeWorldUpdatedBy(actorName);
        worldData.setChallengeWorldCooldownUntilMillis(now + cooldownMillis(policy.challengeCooldownMinutes()));
        worldData.setChallengeWorldChangeCount(worldData.challengeWorldChangeCount() + 1);
        worldData.markDirty();
        return MutationResult.committed(current, next, activeVoteState(worldData, policy));
    }

    private static Optional<MutationResult> validateChallengeTransition(
            double current,
            double requested,
            boolean allowRaise,
            boolean allowLower) {
        if (requested > current && !allowRaise) {
            return Optional.of(MutationResult.rejected(CODE_POLICY_DISALLOWS_CHANGE, "raising challenge is disabled by policy"));
        }
        if (requested < current && !allowLower) {
            return Optional.of(MutationResult.rejected(CODE_POLICY_DISALLOWS_CHANGE, "lowering challenge is disabled by policy"));
        }
        return Optional.empty();
    }

    private static long cooldownMillis(int cooldownMinutes) {
        return Math.max(0L, cooldownMinutes) * 60_000L;
    }

    private void startVote(
            WorldAwakenedWorldProgressionSavedData worldData,
            ServerLevel level,
            ServerPlayer initiator,
            double requestedValue,
            long now) {
        clearVote(worldData);
        Set<String> eligible = new LinkedHashSet<>();
        if (level.getServer() != null) {
            List<ServerPlayer> players = new ArrayList<>(level.getServer().getPlayerList().getPlayers());
            players.sort((left, right) -> left.getUUID().compareTo(right.getUUID()));
            for (ServerPlayer player : players) {
                eligible.add(player.getUUID().toString());
            }
        }
        if (eligible.isEmpty() && initiator != null) {
            eligible.add(initiator.getUUID().toString());
        }
        worldData.setChallengeWorldVoteActive(true);
        worldData.setChallengeWorldVoteTarget(requestedValue);
        worldData.setChallengeWorldVoteInitiator(initiator == null ? "" : initiator.getUUID().toString());
        worldData.setChallengeWorldVoteStartedAtMillis(now);
        worldData.setChallengeWorldVoteTimeoutAtMillis(
                now + (long) WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_VOTE_TIMEOUT_SECONDS.get() * 1000L);
        worldData.challengeWorldVoteEligible().addAll(eligible);
        worldData.markDirty();
    }

    private void clearVote(WorldAwakenedWorldProgressionSavedData worldData) {
        worldData.setChallengeWorldVoteActive(false);
        worldData.setChallengeWorldVoteTarget(1.0D);
        worldData.setChallengeWorldVoteInitiator("");
        worldData.setChallengeWorldVoteStartedAtMillis(0L);
        worldData.setChallengeWorldVoteTimeoutAtMillis(0L);
        worldData.challengeWorldVoteEligible().clear();
        worldData.challengeWorldVoteYes().clear();
        worldData.challengeWorldVoteNo().clear();
        worldData.markDirty();
    }

    private void expireTimedOutVote(WorldAwakenedWorldProgressionSavedData worldData, long now) {
        if (worldData.challengeWorldVoteActive() && worldData.challengeWorldVoteTimeoutAtMillis() <= now) {
            clearVote(worldData);
        }
    }

    private Optional<ChallengeVoteState> activeVoteState(
            WorldAwakenedWorldProgressionSavedData worldData,
            PolicySnapshot policy) {
        if (!worldData.challengeWorldVoteActive()) {
            return Optional.empty();
        }
        return Optional.of(new ChallengeVoteState(
                true,
                worldData.challengeWorldVoteTarget(),
                worldData.challengeWorldVoteInitiator(),
                worldData.challengeWorldVoteStartedAtMillis(),
                worldData.challengeWorldVoteTimeoutAtMillis(),
                Set.copyOf(worldData.challengeWorldVoteEligible()),
                Set.copyOf(worldData.challengeWorldVoteYes()),
                Set.copyOf(worldData.challengeWorldVoteNo()),
                policy.challengeVoteThreshold()));
    }

    private double resolveGlobalModifier(
            ServerLevel level,
            PolicySnapshot policy,
            List<String> policyGates) {
        if (!policy.globalEnabled()) {
            policyGates.add(policy.globalDiagnosticCode().isBlank() ? "global_disabled" : policy.globalDiagnosticCode());
            return 1.0D;
        }
        return sanitizeGlobalValue(level, policy);
    }

    private ResolvedChallenge resolveChallengeModifier(
            ServerLevel level,
            ServerPlayer player,
            PolicySnapshot policy,
            List<String> policyGates) {
        if (!policy.challengeEnabled()) {
            policyGates.add(policy.challengeDiagnosticCode().isBlank() ? "challenge_disabled" : policy.challengeDiagnosticCode());
            return ResolvedChallenge.disabled();
        }

        ScopeResolution scopeResolution = resolveScope(policy, null, player != null);
        if (!scopeResolution.valid()) {
            policyGates.add(scopeResolution.code());
            return ResolvedChallenge.unresolved();
        }

        if (scopeResolution.scope() == ChallengeScope.PLAYER) {
            if (player == null) {
                policyGates.add(WorldAwakenedDiagnosticCodes.CHALLENGE_SCOPE_INVALID);
                return ResolvedChallenge.unresolved();
            }
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state =
                    WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(player.getUUID());
            double value = sanitizePlayerChallengeValue(level, player, state, policy);
            return new ResolvedChallenge(value, "player", "player_saved_data.challenge_modifier");
        }

        double value = sanitizeWorldChallengeValue(level, policy);
        return new ResolvedChallenge(value, "world", "world_saved_data.challenge_world_modifier");
    }

    private ScopeResolution resolveScope(
            PolicySnapshot policy,
            ChallengeScope requestedScope,
            boolean hasPlayerContext) {
        if (!policy.challengeEnabled()) {
            return ScopeResolution.invalid(
                    policy.challengeDiagnosticCode().isBlank()
                            ? WorldAwakenedDiagnosticCodes.CHALLENGE_MODE_UNSUPPORTED
                            : policy.challengeDiagnosticCode(),
                    policy.challengeDiagnosticDetail().isBlank() ? "challenge_disabled_or_invalid" : policy.challengeDiagnosticDetail(),
                    "disabled");
        }

        ChallengeScope resolved = switch (policy.challengeScopeMode()) {
            case PLAYER -> ChallengeScope.PLAYER;
            case WORLD -> ChallengeScope.WORLD;
            case AUTO -> {
                WorldAwakenedProgressionMode progressionMode =
                        WorldAwakenedProgressionMode.fromConfig(WorldAwakenedCommonConfig.PROGRESSION_MODE.get());
                yield progressionMode == WorldAwakenedProgressionMode.PER_PLAYER
                        ? ChallengeScope.PLAYER
                        : ChallengeScope.WORLD;
            }
            case UNSUPPORTED -> null;
        };
        if (resolved == null) {
            return ScopeResolution.invalid(
                    WorldAwakenedDiagnosticCodes.CHALLENGE_MODE_UNSUPPORTED,
                    "unsupported challenge scope mode",
                    "unsupported");
        }
        if (requestedScope != null && resolved != requestedScope) {
            return ScopeResolution.invalid(
                    WorldAwakenedDiagnosticCodes.CHALLENGE_SCOPE_INVALID,
                    "requested scope "
                            + requestedScope.serializedName()
                            + " does not match resolved scope "
                            + resolved.serializedName(),
                    resolved.serializedName());
        }
        if (resolved == ChallengeScope.PLAYER && !hasPlayerContext) {
            return ScopeResolution.invalid(
                    WorldAwakenedDiagnosticCodes.CHALLENGE_SCOPE_INVALID,
                    "resolved player scope requires a player context",
                    resolved.serializedName());
        }
        return ScopeResolution.valid(resolved, resolved.serializedName());
    }

    private double sanitizeGlobalValue(
            ServerLevel level,
            PolicySnapshot policy) {
        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        double raw = worldData.globalDifficultyModifier();
        double clamped = sanitizeValue(raw, policy.globalMinValue(), policy.globalMaxValue(), policy.globalDefaultValue());
        if (clamped != raw) {
            WorldAwakenedLog.warn(
                    LOGGER,
                    WorldAwakenedLogCategory.CONFIG,
                    "code={} detail=clamped global difficulty modifier from {} to {}",
                    WorldAwakenedDiagnosticCodes.DIFFICULTY_GLOBAL_INVALID,
                    raw,
                    clamped);
            worldData.setGlobalDifficultyModifier(clamped);
            worldData.markDirty();
        }
        return clamped;
    }

    private double sanitizeWorldChallengeValue(
            ServerLevel level,
            PolicySnapshot policy) {
        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        double raw = worldData.challengeWorldModifier();
        double clamped = sanitizeValue(raw, policy.challengeMinValue(), policy.challengeMaxValue(), policy.challengeDefaultValue());
        if (clamped != raw) {
            WorldAwakenedLog.warn(
                    LOGGER,
                    WorldAwakenedLogCategory.CONFIG,
                    "code={} detail=clamped world challenge modifier from {} to {}",
                    WorldAwakenedDiagnosticCodes.CHALLENGE_BOUNDS_INVALID,
                    raw,
                    clamped);
            worldData.setChallengeWorldModifier(clamped);
            worldData.markDirty();
        }
        return clamped;
    }

    private double sanitizePlayerChallengeValue(
            ServerLevel level,
            ServerPlayer player,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            PolicySnapshot policy) {
        double raw = state.challengeModifier();
        double clamped = sanitizeValue(raw, policy.challengeMinValue(), policy.challengeMaxValue(), policy.challengeDefaultValue());
        if (clamped != raw) {
            WorldAwakenedLog.warn(
                    LOGGER,
                    WorldAwakenedLogCategory.CONFIG,
                    "code={} detail=clamped player challenge modifier from {} to {} player={}",
                    WorldAwakenedDiagnosticCodes.CHALLENGE_BOUNDS_INVALID,
                    raw,
                    clamped,
                    player.getUUID());
            state.setChallengeModifier(clamped);
            state.markDirty();
        }
        return clamped;
    }

    private static double sanitizeValue(double raw, double min, double max, double fallback) {
        double value = Double.isFinite(raw) ? raw : fallback;
        return clamp(value, min, max);
    }

    private double dimensionBaseline(ResourceLocation dimensionId) {
        return dimensionBaselines().getOrDefault(dimensionId, 1.0D);
    }

    private Map<String, Double> normalizeIntegrationScalars(
            Map<String, Double> integrationScalars,
            List<String> policyGates) {
        if (integrationScalars == null || integrationScalars.isEmpty()) {
            return Map.of();
        }
        List<String> keys = new ArrayList<>(integrationScalars.keySet());
        Collections.sort(keys);
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Double raw = integrationScalars.get(key);
            if (raw == null || !Double.isFinite(raw)) {
                policyGates.add("integration_scalar_invalid:" + key);
                continue;
            }
            normalized.put(key, raw);
        }
        return Map.copyOf(normalized);
    }

    private Map<ResourceLocation, Double> dimensionBaselines() {
        List<? extends String> configured = WorldAwakenedCommonConfig.SPAWN_PRESSURE_DIMENSION_BASELINES.get();
        List<String> normalizedConfig = configured == null
                ? List.of()
                : configured.stream()
                        .filter(entry -> entry != null && !entry.isBlank())
                        .map(String::trim)
                        .toList();
        DimensionBaselineCache cached = dimensionBaselineCache.get();
        if (cached.rawEntries().equals(normalizedConfig)) {
            return cached.baselines();
        }

        Map<ResourceLocation, Double> parsed = new LinkedHashMap<>();
        for (String entry : normalizedConfig) {
            int equalsIndex = entry.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == entry.length() - 1) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "code={} detail=invalid dimension baseline entry '{}'",
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        entry);
                continue;
            }
            String dimensionPart = entry.substring(0, equalsIndex).trim();
            String valuePart = entry.substring(equalsIndex + 1).trim();
            ResourceLocation dimensionId = ResourceLocation.tryParse(dimensionPart);
            if (dimensionId == null) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "code={} detail=invalid dimension id '{}' in baseline entry '{}'",
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        dimensionPart,
                        entry);
                continue;
            }
            double baseline;
            try {
                baseline = Double.parseDouble(valuePart);
            } catch (NumberFormatException exception) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "code={} detail=invalid baseline value '{}' in '{}'",
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        valuePart,
                        entry);
                continue;
            }
            if (!Double.isFinite(baseline) || baseline <= 0.0D) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "code={} detail=non-positive baseline value '{}' in '{}'",
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        valuePart,
                        entry);
                continue;
            }
            parsed.put(dimensionId, baseline);
        }

        DimensionBaselineCache refreshed = new DimensionBaselineCache(
                List.copyOf(normalizedConfig),
                Map.copyOf(parsed));
        dimensionBaselineCache.set(refreshed);
        return refreshed.baselines();
    }

    private static boolean requiresVote(PolicySnapshot policy) {
        if (!policy.challengeEnabled()) {
            return false;
        }
        if (!policy.challengeRequireVoteInGlobal()) {
            return false;
        }
        WorldAwakenedProgressionMode progressionMode =
                WorldAwakenedProgressionMode.fromConfig(WorldAwakenedCommonConfig.PROGRESSION_MODE.get());
        return progressionMode == WorldAwakenedProgressionMode.GLOBAL;
    }

    private static boolean isStepAligned(double value, double anchorValue, double stepValue) {
        BigDecimal valueDecimal = BigDecimal.valueOf(value);
        BigDecimal anchorDecimal = BigDecimal.valueOf(anchorValue);
        BigDecimal stepDecimal = BigDecimal.valueOf(stepValue);
        if (stepDecimal.compareTo(BIG_DECIMAL_ZERO) <= 0) {
            return false;
        }
        BigDecimal offset = valueDecimal.subtract(anchorDecimal);
        BigDecimal[] division = offset.divideAndRemainder(stepDecimal);
        BigDecimal remainder = division[1].setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        return remainder.compareTo(BIG_DECIMAL_ZERO) == 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static PolicySnapshot policySnapshot() {
        boolean modEnabled = WorldAwakenedFeatureGates.modEnabled();

        double globalDefault = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_VALUE.get();
        double globalMin = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MIN_VALUE.get();
        double globalMax = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MAX_VALUE.get();
        boolean globalEnabled = modEnabled && WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_ENABLED.get();
        String globalDiagnosticCode = "";
        String globalDiagnosticDetail = "";
        if (!(globalMin <= globalMax && globalDefault >= globalMin && globalDefault <= globalMax)) {
            globalEnabled = false;
            globalDiagnosticCode = WorldAwakenedDiagnosticCodes.DIFFICULTY_GLOBAL_INVALID;
            globalDiagnosticDetail = "global min/max/default are inconsistent";
        }

        double challengeDefault = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_DEFAULT_VALUE.get();
        double challengeMin = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_MIN_VALUE.get();
        double challengeMax = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_MAX_VALUE.get();
        double challengeStep = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_STEP.get();
        int challengeCooldownMinutes = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_COOLDOWN_MINUTES.get();
        int challengeMaxPerPlayer = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_MAX_CHANGES_PER_PLAYER.get();
        int challengeMaxWorld = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_MAX_WORLD_CHANGES.get();
        boolean challengeAllowPlayerAdjustment = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_ALLOW_PLAYER_ADJUSTMENT.get();
        boolean challengeAllowRaise = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_ALLOW_RAISE.get();
        boolean challengeAllowLower = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_ALLOW_LOWER.get();
        boolean challengeRequireVote = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_REQUIRE_VOTE_IN_GLOBAL.get();
        double challengeVoteThreshold = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_VOTE_THRESHOLD.get();
        int challengeVoteTimeout = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_VOTE_TIMEOUT_SECONDS.get();
        boolean challengeAdminOverride = WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_ADMIN_OVERRIDE.get();
        ScopeMode scopeMode = ScopeMode.fromConfig(WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_SCOPE_MODE.get());
        boolean challengeEnabled = modEnabled && WorldAwakenedCommonConfig.DIFFICULTY_CHALLENGE_ENABLED.get();
        String challengeDiagnosticCode = "";
        String challengeDiagnosticDetail = "";

        if (scopeMode == ScopeMode.UNSUPPORTED) {
            challengeEnabled = false;
            challengeDiagnosticCode = WorldAwakenedDiagnosticCodes.CHALLENGE_MODE_UNSUPPORTED;
            challengeDiagnosticDetail = "scope_mode is unsupported";
        }
        if (challengeEnabled && !(challengeMin <= challengeMax && challengeDefault >= challengeMin && challengeDefault <= challengeMax)) {
            challengeEnabled = false;
            challengeDiagnosticCode = WorldAwakenedDiagnosticCodes.CHALLENGE_BOUNDS_INVALID;
            challengeDiagnosticDetail = "challenge min/max/default are inconsistent";
        }
        if (challengeEnabled && (challengeStep <= 0.0D || !Double.isFinite(challengeStep) || challengeCooldownMinutes < 0)) {
            challengeEnabled = false;
            challengeDiagnosticCode = WorldAwakenedDiagnosticCodes.CHALLENGE_STEP_INVALID;
            challengeDiagnosticDetail = "challenge step or cooldown is invalid";
        }

        boolean voteConfigValid = challengeVoteThreshold > 0.0D
                && challengeVoteThreshold <= 1.0D
                && challengeVoteTimeout > 0;
        if (challengeEnabled && challengeRequireVote && !voteConfigValid) {
            challengeDiagnosticCode = WorldAwakenedDiagnosticCodes.CHALLENGE_VOTE_CONFIG_INVALID;
            challengeDiagnosticDetail = "challenge vote settings are invalid";
        }

        return new PolicySnapshot(
                globalEnabled,
                globalDefault,
                globalMin,
                globalMax,
                globalDiagnosticCode,
                globalDiagnosticDetail,
                challengeEnabled,
                scopeMode,
                challengeDefault,
                challengeMin,
                challengeMax,
                challengeStep,
                challengeCooldownMinutes,
                challengeMaxPerPlayer,
                challengeMaxWorld,
                challengeAllowPlayerAdjustment,
                challengeAllowRaise,
                challengeAllowLower,
                challengeRequireVote,
                challengeVoteThreshold,
                challengeVoteTimeout,
                challengeAdminOverride,
                voteConfigValid,
                challengeDiagnosticCode,
                challengeDiagnosticDetail);
    }

    public enum ChallengeScope {
        PLAYER("player"),
        WORLD("world");

        private final String serializedName;

        ChallengeScope(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    private enum ScopeMode {
        AUTO,
        PLAYER,
        WORLD,
        UNSUPPORTED;

        private static ScopeMode fromConfig(String value) {
            if (value == null) {
                return AUTO;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "auto" -> AUTO;
                case "player" -> PLAYER;
                case "world" -> WORLD;
                default -> UNSUPPORTED;
            };
        }
    }

    public record SpawnPressureContext(
            boolean categoryRestrictionDataAvailable,
            boolean categoryAllowed,
            boolean peacefulBlocked,
            String sourceKey) {
    }

    public record ScalarBreakdown(
            double baseValue,
            double dimensionBaseline,
            double globalModifier,
            double challengeModifier,
            Map<String, Double> integrationScalars,
            double unclampedEffectiveValue,
            double clampedEffectiveValue,
            String clampReason,
            String challengeScopeUsed,
            List<String> policyGatesConsulted,
            Map<String, String> provenance,
            boolean peacefulGateBlocked,
            boolean categoryGateBlocked,
            boolean categoryRestrictionDataAvailable) {
    }

    public record GlobalModifierState(
            boolean enabled,
            double value,
            double defaultValue,
            double minValue,
            double maxValue,
            String diagnosticCode,
            String diagnosticDetail) {
    }

    public record ChallengeModifierState(
            boolean enabled,
            String resolvedScope,
            double value,
            double defaultValue,
            double minValue,
            double maxValue,
            double step,
            long cooldownRemainingMillis,
            int changeCount,
            int maxChanges,
            boolean allowRaise,
            boolean allowLower,
            boolean allowPlayerAdjustment,
            boolean voteRequired,
            Optional<ChallengeVoteState> voteState,
            String diagnosticCode,
            String diagnosticDetail) {
        static ChallengeModifierState disabled(
                String resolvedScope,
                double defaultValue,
                double minValue,
                double maxValue,
                double step,
                boolean allowRaise,
                boolean allowLower,
                boolean allowPlayerAdjustment,
                boolean voteRequired) {
            return new ChallengeModifierState(
                    false,
                    resolvedScope,
                    1.0D,
                    defaultValue,
                    minValue,
                    maxValue,
                    step,
                    0L,
                    0,
                    0,
                    allowRaise,
                    allowLower,
                    allowPlayerAdjustment,
                    voteRequired,
                    Optional.empty(),
                    "",
                    "");
        }
    }

    public record ChallengeVoteState(
            boolean active,
            double targetValue,
            String initiatorUuid,
            long startedAtMillis,
            long timeoutAtMillis,
            Set<String> eligibleVoters,
            Set<String> yesVotes,
            Set<String> noVotes,
            double threshold) {
    }

    public record ChallengeReadResult(
            boolean success,
            String code,
            String detail,
            ChallengeModifierState state) {
        static ChallengeReadResult allowed(ChallengeModifierState state) {
            return new ChallengeReadResult(true, "", "", state);
        }

        static ChallengeReadResult rejected(String code, String detail, ChallengeModifierState state) {
            return new ChallengeReadResult(false, code, detail, state);
        }
    }

    public record MutationResult(
            boolean success,
            boolean committed,
            boolean voteStarted,
            boolean voteRecorded,
            String code,
            String detail,
            double previousValue,
            double currentValue,
            Optional<ChallengeVoteState> voteState) {
        static MutationResult rejected(String code, String detail) {
            return new MutationResult(false, false, false, false, code, detail, 0.0D, 0.0D, Optional.empty());
        }

        static MutationResult committed(double previousValue, double currentValue, Optional<ChallengeVoteState> voteState) {
            return new MutationResult(true, true, false, false, "", "", previousValue, currentValue, voteState);
        }

        static MutationResult voteStarted(double currentValue, Optional<ChallengeVoteState> voteState) {
            return new MutationResult(true, false, true, false, CODE_VOTE_REQUIRED, "vote started", currentValue, currentValue, voteState);
        }

        static MutationResult voteRecorded(
                double currentValue,
                Optional<ChallengeVoteState> voteState,
                String code,
                String detail) {
            return new MutationResult(true, false, false, true, code, detail, currentValue, currentValue, voteState);
        }
    }

    private record ResolvedChallenge(
            double value,
            String scopeUsed,
            String challengeSource) {
        static ResolvedChallenge disabled() {
            return new ResolvedChallenge(1.0D, "disabled", "<none>");
        }

        static ResolvedChallenge unresolved() {
            return new ResolvedChallenge(1.0D, "unresolved", "<none>");
        }
    }

    private record ScopeResolution(
            boolean valid,
            ChallengeScope scope,
            String resolvedScope,
            String code,
            String detail) {
        static ScopeResolution valid(ChallengeScope scope, String resolvedScope) {
            return new ScopeResolution(true, scope, resolvedScope, "", "");
        }

        static ScopeResolution invalid(String code, String detail, String resolvedScope) {
            return new ScopeResolution(false, null, resolvedScope, code, detail);
        }
    }

    private record PolicySnapshot(
            boolean globalEnabled,
            double globalDefaultValue,
            double globalMinValue,
            double globalMaxValue,
            String globalDiagnosticCode,
            String globalDiagnosticDetail,
            boolean challengeEnabled,
            ScopeMode challengeScopeMode,
            double challengeDefaultValue,
            double challengeMinValue,
            double challengeMaxValue,
            double challengeStep,
            int challengeCooldownMinutes,
            int challengeMaxChangesPerPlayer,
            int challengeMaxWorldChanges,
            boolean challengeAllowPlayerAdjustment,
            boolean challengeAllowRaise,
            boolean challengeAllowLower,
            boolean challengeRequireVoteInGlobal,
            double challengeVoteThreshold,
            int challengeVoteTimeoutSeconds,
            boolean challengeAdminOverride,
            boolean voteConfigValid,
            String challengeDiagnosticCode,
            String challengeDiagnosticDetail) {
    }

    private record DimensionBaselineCache(
            List<String> rawEntries,
            Map<ResourceLocation, Double> baselines) {
    }
}
