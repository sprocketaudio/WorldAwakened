package net.sprocketgames.worldawakened.ascension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;

final class WorldAwakenedAscensionStateEditor {
    private WorldAwakenedAscensionStateEditor() {
    }

    static ResetSummary resetAll(WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        int pending = state.pendingAscensionOfferInstances().size();
        int resolved = state.resolvedAscensionOfferInstances().size();
        int chosen = state.chosenAscensionRewards().size();
        int forfeited = state.forfeitedAscensionRewards().size();

        state.pendingAscensionOfferInstances().clear();
        state.resolvedAscensionOfferInstances().clear();
        state.pendingAscensionOffers().clear();
        state.resolvedAscensionOffers().clear();
        state.chosenAscensionRewards().clear();
        state.forfeitedAscensionRewards().clear();
        state.forfeitedAscensionRewardsByOffer().clear();
        state.ascensionRewardUnlockTimestamps().clear();
        state.ascensionRewardSources().clear();
        state.suppressedAscensionRewards().clear();
        state.suppressedAscensionComponentsByReward().clear();
        state.ascensionRewardSuppressionTimestamps().clear();
        state.ascensionComponentSuppressionTimestamps().clear();

        if (pending > 0 || resolved > 0 || chosen > 0 || forfeited > 0) {
            state.markDirty();
        }
        return new ResetSummary(pending, resolved, chosen, forfeited);
    }

    static RewardRevokeSummary revokeReward(
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            ResourceLocation rewardId) {
        List<WorldAwakenedAscensionOfferRuntime> reopened = new ArrayList<>();
        List<String> resolvedKeys = new ArrayList<>();
        for (Map.Entry<String, WorldAwakenedAscensionOfferRuntime> entry : state.resolvedAscensionOfferInstances().entrySet()) {
            WorldAwakenedAscensionOfferRuntime runtime = entry.getValue();
            if (runtime.chosenRewardId().filter(rewardId::equals).isPresent()) {
                resolvedKeys.add(entry.getKey());
                reopened.add(WorldAwakenedAscensionOfferRuntime.pending(
                        runtime.instanceId(),
                        runtime.offerId(),
                        runtime.sourceKey(),
                        runtime.grantedAtMillis(),
                        runtime.candidateRewards()));
            }
        }

        boolean removedLooseReward = state.chosenAscensionRewards().remove(rewardId);
        state.ascensionRewardUnlockTimestamps().remove(rewardId);
        state.ascensionRewardSources().remove(rewardId);
        state.suppressedAscensionRewards().remove(rewardId);
        state.suppressedAscensionComponentsByReward().remove(rewardId);
        state.ascensionRewardSuppressionTimestamps().remove(rewardId);
        state.ascensionComponentSuppressionTimestamps().keySet().removeIf(target ->
                target.startsWith("component|" + rewardId + "|"));

        for (String instanceId : resolvedKeys) {
            state.resolvedAscensionOfferInstances().remove(instanceId);
            state.forfeitedAscensionRewardsByOffer().remove(instanceId);
        }
        if (!reopened.isEmpty()) {
            prependPending(state, reopened);
        }

        rebuildSummarySets(state);
        if (removedLooseReward || !resolvedKeys.isEmpty()) {
            state.markDirty();
        }
        return new RewardRevokeSummary(reopened.size(), removedLooseReward && reopened.isEmpty());
    }

    static OfferMutationSummary reopenInstance(
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return new OfferMutationSummary(false, "invalid_instance_id");
        }
        if (state.pendingAscensionOfferInstances().containsKey(instanceId)) {
            return new OfferMutationSummary(false, "instance_already_pending");
        }

        WorldAwakenedAscensionOfferRuntime resolved = state.resolvedAscensionOfferInstances().remove(instanceId);
        if (resolved == null) {
            return new OfferMutationSummary(false, "instance_not_found");
        }

        state.forfeitedAscensionRewardsByOffer().remove(instanceId);
        prependPending(state, List.of(WorldAwakenedAscensionOfferRuntime.pending(
                resolved.instanceId(),
                resolved.offerId(),
                resolved.sourceKey(),
                resolved.grantedAtMillis(),
                resolved.candidateRewards())));
        rebuildSummarySets(state);
        state.markDirty();
        return new OfferMutationSummary(true, "reopened");
    }

    static OfferMutationSummary clearInstance(
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return new OfferMutationSummary(false, "invalid_instance_id");
        }

        boolean changed = state.pendingAscensionOfferInstances().remove(instanceId) != null;
        WorldAwakenedAscensionOfferRuntime resolved = state.resolvedAscensionOfferInstances().remove(instanceId);
        if (resolved != null) {
            changed = true;
        }
        state.forfeitedAscensionRewardsByOffer().remove(instanceId);

        if (!changed) {
            return new OfferMutationSummary(false, "instance_not_found");
        }

        rebuildSummarySets(state);
        state.markDirty();
        return new OfferMutationSummary(true, "cleared");
    }

    static void rebuildSummarySets(WorldAwakenedPlayerProgressionSavedData.PlayerStageState state) {
        state.pendingAscensionOffers().clear();
        for (WorldAwakenedAscensionOfferRuntime runtime : state.pendingAscensionOfferInstances().values()) {
            state.pendingAscensionOffers().add(runtime.offerId());
        }

        state.resolvedAscensionOffers().clear();
        state.chosenAscensionRewards().clear();
        for (WorldAwakenedAscensionOfferRuntime runtime : state.resolvedAscensionOfferInstances().values()) {
            state.resolvedAscensionOffers().add(runtime.offerId());
            runtime.chosenRewardId().ifPresent(state.chosenAscensionRewards()::add);
        }

        LinkedHashSet<ResourceLocation> mergedForfeited = new LinkedHashSet<>();
        state.forfeitedAscensionRewardsByOffer().values().forEach(mergedForfeited::addAll);
        state.forfeitedAscensionRewards().clear();
        state.forfeitedAscensionRewards().addAll(mergedForfeited);

        state.ascensionRewardUnlockTimestamps().keySet().removeIf(rewardId -> !state.chosenAscensionRewards().contains(rewardId));
        state.ascensionRewardSources().keySet().removeIf(rewardId -> !state.chosenAscensionRewards().contains(rewardId));
        state.suppressedAscensionRewards().retainAll(state.chosenAscensionRewards());
        state.suppressedAscensionComponentsByReward().keySet().removeIf(rewardId -> !state.chosenAscensionRewards().contains(rewardId));
        state.suppressedAscensionComponentsByReward().values().forEach(keys -> keys.removeIf(key -> key == null || key.isBlank()));
        state.suppressedAscensionComponentsByReward().entrySet().removeIf(entry -> entry.getValue().isEmpty());
        state.ascensionRewardSuppressionTimestamps().keySet().removeIf(rewardId -> !state.chosenAscensionRewards().contains(rewardId));
        state.ascensionComponentSuppressionTimestamps().keySet().removeIf(target -> {
            if (target == null || target.isBlank()) {
                return true;
            }
            if (target.startsWith("reward|")) {
                ResourceLocation rewardId = ResourceLocation.tryParse(target.substring("reward|".length()));
                return rewardId == null || !state.chosenAscensionRewards().contains(rewardId);
            }
            if (target.startsWith("component|")) {
                int divider = target.indexOf('|', "component|".length());
                if (divider <= "component|".length()) {
                    return true;
                }
                ResourceLocation rewardId = ResourceLocation.tryParse(target.substring("component|".length(), divider));
                String componentKey = target.substring(divider + 1);
                if (rewardId == null || componentKey.isBlank()) {
                    return true;
                }
                return !state.suppressedAscensionComponentsByReward().getOrDefault(rewardId, Set.of()).contains(componentKey);
            }
            return true;
        });
    }

    private static void prependPending(
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            List<WorldAwakenedAscensionOfferRuntime> reopened) {
        Map<String, WorldAwakenedAscensionOfferRuntime> existing = new LinkedHashMap<>(state.pendingAscensionOfferInstances());
        state.pendingAscensionOfferInstances().clear();
        for (WorldAwakenedAscensionOfferRuntime runtime : reopened) {
            state.pendingAscensionOfferInstances().put(runtime.instanceId(), runtime);
        }
        state.pendingAscensionOfferInstances().putAll(existing);
    }

    record ResetSummary(int pendingOffers, int resolvedOffers, int chosenRewards, int forfeitedRewards) {
        int totalCleared() {
            return pendingOffers + resolvedOffers + chosenRewards + forfeitedRewards;
        }
    }

    record RewardRevokeSummary(int reopenedOffers, boolean removedLooseRewardOnly) {
        boolean changed() {
            return reopenedOffers > 0 || removedLooseRewardOnly;
        }
    }

    record OfferMutationSummary(boolean changed, String detail) {
    }
}
