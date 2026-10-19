package net.sprocketgames.worldawakened.progression;

import java.util.Locale;
import java.util.Optional;

public enum WorldAwakenedProgressionMode {
    PER_SERVER("per_server"),
    PER_PLAYER("per_player");

    private final String serializedName;

    WorldAwakenedProgressionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    /** Parses only the current public configuration identifiers. */
    public static Optional<WorldAwakenedProgressionMode> parse(String rawMode) {
        if (rawMode == null) return Optional.empty();
        return switch (rawMode.trim().toLowerCase(Locale.ROOT)) {
            case "per_server" -> Optional.of(PER_SERVER);
            case "per_player" -> Optional.of(PER_PLAYER);
            default -> Optional.empty();
        };
    }
}

