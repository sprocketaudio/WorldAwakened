package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

class WorldAwakenedWorldProgressionSavedDataTest {
    @Test
    void getUsesTransientFallbackWhenLevelDataStorageUnavailable() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(null);
        when(level.getDataStorage()).thenReturn(null);

        WorldAwakenedWorldProgressionSavedData first = WorldAwakenedWorldProgressionSavedData.get(level);
        WorldAwakenedWorldProgressionSavedData second = WorldAwakenedWorldProgressionSavedData.get(level);

        assertSame(first, second);
    }

    @Test
    void roundTripsWorldProgressionState() {
        WorldAwakenedWorldProgressionSavedData data = new WorldAwakenedWorldProgressionSavedData();
        ResourceLocation baseline = id("testpack:baseline");
        ResourceLocation legacy = id("testpack:legacy_removed");

        data.unlockedStages().add(baseline);
        data.unlockedStages().add(legacy);
        data.unlockTimestamps().put(baseline, 111L);
        data.unlockSources().put(baseline, "command");
        data.activeGroupStages().put("main", baseline);
        data.triggerCooldowns().put("testpack:boss_once", 5000L);
        data.consumedOneShotTriggers().add("testpack:boss_once");
        data.triggerCounters().put("boss_kills", 3);
        data.ruleCooldowns().put("testpack:night_pressure", 9000L);
        data.consumedRules().add("testpack:night_pressure");
        data.invasionCooldownTrackers().put("raid_alpha", 9000L);
        data.setActiveInvasionProfileId("testpack:raid_alpha");
        data.setActiveInvasionDisplayName("Raid Alpha");
        data.activeInvasionTags().add("night");
        data.activeInvasionTags().add("nether");
        data.setActiveInvasionInstanceId(7L);
        data.setActiveInvasionStartedAtMillis(100L);
        data.setActiveInvasionWarningUntilMillis(200L);
        data.setActiveInvasionEndsAtMillis(700L);
        data.setActiveInvasionDurationSeconds(500);
        data.setActiveInvasionWarningSeconds(100);
        data.setActiveInvasionPressureModifier(1.75D);
        data.setActiveInvasionRewardProfileId("testpack:raid_reward");
        data.setInvasionGlobalCooldownUntilMillis(15000L);
        data.nextInvasionInstanceId();
        data.worldScalars().put("spawn_pressure", 1.25D);
        data.setGlobalDifficultyModifier(1.15D);
        data.setGlobalDifficultyUpdatedAtMillis(42L);
        data.setGlobalDifficultyUpdatedBy("operator");
        data.setChallengeWorldModifier(1.25D);
        data.setChallengeWorldCooldownUntilMillis(123456L);
        data.setChallengeWorldChangeCount(3);
        data.setChallengeWorldUpdatedAtMillis(456L);
        data.setChallengeWorldUpdatedBy("operator");
        data.setChallengeWorldVoteActive(true);
        data.setChallengeWorldVoteTarget(1.30D);
        data.setChallengeWorldVoteInitiator("00000000-0000-0000-0000-000000000001");
        data.setChallengeWorldVoteStartedAtMillis(1000L);
        data.setChallengeWorldVoteTimeoutAtMillis(2000L);
        data.challengeWorldVoteEligible().add("00000000-0000-0000-0000-000000000001");
        data.challengeWorldVoteYes().add("00000000-0000-0000-0000-000000000001");
        data.challengeWorldVoteNo().add("00000000-0000-0000-0000-000000000002");

        CompoundTag encoded = data.toTag();
        WorldAwakenedWorldProgressionSavedData decoded = WorldAwakenedWorldProgressionSavedData.fromTag(encoded);

        assertTrue(decoded.unlockedStages().contains(baseline));
        assertTrue(decoded.unlockedStages().contains(legacy));
        assertEquals(111L, decoded.unlockTimestamps().get(baseline));
        assertEquals("command", decoded.unlockSources().get(baseline));
        assertEquals(baseline, decoded.activeGroupStages().get("main"));
        assertEquals(5000L, decoded.triggerCooldowns().get("testpack:boss_once"));
        assertTrue(decoded.consumedOneShotTriggers().contains("testpack:boss_once"));
        assertEquals(3, decoded.triggerCounters().get("boss_kills"));
        assertEquals(9000L, decoded.ruleCooldowns().get("testpack:night_pressure"));
        assertTrue(decoded.consumedRules().contains("testpack:night_pressure"));
        assertEquals(9000L, decoded.invasionCooldownTrackers().get("raid_alpha"));
        assertTrue(decoded.invasionActive());
        assertEquals("testpack:raid_alpha", decoded.activeInvasionProfileId());
        assertEquals("Raid Alpha", decoded.activeInvasionDisplayName());
        assertTrue(decoded.activeInvasionTags().contains("night"));
        assertTrue(decoded.activeInvasionTags().contains("nether"));
        assertEquals(7L, decoded.activeInvasionInstanceId());
        assertEquals(100L, decoded.activeInvasionStartedAtMillis());
        assertEquals(200L, decoded.activeInvasionWarningUntilMillis());
        assertEquals(700L, decoded.activeInvasionEndsAtMillis());
        assertEquals(500, decoded.activeInvasionDurationSeconds());
        assertEquals(100, decoded.activeInvasionWarningSeconds());
        assertEquals(1.75D, decoded.activeInvasionPressureModifier());
        assertEquals("testpack:raid_reward", decoded.activeInvasionRewardProfileId());
        assertEquals(15000L, decoded.invasionGlobalCooldownUntilMillis());
        assertEquals(1L, decoded.invasionInstanceCounter());
        assertEquals(1.25D, decoded.worldScalars().get("spawn_pressure"));
        assertEquals(1.15D, decoded.globalDifficultyModifier());
        assertEquals(42L, decoded.globalDifficultyUpdatedAtMillis());
        assertEquals("operator", decoded.globalDifficultyUpdatedBy());
        assertEquals(1.25D, decoded.challengeWorldModifier());
        assertEquals(123456L, decoded.challengeWorldCooldownUntilMillis());
        assertEquals(3, decoded.challengeWorldChangeCount());
        assertEquals(456L, decoded.challengeWorldUpdatedAtMillis());
        assertEquals("operator", decoded.challengeWorldUpdatedBy());
        assertTrue(decoded.challengeWorldVoteActive());
        assertEquals(1.30D, decoded.challengeWorldVoteTarget());
        assertEquals("00000000-0000-0000-0000-000000000001", decoded.challengeWorldVoteInitiator());
        assertEquals(1000L, decoded.challengeWorldVoteStartedAtMillis());
        assertEquals(2000L, decoded.challengeWorldVoteTimeoutAtMillis());
        assertTrue(decoded.challengeWorldVoteEligible().contains("00000000-0000-0000-0000-000000000001"));
        assertTrue(decoded.challengeWorldVoteYes().contains("00000000-0000-0000-0000-000000000001"));
        assertTrue(decoded.challengeWorldVoteNo().contains("00000000-0000-0000-0000-000000000002"));
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

