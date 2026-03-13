package net.sprocketgames.worldawakened.ascension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
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
                        repeatPolicy));
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
