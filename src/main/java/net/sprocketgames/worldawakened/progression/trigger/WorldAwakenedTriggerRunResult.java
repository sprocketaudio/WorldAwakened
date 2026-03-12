package net.sprocketgames.worldawakened.progression.trigger;

public record WorldAwakenedTriggerRunResult(
        String traceId,
        int evaluatedRules,
        int matchedRules,
        int executedRules,
        int stageUnlocks,
        int stageLocks,
        int emittedEvents,
        int counterUpdates,
        int evaluatedGenericRules,
        int matchedGenericRules,
        int executedGenericRules,
        int genericRuleStageUnlocks,
        int genericRuleStageLocks) {
    public static WorldAwakenedTriggerRunResult empty() {
        return new WorldAwakenedTriggerRunResult("none", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
