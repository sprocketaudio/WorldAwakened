package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class WorldAwakenedProgressionHistoryTest {
    private static final ResourceLocation DORMANT = ResourceLocation.fromNamespaceAndPath("test", "dormant");
    private static final ResourceLocation STIRRING = ResourceLocation.fromNamespaceAndPath("test", "stirring");

    @Test
    void automaticDefaultPhaseAloneDoesNotBlockModeMigration() {
        InMemoryState state = new InMemoryState();
        state.unlockedStages().add(DORMANT);
        state.activeGroupStages().put("main", DORMANT);

        assertFalse(WorldAwakenedProgressionHistory.hasMeaningfulHistory(state, Set.of(DORMANT)));
    }

    @Test
    void laterPhaseOrPartialTriggerProgressBlocksModeMigration() {
        InMemoryState advanced = new InMemoryState();
        advanced.unlockedStages().add(DORMANT);
        advanced.unlockedStages().add(STIRRING);
        assertTrue(WorldAwakenedProgressionHistory.hasMeaningfulHistory(advanced, Set.of(DORMANT)));

        InMemoryState partialTrigger = new InMemoryState();
        partialTrigger.unlockedStages().add(DORMANT);
        partialTrigger.triggerCounters().put("test:reach_stirring", 1);
        assertTrue(WorldAwakenedProgressionHistory.hasMeaningfulHistory(partialTrigger, Set.of(DORMANT)));
    }

    private static final class InMemoryState implements WorldAwakenedProgressionState {
        private final Set<ResourceLocation> unlocked = new LinkedHashSet<>();
        private final Map<ResourceLocation, Long> timestamps = new LinkedHashMap<>();
        private final Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        private final Map<String, ResourceLocation> active = new LinkedHashMap<>();
        private final Map<String, Long> triggerCooldowns = new LinkedHashMap<>();
        private final Set<String> consumedTriggers = new LinkedHashSet<>();
        private final Map<String, Integer> counters = new LinkedHashMap<>();
        private final Map<String, Long> ruleCooldowns = new LinkedHashMap<>();
        private final Set<String> consumedRules = new LinkedHashSet<>();

        @Override public Set<ResourceLocation> unlockedStages() { return unlocked; }
        @Override public Map<ResourceLocation, Long> unlockTimestamps() { return timestamps; }
        @Override public Map<ResourceLocation, String> unlockSources() { return sources; }
        @Override public Map<String, ResourceLocation> activeGroupStages() { return active; }
        @Override public Map<String, Long> triggerCooldowns() { return triggerCooldowns; }
        @Override public Set<String> consumedOneShotTriggers() { return consumedTriggers; }
        @Override public Map<String, Integer> triggerCounters() { return counters; }
        @Override public Map<String, Long> ruleCooldowns() { return ruleCooldowns; }
        @Override public Set<String> consumedRules() { return consumedRules; }
        @Override public void markDirty() { }
    }
}
