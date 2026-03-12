package net.sprocketgames.worldawakened.rules.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorldAwakenedConflictResolutionTest {
    @Test
    void sortsByPriorityDescending() {
        List<TestPriority> input = List.of(new TestPriority(1), new TestPriority(10), new TestPriority(5));
        List<TestPriority> sorted = WorldAwakenedConflictResolution.sortByPriorityDesc(input);
        assertEquals(10, sorted.get(0).priority());
        assertEquals(5, sorted.get(1).priority());
        assertEquals(1, sorted.get(2).priority());
    }

    private record TestPriority(int priority) implements WorldAwakenedConflictResolution.Prioritized {
    }
}

