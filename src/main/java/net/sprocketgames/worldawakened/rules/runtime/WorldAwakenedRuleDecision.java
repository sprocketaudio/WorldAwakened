package net.sprocketgames.worldawakened.rules.runtime;

import java.util.Optional;
import java.util.OptionalDouble;

public record WorldAwakenedRuleDecision(
        WorldAwakenedRuleEngine.CompiledRule rule,
        boolean matched,
        Optional<WorldAwakenedRejectionReason> rejectionReason,
        String detail,
        String stateKey,
        OptionalDouble chanceRoll) {
}
