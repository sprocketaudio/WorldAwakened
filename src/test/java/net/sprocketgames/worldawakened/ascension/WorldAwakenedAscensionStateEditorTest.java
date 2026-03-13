package net.sprocketgames.worldawakened.ascension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;

class WorldAwakenedAscensionStateEditorTest {
    @Test
    void reopenInstanceMovesResolvedOfferBackToPendingAndClearsForfeits() {
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = new WorldAwakenedPlayerProgressionSavedData()
                .getOrCreate(UUID.randomUUID());
        String instanceId = "wao_000000a1";
        WorldAwakenedAscensionOfferRuntime resolved = WorldAwakenedAscensionOfferRuntime.pending(
                instanceId,
                id("testpack:starter"),
                "stage:test",
                10L,
                List.of(id("testpack:reward_a"), id("testpack:reward_b")))
                .resolve(id("testpack:reward_a"), 20L);
        state.resolvedAscensionOfferInstances().put(resolved.instanceId(), resolved);
        state.forfeitedAscensionRewardsByOffer().put(resolved.instanceId(), Set.of(id("testpack:reward_b")));
        WorldAwakenedAscensionStateEditor.rebuildSummarySets(state);

        WorldAwakenedAscensionStateEditor.OfferMutationSummary result = WorldAwakenedAscensionStateEditor.reopenInstance(
                state,
                resolved.instanceId());

        assertTrue(result.changed());
        assertTrue(state.pendingAscensionOfferInstances().containsKey(resolved.instanceId()));
        assertFalse(state.resolvedAscensionOfferInstances().containsKey(resolved.instanceId()));
        assertFalse(state.forfeitedAscensionRewardsByOffer().containsKey(resolved.instanceId()));
        assertFalse(state.chosenAscensionRewards().contains(id("testpack:reward_a")));
        assertEquals(Set.of(id("testpack:starter")), state.pendingAscensionOffers());
    }

    @Test
    void revokeRewardReopensAllMatchingResolvedOffers() {
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = new WorldAwakenedPlayerProgressionSavedData()
                .getOrCreate(UUID.randomUUID());
        ResourceLocation reward = id("testpack:reward_a");
        String firstInstanceId = "wao_000000b1";
        String secondInstanceId = "wao_000000b2";
        WorldAwakenedAscensionOfferRuntime first = WorldAwakenedAscensionOfferRuntime.pending(
                firstInstanceId,
                id("testpack:starter"),
                "stage:test",
                10L,
                List.of(reward, id("testpack:reward_b")))
                .resolve(reward, 20L);
        WorldAwakenedAscensionOfferRuntime second = WorldAwakenedAscensionOfferRuntime.pending(
                secondInstanceId,
                id("testpack:backup"),
                "command:test",
                30L,
                List.of(reward, id("testpack:reward_c")))
                .resolve(reward, 40L);
        state.resolvedAscensionOfferInstances().put(first.instanceId(), first);
        state.resolvedAscensionOfferInstances().put(second.instanceId(), second);
        state.forfeitedAscensionRewardsByOffer().put(first.instanceId(), Set.of(id("testpack:reward_b")));
        state.forfeitedAscensionRewardsByOffer().put(second.instanceId(), Set.of(id("testpack:reward_c")));
        WorldAwakenedAscensionStateEditor.rebuildSummarySets(state);

        WorldAwakenedAscensionStateEditor.RewardRevokeSummary summary = WorldAwakenedAscensionStateEditor.revokeReward(state, reward);

        assertEquals(2, summary.reopenedOffers());
        assertTrue(state.resolvedAscensionOfferInstances().isEmpty());
        assertEquals(2, state.pendingAscensionOfferInstances().size());
        assertFalse(state.chosenAscensionRewards().contains(reward));
        assertTrue(state.forfeitedAscensionRewards().isEmpty());
        assertFalse(state.suppressedAscensionRewards().contains(reward));
        assertFalse(state.suppressedAscensionComponentsByReward().containsKey(reward));
    }

    @Test
    void resetAllClearsAscensionPersistenceBuckets() {
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = new WorldAwakenedPlayerProgressionSavedData()
                .getOrCreate(UUID.randomUUID());
        ResourceLocation reward = id("testpack:reward_a");
        String pendingInstanceId = "wao_000000c1";
        String resolvedInstanceId = "wao_000000c2";
        WorldAwakenedAscensionOfferRuntime pending = WorldAwakenedAscensionOfferRuntime.pending(
                pendingInstanceId,
                id("testpack:starter"),
                "stage:test",
                10L,
                List.of(reward));
        state.pendingAscensionOfferInstances().put(pending.instanceId(), pending);
        state.resolvedAscensionOfferInstances().put(resolvedInstanceId, pending.withInstanceId(resolvedInstanceId).resolve(reward, 20L));
        state.forfeitedAscensionRewardsByOffer().put(resolvedInstanceId, Set.of(id("testpack:reward_b")));
        WorldAwakenedAscensionStateEditor.rebuildSummarySets(state);
        state.ascensionRewardUnlockTimestamps().put(reward, 10L);
        state.ascensionRewardSources().put(reward, "stage:test");
        state.suppressedAscensionRewards().add(reward);
        state.suppressedAscensionComponentsByReward().put(
                reward,
                new java.util.LinkedHashSet<>(Set.of("0|worldawakened:max_health_bonus")));
        state.ascensionRewardSuppressionTimestamps().put(reward, 12L);
        state.ascensionComponentSuppressionTimestamps().put(
                "component|" + reward + "|0|worldawakened:max_health_bonus",
                12L);

        WorldAwakenedAscensionStateEditor.ResetSummary summary = WorldAwakenedAscensionStateEditor.resetAll(state);

        assertEquals(4, summary.totalCleared());
        assertTrue(state.pendingAscensionOfferInstances().isEmpty());
        assertTrue(state.resolvedAscensionOfferInstances().isEmpty());
        assertTrue(state.chosenAscensionRewards().isEmpty());
        assertTrue(state.forfeitedAscensionRewards().isEmpty());
        assertTrue(state.suppressedAscensionRewards().isEmpty());
        assertTrue(state.suppressedAscensionComponentsByReward().isEmpty());
        assertTrue(state.ascensionRewardSuppressionTimestamps().isEmpty());
        assertTrue(state.ascensionComponentSuppressionTimestamps().isEmpty());
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
