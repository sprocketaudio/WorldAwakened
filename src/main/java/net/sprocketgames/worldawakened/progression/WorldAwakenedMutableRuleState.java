package net.sprocketgames.worldawakened.progression;

import java.util.Map;
import java.util.Set;

public interface WorldAwakenedMutableRuleState {
    Map<String, Long> ruleCooldowns();

    Set<String> consumedRules();

    void markDirty();
}
