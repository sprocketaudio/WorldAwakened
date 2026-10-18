package net.sprocketgames.worldawakened.progression.trigger;

import java.util.List;
import java.util.Map;

/**
 * Persistent flat AND/OR progress used by native-editor-authored phase unlocks.
 * The editor supplies stable group and condition tokens; changing an authored
 * group therefore cannot accidentally reuse completion from an older group.
 */
public final class WorldAwakenedUnlockConditionProgress {
    private static final String COUNTER_PREFIX = "worldawakened:phase_condition/";

    private WorldAwakenedUnlockConditionProgress() {
    }

    public static Result record(
            Map<String, Integer> counters,
            String groupToken,
            String conditionToken,
            List<String> conditionTokens,
            boolean requireAll) {
        if (groupToken == null || groupToken.isBlank()
                || conditionToken == null || conditionToken.isBlank()
                || conditionTokens == null || conditionTokens.isEmpty()
                || !conditionTokens.contains(conditionToken)) {
            return new Result(false, false);
        }

        String key = key(groupToken, conditionToken);
        boolean newlyRecorded = counters.putIfAbsent(key, 1) == null;
        boolean satisfied = requireAll
                ? conditionTokens.stream().allMatch(token -> counters.getOrDefault(key(groupToken, token), 0) > 0)
                : conditionTokens.stream().anyMatch(token -> counters.getOrDefault(key(groupToken, token), 0) > 0);
        return new Result(newlyRecorded, satisfied);
    }

    static String key(String groupToken, String conditionToken) {
        return COUNTER_PREFIX + groupToken + "/" + conditionToken;
    }

    public record Result(boolean newlyRecorded, boolean satisfied) {
    }
}
