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

class WorldAwakenedStageStateEngineTest {
    @Test
    void unlockResolvesAliasAndPersistsCanonicalId() {
        StageDefinition stage = stage(
                "testpack:baseline",
                List.of("testpack:starter"),
                0,
                Optional.empty(),
                StageUnlockPolicy.CUMULATIVE);
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(Map.of(stage.id(), stage));
        InMemoryStageState state = new InMemoryStageState();

        WorldAwakenedStageMutationResult result = WorldAwakenedStageStateEngine.unlockStage(
                registry,
                state,
                id("testpack:starter"),
                "test",
                42L);

        assertEquals(WorldAwakenedStageMutationStatus.UNLOCKED, result.status());
        assertTrue(state.unlockedStages().contains(id("testpack:baseline")));
        assertFalse(state.unlockedStages().contains(id("testpack:starter")));
        assertEquals(42L, state.unlockTimestamps().get(id("testpack:baseline")));
        assertTrue(state.dirty);
    }

    @Test
    void exclusiveGroupBlocksSecondUnlock() {
        StageDefinition first = stage(
                "testpack:alpha",
                List.of(),
                0,
                Optional.of("main"),
                StageUnlockPolicy.EXCLUSIVE_GROUP);
        StageDefinition second = stage(
                "testpack:beta",
                List.of(),
                1,
                Optional.of("main"),
                StageUnlockPolicy.EXCLUSIVE_GROUP);
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(Map.of(
                first.id(), first,
                second.id(), second));
        InMemoryStageState state = new InMemoryStageState();

        WorldAwakenedStageMutationResult firstUnlock = WorldAwakenedStageStateEngine.unlockStage(
                registry,
                state,
                first.id(),
                "test",
                1L);
        WorldAwakenedStageMutationResult secondUnlock = WorldAwakenedStageStateEngine.unlockStage(
                registry,
                state,
                second.id(),
                "test",
                2L);

        assertEquals(WorldAwakenedStageMutationStatus.UNLOCKED, firstUnlock.status());
        assertEquals(WorldAwakenedStageMutationStatus.BLOCKED, secondUnlock.status());
        assertTrue(state.unlockedStages().contains(first.id()));
        assertFalse(state.unlockedStages().contains(second.id()));
    }

    @Test
    void replaceGroupUnlockReplacesPreviousStage() {
        StageDefinition first = stage(
                "testpack:phase_one",
                List.of(),
                0,
                Optional.of("main"),
                StageUnlockPolicy.REPLACE_GROUP);
        StageDefinition second = stage(
                "testpack:phase_two",
                List.of(),
                2,
                Optional.of("main"),
                StageUnlockPolicy.REPLACE_GROUP);
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(Map.of(
                first.id(), first,
                second.id(), second));
        InMemoryStageState state = new InMemoryStageState();

        WorldAwakenedStageStateEngine.unlockStage(registry, state, first.id(), "test", 1L);
        WorldAwakenedStageMutationResult secondUnlock = WorldAwakenedStageStateEngine.unlockStage(registry, state, second.id(), "test", 2L);

        assertEquals(WorldAwakenedStageMutationStatus.UNLOCKED, secondUnlock.status());
        assertEquals(Optional.of(first.id()), secondUnlock.replacedStageId());
        assertFalse(state.unlockedStages().contains(first.id()));
        assertTrue(state.unlockedStages().contains(second.id()));
        assertEquals(second.id(), state.activeGroupStages().get("main"));
    }

    @Test
    void lockRespectsRegressionGate() {
        StageDefinition stage = stage(
                "testpack:baseline",
                List.of(),
                0,
                Optional.empty(),
                StageUnlockPolicy.CUMULATIVE);
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(Map.of(stage.id(), stage));
        InMemoryStageState state = new InMemoryStageState();
        WorldAwakenedStageStateEngine.unlockStage(registry, state, stage.id(), "test", 1L);

        WorldAwakenedStageMutationResult blocked = WorldAwakenedStageStateEngine.lockStage(
                registry,
                state,
                stage.id(),
                false);
        WorldAwakenedStageMutationResult locked = WorldAwakenedStageStateEngine.lockStage(
                registry,
                state,
                stage.id(),
                true);

        assertEquals(WorldAwakenedStageMutationStatus.BLOCKED, blocked.status());
        assertEquals(WorldAwakenedStageMutationStatus.LOCKED, locked.status());
        assertFalse(state.unlockedStages().contains(stage.id()));
    }

    private static StageDefinition stage(
            String id,
            List<String> aliases,
            int sortIndex,
            Optional<String> group,
            StageUnlockPolicy policy) {
        List<ResourceLocation> aliasIds = aliases.stream().map(WorldAwakenedStageStateEngineTest::id).toList();
        return new StageDefinition(
                1,
                id(id),
                aliasIds,
                JsonParser.parseString("\"" + id + "\""),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                sortIndex,
                true,
                true,
                List.of(),
                Optional.empty(),
                group,
                policy,
                false);
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }

    private static final class InMemoryStageState implements WorldAwakenedMutableStageState {
        private final Set<ResourceLocation> unlockedStages = new LinkedHashSet<>();
        private final Map<ResourceLocation, Long> unlockTimestamps = new LinkedHashMap<>();
        private final Map<ResourceLocation, String> unlockSources = new LinkedHashMap<>();
        private final Map<String, ResourceLocation> activeGroupStages = new LinkedHashMap<>();
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
        public void markDirty() {
            dirty = true;
        }
    }
}

