package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionOfferRuntime;

class WorldAwakenedPlayerProgressionSavedDataTest {
    @Test
    void roundTripsPlayerStageScaffoldState() {
        WorldAwakenedPlayerProgressionSavedData data = new WorldAwakenedPlayerProgressionSavedData();
        UUID playerId = UUID.fromString("12345678-1234-1234-1234-123456789012");
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = data.getOrCreate(playerId);

        ResourceLocation stage = id("testpack:baseline");
        ResourceLocation dimension = id("minecraft:the_nether");
        ResourceLocation pendingOffer = id("testpack:offer_1");
        ResourceLocation chosenReward = id("testpack:reward_1");
        String pendingInstanceId = "wao_000000d1";
        String resolvedInstanceId = "wao_000000d2";

        state.unlockedStages().add(stage);
        state.unlockTimestamps().put(stage, 500L);
        state.unlockSources().put(stage, "test");
        state.activeGroupStages().put("main", stage);
        state.visitedDimensions().add(dimension);
        state.triggerCooldowns().put("boss_kill", 120L);
        state.consumedOneShotTriggers().add("testpack:manual_once");
        state.triggerCounters().put("boss_kill_total", 4);
        state.ruleCooldowns().put("testpack:night_pressure", 480L);
        state.consumedRules().add("testpack:night_pressure");
        state.debugInspectionFlags().add("verbose_stage_debug");
        state.pendingAscensionOffers().add(pendingOffer);
        state.chosenAscensionRewards().add(chosenReward);
        state.pendingAscensionOfferInstances().put(
                pendingInstanceId,
                WorldAwakenedAscensionOfferRuntime.pending(
                        pendingInstanceId,
                        pendingOffer,
                        "stage:testpack:baseline",
                        1000L,
                        List.of(chosenReward)));
        state.resolvedAscensionOfferInstances().put(
                resolvedInstanceId,
                new WorldAwakenedAscensionOfferRuntime(
                        resolvedInstanceId,
                        pendingOffer,
                        "stage:testpack:baseline",
                        1000L,
                        List.of(chosenReward),
                        Optional.of(chosenReward),
                        java.util.OptionalLong.of(2000L)));
        state.forfeitedAscensionRewardsByOffer().put(
                pendingInstanceId,
                new java.util.LinkedHashSet<>(List.of(id("testpack:reward_2"))));
        state.ascensionRewardUnlockTimestamps().put(chosenReward, 1000L);
        state.ascensionRewardSources().put(chosenReward, "stage:testpack:baseline");

        CompoundTag encoded = data.toTag();
        WorldAwakenedPlayerProgressionSavedData decoded = WorldAwakenedPlayerProgressionSavedData.fromTag(encoded);
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState decodedState = decoded.getIfPresent(playerId).orElseThrow();

        assertTrue(decodedState.unlockedStages().contains(stage));
        assertEquals(500L, decodedState.unlockTimestamps().get(stage));
        assertEquals("test", decodedState.unlockSources().get(stage));
        assertEquals(stage, decodedState.activeGroupStages().get("main"));
        assertTrue(decodedState.visitedDimensions().contains(dimension));
        assertEquals(120L, decodedState.triggerCooldowns().get("boss_kill"));
        assertTrue(decodedState.consumedOneShotTriggers().contains("testpack:manual_once"));
        assertEquals(4, decodedState.triggerCounters().get("boss_kill_total"));
        assertEquals(480L, decodedState.ruleCooldowns().get("testpack:night_pressure"));
        assertTrue(decodedState.consumedRules().contains("testpack:night_pressure"));
        assertTrue(decodedState.debugInspectionFlags().contains("verbose_stage_debug"));
        assertTrue(decodedState.pendingAscensionOffers().contains(pendingOffer));
        assertTrue(decodedState.chosenAscensionRewards().contains(chosenReward));
        assertTrue(decodedState.pendingAscensionOfferInstances().containsKey(pendingInstanceId));
        assertTrue(decodedState.resolvedAscensionOfferInstances().containsKey(resolvedInstanceId));
        assertTrue(decodedState.forfeitedAscensionRewardsByOffer().containsKey(pendingInstanceId));
        assertEquals(1000L, decodedState.ascensionRewardUnlockTimestamps().get(chosenReward));
        assertEquals("stage:testpack:baseline", decodedState.ascensionRewardSources().get(chosenReward));
    }

    @Test
    void migratesLegacyCompositeAscensionInstanceIdsOnLoad() {
        WorldAwakenedPlayerProgressionSavedData data = new WorldAwakenedPlayerProgressionSavedData();
        UUID playerId = UUID.fromString("12345678-1234-1234-1234-123456789012");
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = data.getOrCreate(playerId);

        ResourceLocation offerId = id("testpack:offer_1");
        ResourceLocation rewardId = id("testpack:reward_1");
        String legacyInstanceId = "testpack:offer_1|stage:testpack:baseline";

        state.pendingAscensionOfferInstances().put(
                legacyInstanceId,
                WorldAwakenedAscensionOfferRuntime.pending(
                        legacyInstanceId,
                        offerId,
                        "stage:testpack:baseline",
                        1000L,
                        List.of(rewardId)));
        state.forfeitedAscensionRewardsByOffer().put(legacyInstanceId, new java.util.LinkedHashSet<>(List.of(id("testpack:reward_2"))));

        CompoundTag encoded = data.toTag();
        WorldAwakenedPlayerProgressionSavedData decoded = WorldAwakenedPlayerProgressionSavedData.fromTag(encoded);
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState decodedState = decoded.getIfPresent(playerId).orElseThrow();

        assertEquals(1, decodedState.pendingAscensionOfferInstances().size());
        String migratedInstanceId = decodedState.pendingAscensionOfferInstances().keySet().iterator().next();
        assertTrue(WorldAwakenedAscensionOfferRuntime.isOpaqueInstanceId(migratedInstanceId));
        assertFalse(migratedInstanceId.contains("|"));
        assertTrue(decodedState.forfeitedAscensionRewardsByOffer().containsKey(migratedInstanceId));
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

