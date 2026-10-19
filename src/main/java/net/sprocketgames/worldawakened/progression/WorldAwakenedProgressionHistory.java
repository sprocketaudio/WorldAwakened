package net.sprocketgames.worldawakened.progression;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Distinguishes automatic starting-state initialisation from meaningful progression. */
public final class WorldAwakenedProgressionHistory {
    private WorldAwakenedProgressionHistory() {
    }

    /**
     * A default-unlocked phase alone is safe to migrate because it is recreated for each
     * progression owner. Any later phase, partial trigger progress, consumed one-shot,
     * or rule state is owner-specific and must not be silently reassigned.
     */
    public static boolean hasMeaningfulHistory(
            WorldAwakenedProgressionState state,
            Set<ResourceLocation> defaultStageIds) {
        if (state == null) {
            return false;
        }
        Set<ResourceLocation> defaults = defaultStageIds == null ? Set.of() : defaultStageIds;
        return state.unlockedStages().stream().anyMatch(stageId -> !defaults.contains(stageId))
                || state.activeGroupStages().values().stream().anyMatch(stageId -> !defaults.contains(stageId))
                || !state.triggerCooldowns().isEmpty()
                || !state.consumedOneShotTriggers().isEmpty()
                || !state.triggerCounters().isEmpty()
                || !state.ruleCooldowns().isEmpty()
                || !state.consumedRules().isEmpty();
    }
}
