package net.sprocketgames.worldawakened.rules.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldAwakenedCanonicalPipelineTest {
    @Test
    void preservesConfiguredOrder() {
        assertTrue(WorldAwakenedCanonicalPipeline.isOrdered(WorldAwakenedPipelineStep.TRIGGERS, WorldAwakenedPipelineStep.LOOT_ACTIONS));
        assertFalse(WorldAwakenedCanonicalPipeline.isOrdered(WorldAwakenedPipelineStep.INVASION_SCHEDULING, WorldAwakenedPipelineStep.TRIGGERS));
    }
}

