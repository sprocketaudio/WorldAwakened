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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferMode;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardRepeatPolicy;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.ExecutionScope;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
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
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.DATA_LOAD,
                        "Chosen ascension reward definition missing: player={} reward={}",
                        player.getGameProfile().getName(),
                        rewardId);
                continue;
            }
            rewardEffects.apply(player, reward);
        }
        WorldAwakenedLog.debug(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Reconciled ascension rewards for {} reason={} chosen={}",
                player.getGameProfile().getName(),
                reason,
                state.chosenAscensionRewards().size());
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
        if (!rewardStillEligible(level, player, offer, reward, state)) {
            return new ChooseResult(ChooseStatus.REJECTED, "reward_ineligible");
        }

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
            notifyOfferAvailable(player, nextPending.get().offerId(), true);
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
            notifyOfferAvailable(player, offerId, alreadyPending);
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

    private void notifyOfferAvailable(ServerPlayer player, ResourceLocation offerId, boolean queued) {
        if (!WorldAwakenedCommonConfig.SHOW_ASCENSION_NOTIFICATIONS.get()) {
            return;
        }
        AscensionOfferDefinition offer = datapackService.currentSnapshot().data().ascensionOffers().get(offerId);
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
            if (rewardStillEligible(level, player, offer, reward, state)) {
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
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        if (!reward.enabled()) {
            return false;
        }
        if (rewardBlockedByOfferReusePolicy(offer, reward.id(), state)) {
            return false;
        }

        if (reward.uniqueGroup().isPresent()) {
            String uniqueGroup = reward.uniqueGroup().get();
            for (ResourceLocation chosenRewardId : state.chosenAscensionRewards()) {
                AscensionRewardDefinition chosen = datapackService.currentSnapshot().data().ascensionRewards().get(chosenRewardId);
                if (chosen != null && chosen.uniqueGroup().isPresent() && uniqueGroup.equals(chosen.uniqueGroup().get())) {
                    return false;
                }
            }
        }

        if (!reward.exclusionTags().isEmpty()) {
            Set<String> chosenTags = state.chosenAscensionRewards().stream()
                    .map(id -> datapackService.currentSnapshot().data().ascensionRewards().get(id))
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
}
