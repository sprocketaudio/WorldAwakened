package net.sprocketgames.worldawakened.progression.trigger;

import java.util.Map;
import java.util.Set;

public record WorldAwakenedTriggerStateSnapshot(
        Map<String, Long> cooldowns,
        Set<String> consumedOneShotRules) {
    public static WorldAwakenedTriggerStateSnapshot empty() {
        return new WorldAwakenedTriggerStateSnapshot(Map.of(), Set.of());
    }
}
