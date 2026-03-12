package net.sprocketgames.worldawakened.progression;

import java.util.Map;
import java.util.Set;

public interface WorldAwakenedMutableTriggerState {
    Map<String, Long> triggerCooldowns();

    Set<String> consumedOneShotTriggers();

    Map<String, Integer> triggerCounters();

    void markDirty();
}
