package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class WorldAwakenedWorldProgressionSavedDataTest {
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
        data.worldScalars().put("spawn_pressure", 1.25D);

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
        assertEquals(1.25D, decoded.worldScalars().get("spawn_pressure"));
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

