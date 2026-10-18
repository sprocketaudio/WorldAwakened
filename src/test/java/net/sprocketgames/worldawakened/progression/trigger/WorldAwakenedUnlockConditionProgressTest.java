package net.sprocketgames.worldawakened.progression.trigger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WorldAwakenedUnlockConditionProgressTest {
    @Test
    void andConditionsPersistAndCompleteInEitherOrder() {
        Map<String, Integer> counters = new LinkedHashMap<>();
        List<String> conditions = List.of("day", "nether");

        var nether = WorldAwakenedUnlockConditionProgress.record(
                counters, "group", "nether", conditions, true);
        assertTrue(nether.newlyRecorded());
        assertFalse(nether.satisfied());

        var day = WorldAwakenedUnlockConditionProgress.record(
                counters, "group", "day", conditions, true);
        assertTrue(day.newlyRecorded());
        assertTrue(day.satisfied());
    }

    @Test
    void orConditionCompletesImmediatelyAndDoesNotDoubleRecord() {
        Map<String, Integer> counters = new LinkedHashMap<>();
        List<String> conditions = List.of("day", "nether");

        assertTrue(WorldAwakenedUnlockConditionProgress.record(
                counters, "group", "day", conditions, false).satisfied());
        var replay = WorldAwakenedUnlockConditionProgress.record(
                counters, "group", "day", conditions, false);
        assertFalse(replay.newlyRecorded());
        assertTrue(replay.satisfied());
    }

    @Test
    void changedGroupTokenCannotReuseStaleCompletion() {
        Map<String, Integer> counters = new LinkedHashMap<>();
        WorldAwakenedUnlockConditionProgress.record(
                counters, "old", "day", List.of("day", "nether"), true);

        assertFalse(WorldAwakenedUnlockConditionProgress.record(
                counters, "new", "nether", List.of("day", "nether"), true).satisfied());
    }

    @Test
    void malformedMetadataFailsClosed() {
        Map<String, Integer> counters = new LinkedHashMap<>();
        var result = WorldAwakenedUnlockConditionProgress.record(
                counters, "group", "missing", List.of("day"), true);
        assertFalse(result.newlyRecorded());
        assertFalse(result.satisfied());
        assertTrue(counters.isEmpty());
    }
}
