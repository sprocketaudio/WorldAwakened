package net.sprocketgames.worldawakened.progression.trigger;

import java.util.List;

import net.sprocketgames.worldawakened.data.definition.SourceScope;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;

public record WorldAwakenedTriggerMatchResult(
        int evaluatedRules,
        List<MatchedRule> matchedRules) {
    public int matchedCount() {
        return matchedRules.size();
    }

    public record MatchedRule(TriggerRuleDefinition rule, SourceScope effectiveScope) {
    }
}
