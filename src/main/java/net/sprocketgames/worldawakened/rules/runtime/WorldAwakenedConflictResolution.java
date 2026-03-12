package net.sprocketgames.worldawakened.rules.runtime;

import java.util.Comparator;
import java.util.List;

public final class WorldAwakenedConflictResolution {
    private WorldAwakenedConflictResolution() {
    }

    public static <T extends Prioritized> List<T> sortByPriorityDesc(List<T> items) {
        return items.stream().sorted(Comparator.comparingInt(Prioritized::priority).reversed()).toList();
    }

    public interface Prioritized {
        int priority();
    }
}

