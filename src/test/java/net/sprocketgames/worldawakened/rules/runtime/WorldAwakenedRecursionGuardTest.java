package net.sprocketgames.worldawakened.rules.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldAwakenedRecursionGuardTest {
    @Test
    void blocksRecursiveRuleEntry() {
        WorldAwakenedRecursionGuard guard = new WorldAwakenedRecursionGuard(4);
        assertTrue(guard.tryEnter("rule_a"));
        assertFalse(guard.tryEnter("rule_a"));
        guard.exit("rule_a");
        assertTrue(guard.tryEnter("rule_a"));
    }

    @Test
    void enforcesDepthCap() {
        WorldAwakenedRecursionGuard guard = new WorldAwakenedRecursionGuard(2);
        assertTrue(guard.tryEnter("rule_a"));
        assertTrue(guard.tryEnter("rule_b"));
        assertFalse(guard.tryEnter("rule_c"));
    }
}

