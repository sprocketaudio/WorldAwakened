package net.sprocketgames.worldawakened.ascension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;
import net.sprocketgames.worldawakened.ascension.component.WorldAwakenedAscensionComponentRegistry;
import net.sprocketgames.worldawakened.ascension.component.WorldAwakenedAscensionComponentType;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedCarrierService;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentSuppressionPolicy;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferMode;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardRepeatPolicy;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.ExecutionScope;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleEngine;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleEvaluation;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleMatchContext;
import net.sprocketgames.worldawakened.rules.runtime.WorldAwakenedRuleStateSnapshot;

public final class WorldAwakenedAscensionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<AscensionOfferDefinition> OFFER_PRIORITY = Comparator
            .comparingInt(AscensionOfferDefinition::uiPriority)
            .reversed()
            .thenComparing(offer -> offer.id().toString());

    private final WorldAwakenedDatapackService datapackService;
    private final WorldAwakenedStageService stageService;
    private final WorldAwakenedAscensionRewardEffects rewardEffects;

    public WorldAwakenedAscensionService(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService) {
        this.datapackService = datapackService;
        this.stageService = stageService;
        this.rewardEffects = new WorldAwakenedAscensionRewardEffects();
    }

    public int reconcileAllOnlinePlayers(MinecraftServer server, String reason) {
        if (server == null || !WorldAwakenedFeatureGates.ascensionEnabled()) {
            return 0;
        }
        int reconciled = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            reconcilePlayerRewards(player.serverLevel(), player, reason);
            reconciled++;
        }
        return reconciled;
    }

    public int grantEligibleOffersForStageUnlock(ServerLevel level, ServerPlayer player, ResourceLocation stageId) {
        if (!WorldAwakenedFeatureGates.ascensionEnabled()) {
            return 0;
        }

        if (player != null) {
            return grantEligibleOffersForPlayer(level, player, stageId.toString(), "stage_unlock:" + stageId);
        }

        int granted = 0;
        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
            granted += grantEligibleOffersForPlayer(level, online, stageId.toString(), "stage_unlock:" + stageId);
        }
        return granted;
    }

    public int grantEligibleOffersForPlayer(ServerLevel level, ServerPlayer player, String sourceProgressionKey, String grantSource) {
        if (!WorldAwakenedFeatureGates.ascensionEnabled() || player == null) {
            return 0;
        }

        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        List<AscensionOfferDefinition> offers = snapshot.data().ascensionOffers().values().stream()
                .filter(AscensionOfferDefinition::enabled)
                .sorted(OFFER_PRIORITY)
                .toList();
        int granted = 0;
        for (AscensionOfferDefinition offer : offers) {
            GrantResult result = grantOfferInternal(level, player, offer.id(), sourceProgressionKey, grantSource, false);
            if (result.status() == GrantStatus.GRANTED) {
                granted++;
            }
        }
        return granted;
    }

    public GrantResult grantOfferFromRule(ServerLevel level, ServerPlayer player, ResourceLocation offerId, String ruleId) {
        String sourceKey = ruleId == null || ruleId.isBlank() ? offerId.toString() : "rule:" + ruleId;
        String grantSource = ruleId == null || ruleId.isBlank() ? "rule" : "rule:" + ruleId;
        return grantOfferInternal(level, player, offerId, sourceKey, grantSource, false);
    }

    public GrantResult grantOfferFromCommand(ServerLevel level, ServerPlayer player, ResourceLocation offerId) {
        return grantOfferInternal(level, player, offerId, "command:" + offerId, "command:/wa ascension grant_offer", true);
    }

    public Optional<OpenOfferView> activeOfferView(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        Optional<WorldAwakenedAscensionOfferRuntime> active = activePendingOffer(level, player);
        if (active.isEmpty()) {
            return Optional.empty();
        }

        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        WorldAwakenedAscensionOfferRuntime runtime = active.get();
        AscensionOfferDefinition offer = snapshot.data().ascensionOffers().get(runtime.offerId());
        if (offer == null) {
            return Optional.empty();
        }

        List<RewardChoiceView> choices = new ArrayList<>();
        for (ResourceLocation rewardId : runtime.candidateRewards()) {
            AscensionRewardDefinition reward = snapshot.data().ascensionRewards().get(rewardId);
            if (reward == null) {
                continue;
            }
            choices.add(new RewardChoiceView(
                    reward.id(),
                    displayText(reward.displayName(), reward.id().toString()),
                    reward.description().map(value -> displayText(value, "")).orElse(""),
                    reward.rarity().orElse("")));
        }

        return Optional.of(new OpenOfferView(
                runtime.instanceId(),
                runtime.offerId(),
                displayText(offer.displayName(), offer.id().toString()),
                offer.description().map(value -> displayText(value, "")).orElse(""),
                List.copyOf(choices)));
    }

    public Optional<WorldAwakenedAscensionOfferRuntime> activePendingOffer(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        return activePendingRuntime(state);
    }

    public List<WorldAwakenedAscensionOfferRuntime> pendingOffers(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        return List.copyOf(state.pendingAscensionOfferInstances().values());
    }

    public List<WorldAwakenedAscensionOfferRuntime> resolvedOffers(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        return List.copyOf(state.resolvedAscensionOfferInstances().values());
    }

    public Optional<WorldAwakenedAscensionOfferRuntime> findPendingByOfferId(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation offerId) {
        if (player == null) {
            return Optional.empty();
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        return state.pendingAscensionOfferInstances().values().stream()
                .filter(runtime -> runtime.offerId().equals(offerId))
                .findFirst();
    }

    public RevokeSummary revokeReward(ServerLevel level, ServerPlayer player, ResourceLocation rewardId) {
        if (player == null || rewardId == null) {
            return new RevokeSummary(0, false);
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        WorldAwakenedAscensionStateEditor.RewardRevokeSummary summary = WorldAwakenedAscensionStateEditor.revokeReward(state, rewardId);
        if (!summary.changed()) {
            return new RevokeSummary(0, false);
        }
        reconcilePlayerRewards(level, player, "ascension_revoke");
        return new RevokeSummary(summary.reopenedOffers(), summary.removedLooseRewardOnly());
    }

    public boolean reopenOffer(ServerLevel level, ServerPlayer player, String instanceId) {
        if (player == null || instanceId == null || instanceId.isBlank()) {
            return false;
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        WorldAwakenedAscensionStateEditor.OfferMutationSummary result = WorldAwakenedAscensionStateEditor.reopenInstance(state, instanceId);
        if (!result.changed()) {
            return false;
        }
        reconcilePlayerRewards(level, player, "ascension_reopen");
        return true;
    }

    public boolean clearOfferInstance(ServerLevel level, ServerPlayer player, String instanceId) {
        if (player == null || instanceId == null || instanceId.isBlank()) {
            return false;
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        WorldAwakenedAscensionStateEditor.OfferMutationSummary result = WorldAwakenedAscensionStateEditor.clearInstance(state, instanceId);
        if (!result.changed()) {
            return false;
        }
        reconcilePlayerRewards(level, player, "ascension_clear_instance");
        return true;
    }

    public ResetSummary resetAll(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            return new ResetSummary(0, 0, 0, 0);
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        WorldAwakenedAscensionStateEditor.ResetSummary summary = WorldAwakenedAscensionStateEditor.resetAll(state);
        if (summary.totalCleared() > 0) {
            reconcilePlayerRewards(level, player, "ascension_reset_all");
        }
        return new ResetSummary(
                summary.pendingOffers(),
                summary.resolvedOffers(),
                summary.chosenRewards(),
                summary.forfeitedRewards());
    }

    public SuppressionMutationResult suppressReward(ServerLevel level, ServerPlayer player, ResourceLocation rewardId) {
        if (player == null || rewardId == null) {
            return new SuppressionMutationResult(false, "invalid_request", WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_TARGET_UNKNOWN, rewardId, Set.of());
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        if (!state.chosenAscensionRewards().contains(rewardId)) {
            return new SuppressionMutationResult(
                    false,
                    "reward_not_owned",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_TARGET_UNKNOWN,
                    rewardId,
                    Set.of());
        }
        if (!state.suppressedAscensionRewards().add(rewardId)) {
            return new SuppressionMutationResult(
                    false,
                    "reward_already_suppressed",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_APPLIED,
                    rewardId,
                    Set.of());
        }
        long now = System.currentTimeMillis();
        state.ascensionRewardSuppressionTimestamps().put(rewardId, now);
        state.markDirty();
        reconcilePlayerRewards(level, player, "ascension_suppress_reward");
        return new SuppressionMutationResult(
                true,
                "suppression_applied",
                WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_APPLIED,
                rewardId,
                Set.of());
    }

    public SuppressionMutationResult unsuppressReward(ServerLevel level, ServerPlayer player, ResourceLocation rewardId) {
        if (player == null || rewardId == null) {
            return new SuppressionMutationResult(false, "invalid_request", WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_TARGET_UNKNOWN, rewardId, Set.of());
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        if (!state.chosenAscensionRewards().contains(rewardId)) {
            return new SuppressionMutationResult(
                    false,
                    "reward_not_owned",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_TARGET_UNKNOWN,
                    rewardId,
                    Set.of());
        }
        if (!state.suppressedAscensionRewards().remove(rewardId)) {
            return new SuppressionMutationResult(
                    false,
                    "reward_not_suppressed",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_REMOVED,
                    rewardId,
                    Set.of());
        }
        state.ascensionRewardSuppressionTimestamps().remove(rewardId);
        state.markDirty();
        reconcilePlayerRewards(level, player, "ascension_unsuppress_reward");
        return new SuppressionMutationResult(
                true,
                "suppression_removed",
                WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_REMOVED,
                rewardId,
                Set.of());
    }

    public SuppressionMutationResult suppressComponent(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation rewardId,
            String requestedComponentKey) {
        return mutateComponentSuppression(level, player, rewardId, requestedComponentKey, true);
    }

    public SuppressionMutationResult unsuppressComponent(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation rewardId,
            String requestedComponentKey) {
        return mutateComponentSuppression(level, player, rewardId, requestedComponentKey, false);
    }

    public RewardSuppressionView inspectRewardSuppression(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation rewardId) {
        if (player == null || rewardId == null) {
            return RewardSuppressionView.unknown(rewardId);
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        return resolveRewardSuppression(snapshot, state, rewardId);
    }

    public List<String> suppressibleComponentKeys(ServerLevel level, ServerPlayer player, ResourceLocation rewardId) {
        if (player == null || rewardId == null) {
            return List.of();
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        if (!state.chosenAscensionRewards().contains(rewardId)) {
            return List.of();
        }
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        AscensionRewardDefinition rewardDefinition = snapshot.data().ascensionRewards().get(rewardId);
        if (rewardDefinition == null) {
            return List.of();
        }
        return buildComponentSuppressionEntries(rewardDefinition).stream()
                .filter(entry -> entry.component().enabled())
                .filter(entry -> entry.componentLevelSupported())
                .map(ComponentSuppressionEntry::componentKey)
                .toList();
    }

    public List<String> suppressedComponentKeys(ServerLevel level, ServerPlayer player, ResourceLocation rewardId) {
        if (player == null || rewardId == null) {
            return List.of();
        }
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        return state.suppressedAscensionComponentsByReward().getOrDefault(rewardId, Set.of()).stream()
                .sorted()
                .toList();
    }

    public void reconcilePlayerRewards(ServerLevel level, ServerPlayer player, String reason) {
        if (player == null || !WorldAwakenedFeatureGates.ascensionEnabled()) {
            return;
        }
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        rewardEffects.clear(player);
        for (ResourceLocation rewardId : state.chosenAscensionRewards()) {
            AscensionRewardDefinition reward = snapshot.data().ascensionRewards().get(rewardId);
            if (reward == null) {
                if (state.suppressedAscensionRewards().contains(rewardId)
                        || state.suppressedAscensionComponentsByReward().containsKey(rewardId)) {
                    WorldAwakenedLog.warn(
                            LOGGER,
                            WorldAwakenedLogCategory.PIPELINE,
                            "Suppressed ascension reference missing definition: code={} player={} reward={}",
                            WorldAwakenedDiagnosticCodes.ASC_SUPPRESSED_DEFINITION_MISSING,
                            player.getGameProfile().getName(),
                            rewardId);
                }
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.DATA_LOAD,
                        "Chosen ascension reward definition missing: player={} reward={}",
                        player.getGameProfile().getName(),
                        rewardId);
                continue;
            }
            RewardSuppressionView suppressionView = resolveRewardSuppression(snapshot, state, rewardId);
            if (!suppressionView.missingSuppressedComponentKeys().isEmpty()) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Suppressed ascension component key missing from definition: code={} player={} reward={} component_keys={}",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSED_DEFINITION_MISSING,
                        player.getGameProfile().getName(),
                        rewardId,
                        suppressionView.missingSuppressedComponentKeys());
            }
            if (suppressionView.liveState() == RewardLiveState.SUPPRESSED) {
                continue;
            }
            Set<String> effectiveSuppressedComponentKeys = suppressionView.effectiveSuppressedComponentKeys();
            if (suppressionView.liveState() == RewardLiveState.SUPPRESSION_REJECTED_INVALID_GROUP_STATE
                    || suppressionView.liveState() == RewardLiveState.SUPPRESSION_REJECTED_NOT_INDEPENDENTLY_SUPPORTED) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Skipped ascension reward due invalid suppression state: code={} player={} reward={} detail={}",
                        suppressionView.rejectionCode(),
                        player.getGameProfile().getName(),
                        rewardId,
                        suppressionView.rejectionDetail());
                effectiveSuppressedComponentKeys = Set.of();
            }
            rewardEffects.apply(player, reward, effectiveSuppressedComponentKeys);
        }
        WorldAwakenedOwnedCarrierService.syncOwnedCarriers(player);
        WorldAwakenedLog.debug(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Reconciled ascension rewards for {} reason={} chosen={} suppressed_rewards={}",
                player.getGameProfile().getName(),
                reason,
                state.chosenAscensionRewards().size(),
                state.suppressedAscensionRewards().size());
    }

    public ChooseResult chooseReward(ServerLevel level, ServerPlayer player, String instanceId, ResourceLocation rewardId, String source) {
        if (player == null || instanceId == null || instanceId.isBlank() || rewardId == null) {
            return new ChooseResult(ChooseStatus.REJECTED, "invalid_request");
        }
        if (!WorldAwakenedFeatureGates.ascensionEnabled()) {
            return new ChooseResult(ChooseStatus.REJECTED, "ascension_disabled");
        }

        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        WorldAwakenedAscensionOfferRuntime runtime = state.pendingAscensionOfferInstances().get(instanceId);
        if (runtime == null) {
            return new ChooseResult(ChooseStatus.REJECTED, "pending_instance_missing_or_stale");
        }
        if (!runtime.candidateRewards().contains(rewardId)) {
            return new ChooseResult(ChooseStatus.REJECTED, "reward_not_in_offer");
        }

        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        AscensionOfferDefinition offer = snapshot.data().ascensionOffers().get(runtime.offerId());
        AscensionRewardDefinition reward = snapshot.data().ascensionRewards().get(rewardId);
        if (offer == null || reward == null || !reward.enabled()) {
            return new ChooseResult(ChooseStatus.REJECTED, "offer_or_reward_missing");
        }
        if (!rewardStillEligible(level, player, offer, reward, state, snapshot.data().ascensionRewards())) {
            return new ChooseResult(ChooseStatus.REJECTED, "reward_ineligible");
        }
        snapshot = datapackService.pinSnapshot(snapshot, "ascension.choose_reward");

        Set<ResourceLocation> forfeitedRewards = runtime.candidateRewards().stream()
                .filter(candidate -> !candidate.equals(rewardId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long now = System.currentTimeMillis();

        state.pendingAscensionOfferInstances().remove(instanceId);
        state.resolvedAscensionOfferInstances().put(instanceId, runtime.resolve(rewardId, now));
        state.chosenAscensionRewards().add(rewardId);
        state.forfeitedAscensionRewards().addAll(forfeitedRewards);
        state.forfeitedAscensionRewardsByOffer().put(instanceId, forfeitedRewards);
        state.ascensionRewardUnlockTimestamps().putIfAbsent(rewardId, now);
        state.ascensionRewardSources().putIfAbsent(rewardId, runtime.sourceKey());
        refreshSummarySets(state);
        state.markDirty();

        reconcilePlayerRewards(level, player, "ascension_choose");
        player.sendSystemMessage(Component.literal("You chose ")
                .append(displayComponent(reward.displayName(), player.serverLevel(), reward.id().toString()).withStyle(ChatFormatting.AQUA)));

        Optional<WorldAwakenedAscensionOfferRuntime> nextPending = activePendingRuntime(state);
        if (nextPending.isPresent() && WorldAwakenedCommonConfig.REMIND_PENDING_OFFERS.get()) {
            notifyOfferAvailable(player, nextPending.get().offerId(), true, snapshot);
        }

        WorldAwakenedLog.debug(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Accepted ascension choice player={} instance={} reward={} source={}",
                player.getGameProfile().getName(),
                instanceId,
                rewardId,
                source);
        return new ChooseResult(ChooseStatus.ACCEPTED, "selected");
    }

    private SuppressionMutationResult mutateComponentSuppression(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation rewardId,
            String requestedComponentKey,
            boolean suppress) {
        if (player == null || rewardId == null || requestedComponentKey == null || requestedComponentKey.isBlank()) {
            return new SuppressionMutationResult(
                    false,
                    "invalid_request",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_TARGET_UNKNOWN,
                    rewardId,
                    Set.of());
        }

        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        if (!state.chosenAscensionRewards().contains(rewardId)) {
            return new SuppressionMutationResult(
                    false,
                    "reward_not_owned",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_TARGET_UNKNOWN,
                    rewardId,
                    Set.of());
        }

        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        AscensionRewardDefinition rewardDefinition = snapshot.data().ascensionRewards().get(rewardId);
        if (rewardDefinition == null) {
            return new SuppressionMutationResult(
                    false,
                    "suppressed_definition_missing",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSED_DEFINITION_MISSING,
                    rewardId,
                    Set.of());
        }

        ComponentSuppressionResolution resolution = resolveComponentSuppressionTargets(rewardDefinition, requestedComponentKey);
        if (!resolution.accepted()) {
            return new SuppressionMutationResult(
                    false,
                    resolution.detail(),
                    resolution.code(),
                    rewardId,
                    Set.of());
        }

        Set<String> requestedKeys = resolution.componentKeys();
        long now = System.currentTimeMillis();
        boolean changed;
        if (suppress) {
            Set<String> current = state.suppressedAscensionComponentsByReward()
                    .computeIfAbsent(rewardId, ignored -> new LinkedHashSet<>());
            changed = current.addAll(requestedKeys);
            if (!changed) {
                return new SuppressionMutationResult(
                        false,
                        "component_already_suppressed",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_APPLIED,
                        rewardId,
                        requestedKeys);
            }
            requestedKeys.forEach(componentKey -> state.ascensionComponentSuppressionTimestamps()
                    .put(componentTimestampTarget(rewardId, componentKey), now));
        } else {
            Set<String> current = state.suppressedAscensionComponentsByReward().get(rewardId);
            if (current == null || current.isEmpty()) {
                return new SuppressionMutationResult(
                        false,
                        "component_not_suppressed",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_REMOVED,
                        rewardId,
                        requestedKeys);
            }
            changed = current.removeAll(requestedKeys);
            if (!changed) {
                return new SuppressionMutationResult(
                        false,
                        "component_not_suppressed",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_REMOVED,
                        rewardId,
                        requestedKeys);
            }
            requestedKeys.forEach(componentKey -> state.ascensionComponentSuppressionTimestamps()
                    .remove(componentTimestampTarget(rewardId, componentKey)));
            if (current.isEmpty()) {
                state.suppressedAscensionComponentsByReward().remove(rewardId);
            }
        }

        state.markDirty();
        reconcilePlayerRewards(level, player, suppress ? "ascension_suppress_component" : "ascension_unsuppress_component");
        return new SuppressionMutationResult(
                true,
                suppress ? "suppression_applied" : "suppression_removed",
                suppress ? WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_APPLIED : WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_REMOVED,
                rewardId,
                requestedKeys);
    }

    private static ComponentSuppressionResolution resolveComponentSuppressionTargets(
            AscensionRewardDefinition rewardDefinition,
            String requestedComponentKey) {
        List<ComponentSuppressionEntry> entries = buildComponentSuppressionEntries(rewardDefinition);
        Optional<ComponentSuppressionEntry> exact = entries.stream()
                .filter(entry -> entry.componentKey().equals(requestedComponentKey))
                .findFirst();
        Optional<ComponentSuppressionEntry> byIndex = Optional.empty();
        if (exact.isEmpty()) {
            OptionalInt requestedIndex = WorldAwakenedAscensionComponentKeys.parseIndex(requestedComponentKey);
            if (requestedIndex.isPresent()) {
                byIndex = entries.stream()
                        .filter(entry -> entry.index() == requestedIndex.getAsInt())
                        .findFirst();
            }
        }
        Optional<ComponentSuppressionEntry> resolved = exact.isPresent() ? exact : byIndex;
        ComponentSuppressionEntry target = resolved.orElse(null);
        if (target == null || !target.component().enabled()) {
            return ComponentSuppressionResolution.rejected(
                    "component_target_unknown",
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_TARGET_UNKNOWN);
        }
        if (!target.componentLevelSupported()) {
            return ComponentSuppressionResolution.rejected(
                    "component_not_suppressible",
                    WorldAwakenedDiagnosticCodes.ASC_COMPONENT_NOT_SUPPRESSIBLE);
        }
        if (target.effectivePolicy() == AscensionComponentSuppressionPolicy.GROUPED) {
            if (target.normalizedGroup().isEmpty()) {
                return ComponentSuppressionResolution.rejected(
                        "suppression_group_required",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_GROUP_REQUIRED);
            }
            String group = target.normalizedGroup().get();
            List<ComponentSuppressionEntry> groupedEntries = entries.stream()
                    .filter(entry -> entry.component().enabled())
                    .filter(entry -> entry.normalizedGroup().isPresent() && entry.normalizedGroup().get().equals(group))
                    .toList();
            if (groupedEntries.isEmpty()) {
                return ComponentSuppressionResolution.rejected(
                        "suppression_invalid_partial",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_INVALID_PARTIAL);
            }
            if (groupedEntries.stream().anyMatch(entry -> entry.effectivePolicy() != AscensionComponentSuppressionPolicy.GROUPED)) {
                return ComponentSuppressionResolution.rejected(
                        "suppression_invalid_partial",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_INVALID_PARTIAL);
            }
            if (groupedEntries.stream().anyMatch(entry -> !entry.componentLevelSupported())) {
                return ComponentSuppressionResolution.rejected(
                        "component_not_suppressible",
                        WorldAwakenedDiagnosticCodes.ASC_COMPONENT_NOT_SUPPRESSIBLE);
            }
            Set<String> componentKeys = groupedEntries.stream()
                    .map(ComponentSuppressionEntry::componentKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return ComponentSuppressionResolution.accepted(componentKeys, true);
        }
        return ComponentSuppressionResolution.accepted(Set.of(target.componentKey()), false);
    }

    private RewardSuppressionView resolveRewardSuppression(
            WorldAwakenedDatapackSnapshot snapshot,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            ResourceLocation rewardId) {
        boolean owned = state.chosenAscensionRewards().contains(rewardId);
        Set<String> configuredSuppressedComponentKeys = new LinkedHashSet<>(
                state.suppressedAscensionComponentsByReward().getOrDefault(rewardId, Set.of()));
        AscensionRewardDefinition rewardDefinition = snapshot.data().ascensionRewards().get(rewardId);
        if (rewardDefinition == null) {
            return new RewardSuppressionView(
                    rewardId,
                    owned,
                    state.suppressedAscensionRewards().contains(rewardId),
                    RewardLiveState.MISSING_DEFINITION,
                    Set.copyOf(configuredSuppressedComponentKeys),
                    Set.of(),
                    Set.copyOf(configuredSuppressedComponentKeys),
                    WorldAwakenedDiagnosticCodes.ASC_SUPPRESSED_DEFINITION_MISSING,
                    "suppressed_definition_missing",
                    false);
        }

        if (state.suppressedAscensionRewards().contains(rewardId)) {
            return new RewardSuppressionView(
                    rewardId,
                    owned,
                    true,
                    RewardLiveState.SUPPRESSED,
                    Set.copyOf(configuredSuppressedComponentKeys),
                    Set.of(),
                    Set.of(),
                    "",
                    "",
                    false);
        }

        List<ComponentSuppressionEntry> entries = buildComponentSuppressionEntries(rewardDefinition).stream()
                .filter(entry -> entry.component().enabled())
                .toList();
        if (configuredSuppressedComponentKeys.isEmpty()) {
            return new RewardSuppressionView(
                    rewardId,
                    owned,
                    false,
                    RewardLiveState.ACTIVE,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    "",
                    "",
                    false);
        }

        Set<String> knownComponentKeys = entries.stream()
                .map(ComponentSuppressionEntry::componentKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> missingKeys = configuredSuppressedComponentKeys.stream()
                .filter(key -> !knownComponentKeys.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> effectiveKeys = configuredSuppressedComponentKeys.stream()
                .filter(knownComponentKeys::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (ComponentSuppressionEntry entry : entries) {
            if (!effectiveKeys.contains(entry.componentKey())) {
                continue;
            }
            if (!entry.componentLevelSupported()) {
                return new RewardSuppressionView(
                        rewardId,
                        owned,
                        false,
                        RewardLiveState.SUPPRESSION_REJECTED_NOT_INDEPENDENTLY_SUPPORTED,
                        Set.copyOf(configuredSuppressedComponentKeys),
                        Set.of(),
                        Set.copyOf(missingKeys),
                        WorldAwakenedDiagnosticCodes.ASC_COMPONENT_NOT_SUPPRESSIBLE,
                        "component_not_suppressible",
                        false);
            }
        }

        Map<String, List<ComponentSuppressionEntry>> groupedEntries = new LinkedHashMap<>();
        for (ComponentSuppressionEntry entry : entries) {
            if (entry.effectivePolicy() != AscensionComponentSuppressionPolicy.GROUPED) {
                continue;
            }
            if (entry.normalizedGroup().isEmpty()) {
                return new RewardSuppressionView(
                        rewardId,
                        owned,
                        false,
                        RewardLiveState.SUPPRESSION_REJECTED_INVALID_GROUP_STATE,
                        Set.copyOf(configuredSuppressedComponentKeys),
                        Set.of(),
                        Set.copyOf(missingKeys),
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_GROUP_REQUIRED,
                        "suppression_group_required",
                        false);
            }
            groupedEntries.computeIfAbsent(entry.normalizedGroup().get(), ignored -> new ArrayList<>()).add(entry);
        }

        boolean groupedActive = false;
        Set<String> groupedSuppressedKeys = new LinkedHashSet<>();
        for (Map.Entry<String, List<ComponentSuppressionEntry>> entry : groupedEntries.entrySet()) {
            List<ComponentSuppressionEntry> group = entry.getValue();
            long suppressedCount = group.stream()
                    .filter(component -> effectiveKeys.contains(component.componentKey()))
                    .count();
            if (suppressedCount == 0) {
                continue;
            }
            if (suppressedCount != group.size()) {
                return new RewardSuppressionView(
                        rewardId,
                        owned,
                        false,
                        RewardLiveState.SUPPRESSION_REJECTED_INVALID_GROUP_STATE,
                        Set.copyOf(configuredSuppressedComponentKeys),
                        Set.of(),
                        Set.copyOf(missingKeys),
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_INVALID_PARTIAL,
                        "suppression_invalid_partial",
                        false);
            }
            groupedActive = true;
            groupedSuppressedKeys.addAll(group.stream().map(ComponentSuppressionEntry::componentKey).toList());
        }

        RewardLiveState liveState;
        if (effectiveKeys.isEmpty()) {
            liveState = RewardLiveState.ACTIVE;
        } else if (groupedActive && groupedSuppressedKeys.containsAll(effectiveKeys)) {
            liveState = RewardLiveState.SUPPRESSED_GROUP;
        } else {
            liveState = RewardLiveState.PARTIALLY_SUPPRESSED;
        }
        return new RewardSuppressionView(
                rewardId,
                owned,
                false,
                liveState,
                Set.copyOf(configuredSuppressedComponentKeys),
                Set.copyOf(effectiveKeys),
                Set.copyOf(missingKeys),
                "",
                "",
                groupedActive);
    }

    private static List<ComponentSuppressionEntry> buildComponentSuppressionEntries(AscensionRewardDefinition rewardDefinition) {
        List<ComponentSuppressionEntry> entries = new ArrayList<>();
        for (int index = 0; index < rewardDefinition.components().size(); index++) {
            AscensionComponentDefinition component = rewardDefinition.components().get(index);
            Optional<WorldAwakenedAscensionComponentType> type = WorldAwakenedAscensionComponentRegistry.lookup(component.type());
            boolean typeAllowsComponentSuppression = type.map(WorldAwakenedAscensionComponentType::suppressibleIndividually).orElse(false);
            AscensionComponentSuppressionPolicy effectivePolicy = component.effectiveSuppressionPolicy();
            boolean componentMetadataAllowsComponentSuppression = component.suppressibleIndividually()
                    && effectivePolicy != AscensionComponentSuppressionPolicy.REWARD_ONLY;
            boolean componentLevelSupported = componentMetadataAllowsComponentSuppression
                    && typeAllowsComponentSuppression;
            entries.add(new ComponentSuppressionEntry(
                    index,
                    component,
                    WorldAwakenedAscensionComponentKeys.componentKey(index, component),
                    effectivePolicy,
                    component.normalizedSuppressionGroup(),
                    componentLevelSupported));
        }
        return List.copyOf(entries);
    }

    private static String componentTimestampTarget(ResourceLocation rewardId, String componentKey) {
        return "component|" + rewardId + "|" + componentKey;
    }

    private GrantResult grantOfferInternal(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation offerId,
            String sourceProgressionKey,
            String grantSource,
            boolean bypassEligibility) {
        if (player == null) {
            return new GrantResult(offerId, "", GrantStatus.REJECTED, "missing_player");
        }
        if (!WorldAwakenedFeatureGates.ascensionEnabled()) {
            return new GrantResult(offerId, "", GrantStatus.REJECTED, "ascension_disabled");
        }

        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        AscensionOfferDefinition offer = snapshot.data().ascensionOffers().get(offerId);
        if (offer == null || !offer.enabled()) {
            return new GrantResult(offerId, "", GrantStatus.REJECTED, "unknown_or_disabled_offer");
        }

        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = playerState(level, player);
        String normalizedSource = normalizeSourceKey(offerId, sourceProgressionKey);
        Optional<WorldAwakenedAscensionOfferRuntime> existingRuntime = findRuntimeByOfferAndSource(state, offerId, normalizedSource);
        if (existingRuntime.isPresent()) {
            return new GrantResult(offerId, existingRuntime.get().instanceId(), GrantStatus.ALREADY_PRESENT, "idempotent_instance");
        }

        if (!bypassEligibility && !offerEligibleForGrant(level, player, offer, state)) {
            return new GrantResult(offerId, "", GrantStatus.REJECTED, "offer_conditions_not_met");
        }

        CandidateSelection selection = selectCandidates(level, player, offer, state, snapshot.data().ascensionRewards());
        if (selection.candidates().isEmpty()) {
            return new GrantResult(offerId, "", GrantStatus.REJECTED, selection.detail());
        }
        snapshot = datapackService.pinSnapshot(snapshot, "ascension.grant_offer_internal");

        boolean alreadyPending = !state.pendingAscensionOfferInstances().isEmpty();
        String instanceId = nextRuntimeInstanceId(state);
        WorldAwakenedAscensionOfferRuntime runtime = WorldAwakenedAscensionOfferRuntime.pending(
                instanceId,
                offerId,
                normalizedSource,
                System.currentTimeMillis(),
                selection.candidates());
        state.pendingAscensionOfferInstances().put(instanceId, runtime);
        refreshSummarySets(state);
        state.markDirty();

        if (!alreadyPending || WorldAwakenedCommonConfig.REMIND_PENDING_OFFERS.get()) {
            notifyOfferAvailable(player, offerId, alreadyPending, snapshot);
        }

        WorldAwakenedLog.debug(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Granted ascension offer {} instance={} to {} source={} reason={}",
                offerId,
                instanceId,
                player.getGameProfile().getName(),
                grantSource,
                selection.detail());
        return new GrantResult(offerId, instanceId, GrantStatus.GRANTED, selection.detail());
    }

    private void notifyOfferAvailable(
            ServerPlayer player,
            ResourceLocation offerId,
            boolean queued,
            WorldAwakenedDatapackSnapshot pinnedSnapshot) {
        if (!WorldAwakenedCommonConfig.SHOW_ASCENSION_NOTIFICATIONS.get()) {
            return;
        }
        WorldAwakenedDatapackSnapshot snapshot = datapackService.pinSnapshot(pinnedSnapshot, "ascension.notify_offer");
        AscensionOfferDefinition offer = snapshot.data().ascensionOffers().get(offerId);
        MutableComponent offerTitle = offer == null
                ? Component.literal(offerId.toString())
                : displayComponent(offer.displayName(), player.serverLevel(), offer.id().toString());
        MutableComponent message = Component.literal(queued ? "Queued ascension offer available: " : "Ascension offer available: ")
                .append(offerTitle.withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" "))
                .append(Component.literal("[Open]").withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open this offer")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wa ascension open"))));
        player.sendSystemMessage(message);
    }

    private CandidateSelection selectCandidates(
            ServerLevel level,
            ServerPlayer player,
            AscensionOfferDefinition offer,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            Map<ResourceLocation, AscensionRewardDefinition> rewardDefinitions) {
        List<AscensionRewardDefinition> pool = resolveOfferPool(offer, rewardDefinitions);
        if (pool.isEmpty()) {
            return new CandidateSelection(List.of(), "no_reward_pool");
        }

        List<AscensionRewardDefinition> eligible = new ArrayList<>();
        for (AscensionRewardDefinition reward : pool) {
            if (rewardStillEligible(level, player, offer, reward, state, rewardDefinitions)) {
                eligible.add(reward);
            }
        }
        if (eligible.isEmpty()) {
            return new CandidateSelection(List.of(), "no_eligible_rewards");
        }

        int choiceCount = Math.max(1, offer.choiceCount());
        List<ResourceLocation> selected = switch (offer.offerMode()) {
            case EXPLICIT_LIST -> eligible.stream()
                    .limit(choiceCount)
                    .map(AscensionRewardDefinition::id)
                    .toList();
            case WEIGHTED_FROM_POOL, WEIGHTED_FROM_TAG_GROUP -> weightedSample(
                    eligible,
                    choiceCount,
                    player.getUUID().toString() + "|" + offer.id());
        };
        return new CandidateSelection(selected, "selected_count=" + selected.size());
    }

    private boolean offerEligibleForGrant(
            ServerLevel level,
            ServerPlayer player,
            AscensionOfferDefinition offer,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        if (!offer.triggerConditions().isEmpty()
                && !evaluateConditions(level, player, offer.id(), "offer_trigger", offer.triggerConditions(), state, true)) {
            return false;
        }
        return evaluateStageFilters(level, player, offer.stageFilters());
    }

    private boolean rewardStillEligible(
            ServerLevel level,
            ServerPlayer player,
            AscensionOfferDefinition offer,
            AscensionRewardDefinition reward,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            Map<ResourceLocation, AscensionRewardDefinition> rewardDefinitions) {
        if (!reward.enabled()) {
            return false;
        }
        if (rewardBlockedByOfferReusePolicy(offer, reward.id(), state)) {
            return false;
        }

        if (reward.uniqueGroup().isPresent()) {
            String uniqueGroup = reward.uniqueGroup().get();
            for (ResourceLocation chosenRewardId : state.chosenAscensionRewards()) {
                AscensionRewardDefinition chosen = rewardDefinitions.get(chosenRewardId);
                if (chosen != null && chosen.uniqueGroup().isPresent() && uniqueGroup.equals(chosen.uniqueGroup().get())) {
                    return false;
                }
            }
        }

        if (!reward.exclusionTags().isEmpty()) {
            Set<String> chosenTags = state.chosenAscensionRewards().stream()
                    .map(rewardDefinitions::get)
                    .filter(java.util.Objects::nonNull)
                    .flatMap(definition -> definition.tags().stream())
                    .map(tag -> tag.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            for (String exclusion : reward.exclusionTags()) {
                if (chosenTags.contains(exclusion.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        }

        if (!reward.requiresConditions().isEmpty()
                && !evaluateConditions(level, player, reward.id(), "reward_requires", reward.requiresConditions(), state, false)) {
            return false;
        }
        if (!reward.forbiddenConditions().isEmpty()
                && evaluateConditions(level, player, reward.id(), "reward_forbidden", reward.forbiddenConditions(), state, false)) {
            return false;
        }
        return true;
    }

    static boolean rewardBlockedByOfferReusePolicy(
            AscensionOfferDefinition offer,
            ResourceLocation rewardId,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        if (offer.rewardRepeatPolicy() != AscensionRewardRepeatPolicy.ALLOW_ALL
                && state.chosenAscensionRewards().contains(rewardId)) {
            return true;
        }
        if (offer.rewardRepeatPolicy() == AscensionRewardRepeatPolicy.BLOCK_ALL
                && state.forfeitedAscensionRewards().contains(rewardId)) {
            return true;
        }
        return false;
    }

    private boolean evaluateConditions(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation objectId,
            String scope,
            List<JsonElement> conditions,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            boolean applyChance) {
        RuleDefinition synthetic = new RuleDefinition(
                1,
                ResourceLocation.fromNamespaceAndPath(WorldAwakenedConstants.MOD_ID, "ascension/" + scope + "/" + objectId.getPath()),
                true,
                0,
                conditions,
                List.of(),
                1.0D,
                1.0D,
                Optional.empty(),
                ExecutionScope.PLAYER,
                List.of("ascension"));
        List<WorldAwakenedRuleEngine.CompiledRule> compiled = WorldAwakenedRuleEngine.compile(List.of(synthetic));
        if (compiled.isEmpty()) {
            return true;
        }

        WorldAwakenedRuleMatchContext context = buildRuleMatchContext(level, player, state);
        WorldAwakenedRuleEvaluation evaluation = WorldAwakenedRuleEngine.evaluate(
                compiled,
                stageService.stageRegistry(),
                context,
                applyChance);
        return evaluation.matchedCount() > 0;
    }

    private WorldAwakenedRuleMatchContext buildRuleMatchContext(
            ServerLevel level,
            ServerPlayer player,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        Set<ResourceLocation> pendingOfferDefinitions = state.pendingAscensionOfferInstances().values().stream()
                .map(WorldAwakenedAscensionOfferRuntime::offerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Optional<ResourceLocation> biomeId = Optional.empty();
        OptionalDouble localDifficulty = OptionalDouble.empty();
        OptionalDouble distance = OptionalDouble.empty();
        if (player != null) {
            BlockPos pos = player.blockPosition();
            Optional<ResourceKey<Biome>> key = level.getBiome(pos).unwrapKey();
            biomeId = key.map(ResourceKey::location);
            localDifficulty = OptionalDouble.of(level.getCurrentDifficultyAt(pos).getEffectiveDifficulty());
            distance = OptionalDouble.of(player.position().distanceTo(Vec3.atCenterOf(level.getSharedSpawnPos())));
        }

        Set<String> loadedMods = ModList.get().getMods().stream()
                .map(mod -> mod.getModId().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        toggles.put("general.enable_mod", WorldAwakenedCommonConfig.ENABLE_MOD.get());
        toggles.put("general.debug_logging", WorldAwakenedCommonConfig.DEBUG_LOGGING.get());
        toggles.put("general.enable_debug_commands", WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get());
        toggles.put("general.validation_logging", WorldAwakenedCommonConfig.VALIDATION_LOGGING.get());
        toggles.put("compat.apotheosis.enabled", WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get());

        Set<ResourceLocation> worldStages = stageService.getUnlockedStages(level);
        Set<ResourceLocation> playerStages = stageService.getUnlockedStages(level, player);

        return new WorldAwakenedRuleMatchContext(
                "ascension",
                Optional.empty(),
                level.dimension().location(),
                biomeId,
                Optional.empty(),
                Set.of(),
                Optional.of(player.getStringUUID()),
                Optional.empty(),
                true,
                false,
                false,
                false,
                OptionalLong.of(Math.max(0L, level.getDayTime() / 24000L)),
                distance,
                level.getServer().getPlayerCount(),
                localDifficulty,
                System.currentTimeMillis(),
                level.getGameTime(),
                worldStages,
                playerStages,
                WorldAwakenedRuleStateSnapshot.empty(),
                WorldAwakenedRuleStateSnapshot.empty(),
                Set.copyOf(state.chosenAscensionRewards()),
                Set.copyOf(pendingOfferDefinitions),
                Set.copyOf(loadedMods),
                Map.copyOf(toggles),
                false,
                Optional.empty());
    }

    private boolean evaluateStageFilters(ServerLevel level, ServerPlayer player, Optional<JsonElement> stageFilters) {
        if (stageFilters.isEmpty() || !stageFilters.get().isJsonObject()) {
            return true;
        }
        JsonObject filter = stageFilters.get().getAsJsonObject();
        Set<ResourceLocation> playerStages = stageService.getUnlockedStages(level, player);
        Set<ResourceLocation> worldStages = stageService.getUnlockedStages(level);
        Set<ResourceLocation> active = new LinkedHashSet<>(worldStages);
        active.addAll(playerStages);
        WorldAwakenedStageRegistry registry = stageService.stageRegistry();

        if (filter.has("all_of") && filter.get("all_of").isJsonArray()) {
            for (JsonElement entry : filter.getAsJsonArray("all_of")) {
                ResourceLocation stageId = parseResourceLocation(entry);
                if (stageId == null || !hasStage(active, registry, stageId)) {
                    return false;
                }
            }
        }
        if (filter.has("any_of") && filter.get("any_of").isJsonArray()) {
            boolean any = false;
            for (JsonElement entry : filter.getAsJsonArray("any_of")) {
                ResourceLocation stageId = parseResourceLocation(entry);
                if (stageId != null && hasStage(active, registry, stageId)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        if (filter.has("none_of") && filter.get("none_of").isJsonArray()) {
            for (JsonElement entry : filter.getAsJsonArray("none_of")) {
                ResourceLocation stageId = parseResourceLocation(entry);
                if (stageId != null && hasStage(active, registry, stageId)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<AscensionRewardDefinition> resolveOfferPool(
            AscensionOfferDefinition offer,
            Map<ResourceLocation, AscensionRewardDefinition> rewardDefinitions) {
        LinkedHashMap<ResourceLocation, AscensionRewardDefinition> pool = new LinkedHashMap<>();
        for (ResourceLocation rewardId : offer.candidateRewards()) {
            AscensionRewardDefinition reward = rewardDefinitions.get(rewardId);
            if (reward != null && reward.enabled()) {
                pool.put(reward.id(), reward);
            }
        }
        if (!offer.candidateRewardTags().isEmpty()) {
            Set<String> tags = offer.candidateRewardTags().stream()
                    .map(tag -> tag.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (AscensionRewardDefinition reward : rewardDefinitions.values()) {
                if (!reward.enabled()) {
                    continue;
                }
                boolean hasTag = reward.tags().stream()
                        .map(tag -> tag.toLowerCase(Locale.ROOT))
                        .anyMatch(tags::contains);
                if (hasTag) {
                    pool.put(reward.id(), reward);
                }
            }
        }
        return List.copyOf(pool.values());
    }

    private static List<ResourceLocation> weightedSample(List<AscensionRewardDefinition> rewards, int count, String seedKey) {
        if (rewards.isEmpty()) {
            return List.of();
        }
        List<AscensionRewardDefinition> pool = new ArrayList<>(rewards);
        List<ResourceLocation> selected = new ArrayList<>();
        SplittableRandom random = new SplittableRandom(seed(seedKey));
        while (!pool.isEmpty() && selected.size() < count) {
            double total = 0.0D;
            for (AscensionRewardDefinition reward : pool) {
                total += Math.max(0.0001D, reward.offerWeight().orElse(1.0D));
            }
            double roll = random.nextDouble() * total;
            double cursor = 0.0D;
            int picked = 0;
            for (int index = 0; index < pool.size(); index++) {
                cursor += Math.max(0.0001D, pool.get(index).offerWeight().orElse(1.0D));
                if (roll <= cursor) {
                    picked = index;
                    break;
                }
            }
            AscensionRewardDefinition reward = pool.remove(picked);
            selected.add(reward.id());
        }
        return List.copyOf(selected);
    }

    private static long seed(String value) {
        long seed = 0x9E3779B97F4A7C15L;
        for (int index = 0; index < value.length(); index++) {
            seed ^= value.charAt(index);
            seed *= 0x100000001B3L;
            seed ^= (seed >>> 31);
        }
        return seed;
    }

    private static String normalizeSourceKey(ResourceLocation offerId, String sourceProgressionKey) {
        if (sourceProgressionKey == null || sourceProgressionKey.isBlank()) {
            return offerId.toString();
        }
        return sourceProgressionKey.trim();
    }

    private static Optional<WorldAwakenedAscensionOfferRuntime> activePendingRuntime(
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        return state.pendingAscensionOfferInstances().values().stream().findFirst();
    }

    private static Optional<WorldAwakenedAscensionOfferRuntime> findRuntimeByOfferAndSource(
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            ResourceLocation offerId,
            String sourceKey) {
        return state.pendingAscensionOfferInstances().values().stream()
                .filter(runtime -> runtime.offerId().equals(offerId) && runtime.sourceKey().equals(sourceKey))
                .findFirst()
                .or(() -> state.resolvedAscensionOfferInstances().values().stream()
                        .filter(runtime -> runtime.offerId().equals(offerId) && runtime.sourceKey().equals(sourceKey))
                        .findFirst());
    }

    private static String nextRuntimeInstanceId(WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        LinkedHashSet<String> existingIds = new LinkedHashSet<>();
        existingIds.addAll(state.pendingAscensionOfferInstances().keySet());
        existingIds.addAll(state.resolvedAscensionOfferInstances().keySet());
        String candidate = WorldAwakenedAscensionOfferRuntime.randomOpaqueInstanceId();
        while (existingIds.contains(candidate)) {
            candidate = WorldAwakenedAscensionOfferRuntime.randomOpaqueInstanceId();
        }
        return candidate;
    }

    private static WorldAwakenedPlayerProgressionSavedData.PlayerStageState playerState(ServerLevel level, ServerPlayer player) {
        return WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(player.getUUID());
    }

    private static void refreshSummarySets(WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        WorldAwakenedAscensionStateEditor.rebuildSummarySets(state);
    }

    private static ResourceLocation parseResourceLocation(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String raw = element.getAsString();
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }
        if (!raw.contains(":")) {
            return ResourceLocation.tryParse(WorldAwakenedConstants.MOD_ID + ":" + raw);
        }
        return null;
    }

    public record ResetSummary(
            int pendingOffers,
            int resolvedOffers,
            int chosenRewards,
            int forfeitedRewards) {
        public int totalCleared() {
            return pendingOffers + resolvedOffers + chosenRewards + forfeitedRewards;
        }
    }

    public record RevokeSummary(int reopenedOffers, boolean removedLooseRewardOnly) {
        public boolean changed() {
            return reopenedOffers > 0 || removedLooseRewardOnly;
        }
    }

    private static boolean hasStage(Set<ResourceLocation> activeStages, WorldAwakenedStageRegistry registry, ResourceLocation requested) {
        Optional<ResourceLocation> canonical = registry.resolveCanonicalId(requested);
        if (canonical.isPresent()) {
            return activeStages.contains(canonical.get());
        }
        return activeStages.contains(requested);
    }

    private static String displayText(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            return value.isBlank() ? fallback : value;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text") && object.get("text").isJsonPrimitive()) {
                String text = object.getAsJsonPrimitive("text").getAsString();
                if (!text.isBlank()) {
                    return text;
                }
            }
            if (object.has("translate") && object.get("translate").isJsonPrimitive()) {
                String key = object.getAsJsonPrimitive("translate").getAsString();
                if (!key.isBlank()) {
                    return key;
                }
            }
        }
        return fallback.isBlank() ? element.toString() : fallback;
    }

    private static MutableComponent displayComponent(JsonElement element, ServerLevel level, String fallback) {
        if (element == null || element.isJsonNull()) {
            return Component.literal(fallback);
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            return Component.literal(value.isBlank() ? fallback : value);
        }
        try {
            MutableComponent parsed = Component.Serializer.fromJson(element, level.registryAccess());
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
        }
        return Component.literal(displayText(element, fallback));
    }

    private record CandidateSelection(List<ResourceLocation> candidates, String detail) {
    }

    public record OpenOfferView(
            String instanceId,
            ResourceLocation offerId,
            String title,
            String description,
            List<RewardChoiceView> rewards) {
    }

    public record RewardChoiceView(
            ResourceLocation rewardId,
            String title,
            String description,
            String rarity) {
    }

    public record GrantResult(
            ResourceLocation offerId,
            String instanceId,
            GrantStatus status,
            String detail) {
    }

    public enum GrantStatus {
        GRANTED,
        ALREADY_PRESENT,
        REJECTED
    }

    public record ChooseResult(
            ChooseStatus status,
            String detail) {
    }

    public enum ChooseStatus {
        ACCEPTED,
        REJECTED
    }

    public record SuppressionMutationResult(
            boolean changed,
            String detail,
            String diagnosticCode,
            ResourceLocation rewardId,
            Set<String> componentKeys) {
    }

    public enum RewardLiveState {
        ACTIVE,
        SUPPRESSED,
        PARTIALLY_SUPPRESSED,
        SUPPRESSED_GROUP,
        SUPPRESSION_REJECTED_INVALID_GROUP_STATE,
        SUPPRESSION_REJECTED_NOT_INDEPENDENTLY_SUPPORTED,
        MISSING_DEFINITION,
        UNKNOWN
    }

    public record RewardSuppressionView(
            ResourceLocation rewardId,
            boolean owned,
            boolean rewardSuppressed,
            RewardLiveState liveState,
            Set<String> configuredSuppressedComponentKeys,
            Set<String> effectiveSuppressedComponentKeys,
            Set<String> missingSuppressedComponentKeys,
            String rejectionCode,
            String rejectionDetail,
            boolean groupedSuppressionActive) {
        static RewardSuppressionView unknown(ResourceLocation rewardId) {
            return new RewardSuppressionView(
                    rewardId,
                    false,
                    false,
                    RewardLiveState.UNKNOWN,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    "",
                    "",
                    false);
        }
    }

    private record ComponentSuppressionEntry(
            int index,
            AscensionComponentDefinition component,
            String componentKey,
            AscensionComponentSuppressionPolicy effectivePolicy,
            Optional<String> normalizedGroup,
            boolean componentLevelSupported) {
    }

    private record ComponentSuppressionResolution(
            boolean accepted,
            String detail,
            String code,
            Set<String> componentKeys,
            boolean groupedSuppression) {
        static ComponentSuppressionResolution accepted(Set<String> componentKeys, boolean groupedSuppression) {
            return new ComponentSuppressionResolution(true, "", "", Set.copyOf(componentKeys), groupedSuppression);
        }

        static ComponentSuppressionResolution rejected(String detail, String code) {
            return new ComponentSuppressionResolution(false, detail, code, Set.of(), false);
        }
    }
}
