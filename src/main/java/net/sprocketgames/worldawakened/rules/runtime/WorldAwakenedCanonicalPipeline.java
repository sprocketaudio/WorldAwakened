package net.sprocketgames.worldawakened.rules.runtime;

import java.util.List;

public final class WorldAwakenedCanonicalPipeline {
    private static final List<WorldAwakenedPipelineStep> ORDER = List.of(
            WorldAwakenedPipelineStep.TRIGGERS,
            WorldAwakenedPipelineStep.STAGE_ACTIONS,
            WorldAwakenedPipelineStep.SCALAR_AND_STATE_ACTIONS,
            WorldAwakenedPipelineStep.SPAWN_AND_MUTATOR_ACTIONS,
            WorldAwakenedPipelineStep.LOOT_ACTIONS,
            WorldAwakenedPipelineStep.INVASION_SCHEDULING,
            WorldAwakenedPipelineStep.NOTIFICATIONS_AND_DEBUG);

    private WorldAwakenedCanonicalPipeline() {
    }

    public static List<WorldAwakenedPipelineStep> order() {
        return ORDER;
    }

    public static boolean isOrdered(WorldAwakenedPipelineStep current, WorldAwakenedPipelineStep next) {
        return ORDER.indexOf(current) <= ORDER.indexOf(next);
    }
}

