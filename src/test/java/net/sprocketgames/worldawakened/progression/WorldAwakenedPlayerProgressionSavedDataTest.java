package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

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
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

