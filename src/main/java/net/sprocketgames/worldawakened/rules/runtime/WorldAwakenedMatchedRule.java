package net.sprocketgames.worldawakened.rules.runtime;

import net.sprocketgames.worldawakened.data.definition.ExecutionScope;

public record WorldAwakenedMatchedRule(
        WorldAwakenedRuleEngine.CompiledRule rule,
        ExecutionScope resolvedScope,
        String stateKey) {
}
