package net.sprocketgames.worldawakened.progression;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the persisted owner of phase, trigger and rule progress. Ascension deliberately
 * remains on the player's UUID-owned data and must not use this type.
 */
public record WorldAwakenedProgressionOwner(Kind kind, String key) {
    public enum Kind { SERVER, PLAYER, FTB_TEAM }

    public static WorldAwakenedProgressionOwner resolve(
            WorldAwakenedProgressionMode mode,
            UUID playerId,
            Optional<String> ftbTeamId) {
        if (mode != WorldAwakenedProgressionMode.PER_PLAYER || playerId == null) {
            return new WorldAwakenedProgressionOwner(Kind.SERVER, "server");
        }
        Optional<String> team = ftbTeamId.filter(value -> !value.isBlank());
        if (team.isPresent()) {
            return new WorldAwakenedProgressionOwner(Kind.FTB_TEAM, "ftb:" + team.get());
        }
        return new WorldAwakenedProgressionOwner(Kind.PLAYER, playerId.toString());
    }
}
