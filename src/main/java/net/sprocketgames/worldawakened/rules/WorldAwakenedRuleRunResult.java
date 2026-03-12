package net.sprocketgames.worldawakened.rules;

public record WorldAwakenedRuleRunResult(
        String traceId,
        int evaluatedRules,
        int matchedRules,
        int executedRules,
        int stageUnlocks,
        int stageLocks,
        int warningsSent,
        int markedConsumed,
        int deferredActions) {
    public static WorldAwakenedRuleRunResult empty(String traceId) {
        return new WorldAwakenedRuleRunResult(traceId, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
