package net.sprocketgames.worldawakened.ascension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferRepeatPolicy;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardRepeatPolicy;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;

class WorldAwakenedAscensionServiceTest {
    @Test
    void rewardReusePolicyBlocksChosenAndForfeitedRewardsWhenDisabled() {
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = new WorldAwakenedPlayerProgressionSavedData()
                .getOrCreate(UUID.randomUUID());
        ResourceLocation chosen = id("testpack:chosen_reward");
        ResourceLocation forfeited = id("testpack:forfeited_reward");
        state.chosenAscensionRewards().add(chosen);
        state.forfeitedAscensionRewards().add(forfeited);

        AscensionOfferDefinition offer = offer(AscensionRewardRepeatPolicy.BLOCK_ALL);

        assertTrue(WorldAwakenedAscensionService.rewardBlockedByOfferReusePolicy(offer, chosen, state));
        assertTrue(WorldAwakenedAscensionService.rewardBlockedByOfferReusePolicy(offer, forfeited, state));
        assertFalse(WorldAwakenedAscensionService.rewardBlockedByOfferReusePolicy(offer, id("testpack:fresh_reward"), state));
    }

    @Test
    void rewardReusePolicyAlwaysBlocksChosenRewardsAndAllowsForfeitedRewardsWhenEnabled() {
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = new WorldAwakenedPlayerProgressionSavedData()
                .getOrCreate(UUID.randomUUID());
        ResourceLocation chosen = id("testpack:chosen_reward");
        ResourceLocation forfeited = id("testpack:forfeited_reward");
        state.chosenAscensionRewards().add(chosen);
        state.forfeitedAscensionRewards().add(forfeited);

        AscensionOfferDefinition offer = offer(AscensionRewardRepeatPolicy.ALLOW_FORFEITED_ONLY);

        assertTrue(WorldAwakenedAscensionService.rewardBlockedByOfferReusePolicy(offer, chosen, state));
        assertFalse(WorldAwakenedAscensionService.rewardBlockedByOfferReusePolicy(offer, forfeited, state));
    }

    @Test
    void rewardReusePolicyAllowAllAllowsChosenAndForfeitedRewards() {
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = new WorldAwakenedPlayerProgressionSavedData()
                .getOrCreate(UUID.randomUUID());
        ResourceLocation chosen = id("testpack:chosen_reward");
        ResourceLocation forfeited = id("testpack:forfeited_reward");
        state.chosenAscensionRewards().add(chosen);
        state.forfeitedAscensionRewards().add(forfeited);

        AscensionOfferDefinition offer = offer(AscensionRewardRepeatPolicy.ALLOW_ALL);

        assertFalse(WorldAwakenedAscensionService.rewardBlockedByOfferReusePolicy(offer, chosen, state));
        assertFalse(WorldAwakenedAscensionService.rewardBlockedByOfferReusePolicy(offer, forfeited, state));
    }

    @Test
    void conditionlessOfferIsNotImplicitlyGrantedByPhaseChanges() {
        AscensionOfferDefinition offer = offer(AscensionRewardRepeatPolicy.BLOCK_ALL);

        assertFalse(WorldAwakenedAscensionService.hasExplicitAutomaticAvailability(offer));
    }

    @Test
    void explicitStageFilterMakesOfferEligibleForAutomaticPhaseEvaluation() {
        AscensionOfferDefinition base = offer(AscensionRewardRepeatPolicy.BLOCK_ALL);
        JsonObject stageFilters = new JsonObject();
        stageFilters.add("all_of", com.google.gson.JsonParser.parseString("[\"testpack:phase\"]"));
        AscensionOfferDefinition offer = new AscensionOfferDefinition(
                base.schemaVersion(), base.id(), base.enabled(), base.presentation(),
                new AscensionOfferDefinition.OfferFilters(List.of(), Optional.of(stageFilters),
                        Optional.empty(), Optional.empty()),
                base.candidates(), base.policy());

        assertTrue(WorldAwakenedAscensionService.hasExplicitAutomaticAvailability(offer));
        assertEquals("automatic:testpack:test_offer", WorldAwakenedAscensionService.automaticOfferSourceKey(offer),
                "phase unlock and login reconciliation must dedupe against the same source identity");
    }

    @Test
    void pendingAndResolvedInstancesBothCountAsExistingOffers() {
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = new WorldAwakenedPlayerProgressionSavedData()
                .getOrCreate(UUID.randomUUID());
        ResourceLocation offerId = id("testpack:test_offer");
        ResourceLocation rewardId = id("testpack:fresh_reward");
        WorldAwakenedAscensionOfferRuntime pending = WorldAwakenedAscensionOfferRuntime.pending(
                "wao_pending1", offerId, "phase:a", 1L, List.of(rewardId));
        state.pendingAscensionOfferInstances().put(pending.instanceId(), pending);
        assertTrue(WorldAwakenedAscensionService.hasOfferInstance(state, offerId));

        state.pendingAscensionOfferInstances().clear();
        WorldAwakenedAscensionOfferRuntime resolved = pending.withInstanceId("wao_resolved").resolve(rewardId, 2L);
        state.resolvedAscensionOfferInstances().put(resolved.instanceId(), resolved);
        assertTrue(WorldAwakenedAscensionService.hasOfferInstance(state, offerId));
        assertFalse(WorldAwakenedAscensionService.hasOfferInstance(state, id("testpack:other_offer")));
    }

    private static AscensionOfferDefinition offer(AscensionRewardRepeatPolicy repeatPolicy) {
        return new AscensionOfferDefinition(
                1,
                id("testpack:test_offer"),
                true,
                new AscensionOfferDefinition.OfferPresentation(
                        new JsonPrimitive("Test Offer"),
                        Optional.empty()),
                new AscensionOfferDefinition.OfferFilters(
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                new AscensionOfferDefinition.OfferCandidates(
                        List.of(id("testpack:chosen_reward"), id("testpack:forfeited_reward"), id("testpack:fresh_reward")),
                        List.of(),
                        net.sprocketgames.worldawakened.data.definition.AscensionOfferMode.EXPLICIT_LIST,
                        new JsonObject()),
                new AscensionOfferDefinition.OfferPolicy(
                        3,
                        1,
                        0,
                        true,
                        AscensionOfferRepeatPolicy.ONCE_PER_PLAYER,
                        repeatPolicy));
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
