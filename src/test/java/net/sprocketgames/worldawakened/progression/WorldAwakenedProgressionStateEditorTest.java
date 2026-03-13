package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;

class WorldAwakenedProgressionStateEditorTest {
    @Test
    void resetStagesClearsUnlockedStageMetadata() {
        InMemoryState state = new InMemoryState();
        state.unlockedStages().add(id("testpack:alpha"));
        state.unlockTimestamps().put(id("testpack:alpha"), 10L);
        state.unlockSources().put(id("testpack:alpha"), "test");
        state.activeGroupStages().put("main", id("testpack:alpha"));

        WorldAwakenedProgressionStateEditor.StageResetSummary summary = WorldAwakenedProgressionStateEditor.resetStages(state);

        assertEquals(1, summary.clearedStages());
        assertTrue(state.unlockedStages().isEmpty());
        assertTrue(state.unlockTimestamps().isEmpty());
        assertTrue(state.unlockSources().isEmpty());
        assertTrue(state.activeGroupStages().isEmpty());
        assertTrue(state.dirty);
    }

    @Test
    void clearStageUsesCanonicalLockFlow() {
        StageDefinition stage = stage("testpack:alpha");
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(Map.of(stage.id(), stage));
        InMemoryState state = new InMemoryState();
        WorldAwakenedStageStateEngine.unlockStage(registry, state, stage.id(), "test", 1L);

        WorldAwakenedStageMutationResult result = WorldAwakenedProgressionStateEditor.clearStage(registry, state, stage.id());

        assertEquals(WorldAwakenedStageMutationStatus.LOCKED, result.status());
        assertFalse(state.unlockedStages().contains(stage.id()));
    }

    @Test
    void resetTriggersClearsCooldownsConsumedAndCounters() {
        InMemoryState state = new InMemoryState();
        state.triggerCooldowns().put("testpack:trigger", 10L);
        state.consumedOneShotTriggers().add("testpack:trigger");
        state.triggerCounters().put("testpack:trigger", 2);

        WorldAwakenedProgressionStateEditor.TriggerResetSummary summary = WorldAwakenedProgressionStateEditor.resetTriggers(state);

        assertEquals(3, summary.totalCleared());
        assertTrue(state.triggerCooldowns().isEmpty());
        assertTrue(state.consumedOneShotTriggers().isEmpty());
        assertTrue(state.triggerCounters().isEmpty());
    }

    @Test
    void resetRulesClearsCooldownsAndConsumedMarkers() {
        InMemoryState state = new InMemoryState();
        state.ruleCooldowns().put("testpack:rule", 10L);
        state.consumedRules().add("testpack:rule");

        WorldAwakenedProgressionStateEditor.RuleResetSummary summary = WorldAwakenedProgressionStateEditor.resetRules(state);

        assertEquals(2, summary.totalCleared());
        assertTrue(state.ruleCooldowns().isEmpty());
        assertTrue(state.consumedRules().isEmpty());
    }

    @Test
    void clearSpecificTriggerAndRuleOnlyRemovesMatchingEntries() {
        InMemoryState state = new InMemoryState();
        state.triggerCooldowns().put("testpack:trigger_a", 10L);
        state.triggerCounters().put("testpack:trigger_a", 1);
        state.triggerCooldowns().put("testpack:trigger_b", 20L);
        state.ruleCooldowns().put("testpack:rule_a", 10L);
        state.consumedRules().add("testpack:rule_a");
        state.ruleCooldowns().put("testpack:rule_b", 20L);

        assertTrue(WorldAwakenedProgressionStateEditor.clearTrigger(state, id("testpack:trigger_a")));
        assertTrue(WorldAwakenedProgressionStateEditor.clearRule(state, id("testpack:rule_a")));

        assertFalse(state.triggerCooldowns().containsKey("testpack:trigger_a"));
        assertTrue(state.triggerCooldowns().containsKey("testpack:trigger_b"));
        assertFalse(state.ruleCooldowns().containsKey("testpack:rule_a"));
        assertTrue(state.ruleCooldowns().containsKey("testpack:rule_b"));
        assertFalse(state.consumedRules().contains("testpack:rule_a"));
    }

    private static StageDefinition stage(String id) {
        return new StageDefinition(
                1,
                id(id),
                List.of(),
                JsonParser.parseString("\"" + id + "\""),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                true,
                true,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                StageUnlockPolicy.CUMULATIVE,
                false);
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }

    private static final class InMemoryState implements WorldAwakenedMutableStageState, WorldAwakenedMutableTriggerState, WorldAwakenedMutableRuleState {
        private final Set<ResourceLocation> unlockedStages = new LinkedHashSet<>();
        private final Map<ResourceLocation, Long> unlockTimestamps = new LinkedHashMap<>();
        private final Map<ResourceLocation, String> unlockSources = new LinkedHashMap<>();
        private final Map<String, ResourceLocation> activeGroupStages = new LinkedHashMap<>();
        private final Map<String, Long> triggerCooldowns = new LinkedHashMap<>();
        private final Set<String> consumedOneShotTriggers = new LinkedHashSet<>();
        private final Map<String, Integer> triggerCounters = new LinkedHashMap<>();
        private final Map<String, Long> ruleCooldowns = new LinkedHashMap<>();
        private final Set<String> consumedRules = new LinkedHashSet<>();
        private boolean dirty;

        @Override
        public Set<ResourceLocation> unlockedStages() {
            return unlockedStages;
        }

        @Override
        public Map<ResourceLocation, Long> unlockTimestamps() {
            return unlockTimestamps;
        }

        @Override
        public Map<ResourceLocation, String> unlockSources() {
            return unlockSources;
        }

        @Override
        public Map<String, ResourceLocation> activeGroupStages() {
            return activeGroupStages;
        }

        @Override
        public Map<String, Long> triggerCooldowns() {
            return triggerCooldowns;
        }

        @Override
        public Set<String> consumedOneShotTriggers() {
            return consumedOneShotTriggers;
        }

        @Override
        public Map<String, Integer> triggerCounters() {
            return triggerCounters;
        }

        @Override
        public Map<String, Long> ruleCooldowns() {
            return ruleCooldowns;
        }

        @Override
        public Set<String> consumedRules() {
            return consumedRules;
        }

        @Override
        public void markDirty() {
            dirty = true;
        }
    }
}
