package net.sprocketgames.worldawakened.rules.runtime;

import java.util.List;

public record WorldAwakenedRuleEvaluation(
        int evaluatedRules,
        List<WorldAwakenedRuleDecision> decisions,
        List<WorldAwakenedMatchedRule> matchedRules) {
    public int matchedCount() {
        return matchedRules.size();
    }
}
