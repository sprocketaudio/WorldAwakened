package net.sprocketgames.worldawakened.rules.runtime;

import java.util.Map;
import java.util.Set;

public record WorldAwakenedRuleStateSnapshot(
        Map<String, Long> cooldowns,
        Set<String> consumedRules) {
    public static WorldAwakenedRuleStateSnapshot empty() {
        return new WorldAwakenedRuleStateSnapshot(Map.of(), Set.of());
    }
}
