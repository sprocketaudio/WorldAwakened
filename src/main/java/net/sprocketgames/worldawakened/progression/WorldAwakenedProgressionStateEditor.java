package net.sprocketgames.worldawakened.progression;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedProgressionStateEditor {
    private WorldAwakenedProgressionStateEditor() {
    }

    public static StageResetSummary resetStages(WorldAwakenedMutableStageState state) {
        int unlocked = state.unlockedStages().size();
        state.unlockedStages().clear();
        state.unlockTimestamps().clear();
        state.unlockSources().clear();
        state.activeGroupStages().clear();
        if (unlocked > 0) {
            state.markDirty();
        }
        return new StageResetSummary(unlocked);
    }

    public static WorldAwakenedStageMutationResult clearStage(
            WorldAwakenedStageRegistry registry,
            WorldAwakenedMutableStageState state,
            ResourceLocation stageId) {
        return WorldAwakenedStageStateEngine.lockStage(registry, state, stageId, true);
    }

    public static TriggerResetSummary resetTriggers(WorldAwakenedMutableTriggerState state) {
        int cooldowns = state.triggerCooldowns().size();
        int consumed = state.consumedOneShotTriggers().size();
        int counters = state.triggerCounters().size();
        state.triggerCooldowns().clear();
        state.consumedOneShotTriggers().clear();
        state.triggerCounters().clear();
        if (cooldowns > 0 || consumed > 0 || counters > 0) {
            state.markDirty();
        }
        return new TriggerResetSummary(cooldowns, consumed, counters);
    }

    public static boolean clearTrigger(WorldAwakenedMutableTriggerState state, ResourceLocation triggerId) {
        String key = triggerId.toString();
        boolean changed = state.triggerCooldowns().remove(key) != null;
        changed |= state.consumedOneShotTriggers().remove(key);
        changed |= state.triggerCounters().remove(key) != null;
        if (changed) {
            state.markDirty();
        }
        return changed;
    }

    public static RuleResetSummary resetRules(WorldAwakenedMutableRuleState state) {
        int cooldowns = state.ruleCooldowns().size();
        int consumed = state.consumedRules().size();
        state.ruleCooldowns().clear();
        state.consumedRules().clear();
        if (cooldowns > 0 || consumed > 0) {
            state.markDirty();
        }
        return new RuleResetSummary(cooldowns, consumed);
    }

    public static boolean clearRule(WorldAwakenedMutableRuleState state, ResourceLocation ruleId) {
        String key = ruleId.toString();
        boolean changed = state.ruleCooldowns().remove(key) != null;
        changed |= state.consumedRules().remove(key);
        if (changed) {
            state.markDirty();
        }
        return changed;
    }

    public record StageResetSummary(int clearedStages) {
    }

    public record TriggerResetSummary(int clearedCooldowns, int clearedConsumed, int clearedCounters) {
        public int totalCleared() {
            return clearedCooldowns + clearedConsumed + clearedCounters;
        }
    }

    public record RuleResetSummary(int clearedCooldowns, int clearedConsumed) {
        public int totalCleared() {
            return clearedCooldowns + clearedConsumed;
        }
    }
}
