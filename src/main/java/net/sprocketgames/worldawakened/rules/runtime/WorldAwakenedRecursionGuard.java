package net.sprocketgames.worldawakened.rules.runtime;

import java.util.HashSet;
import java.util.Set;

public final class WorldAwakenedRecursionGuard {
    private final int maxDepth;
    private final Set<String> activeRuleIds = new HashSet<>();
    private int depth;

    public WorldAwakenedRecursionGuard(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public boolean tryEnter(String ruleId) {
        if (depth >= maxDepth) {
            return false;
        }
        if (!activeRuleIds.add(ruleId)) {
            return false;
        }
        depth++;
        return true;
    }

    public void exit(String ruleId) {
        if (activeRuleIds.remove(ruleId) && depth > 0) {
            depth--;
        }
    }

    public int depth() {
        return depth;
    }
}

