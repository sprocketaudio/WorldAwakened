package net.sprocketgames.worldawakened.progression;

import java.util.Locale;

public enum WorldAwakenedProgressionMode {
    GLOBAL("global"),
    PER_PLAYER("per_player"),
    HYBRID("hybrid");

    private final String serializedName;

    WorldAwakenedProgressionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorldAwakenedProgressionMode fromConfig(String rawMode) {
        if (rawMode == null) {
            return GLOBAL;
        }

        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "global" -> GLOBAL;
            case "per_player", "per-player", "perplayer", "player" -> PER_PLAYER;
            case "hybrid" -> HYBRID;
            default -> GLOBAL;
        };
    }
}

