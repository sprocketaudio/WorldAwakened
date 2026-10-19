package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorldAwakenedProgressionOwnerTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void perServerAlwaysUsesOneSharedOwner() {
        assertEquals(new WorldAwakenedProgressionOwner(WorldAwakenedProgressionOwner.Kind.SERVER, "server"),
                WorldAwakenedProgressionOwner.resolve(WorldAwakenedProgressionMode.PER_SERVER, A, Optional.of("alpha")));
    }

    @Test
    void perPlayerUsesIndividualOwnerWithoutFtbTeams() {
        assertEquals(new WorldAwakenedProgressionOwner(WorldAwakenedProgressionOwner.Kind.PLAYER, A.toString()),
                WorldAwakenedProgressionOwner.resolve(WorldAwakenedProgressionMode.PER_PLAYER, A, Optional.empty()));
    }

    @Test
    void teamMembersShareWhileDifferentTeamsRemainIndependent() {
        WorldAwakenedProgressionOwner alphaA = WorldAwakenedProgressionOwner.resolve(
                WorldAwakenedProgressionMode.PER_PLAYER, A, Optional.of("alpha"));
        WorldAwakenedProgressionOwner alphaB = WorldAwakenedProgressionOwner.resolve(
                WorldAwakenedProgressionMode.PER_PLAYER, B, Optional.of("alpha"));
        WorldAwakenedProgressionOwner beta = WorldAwakenedProgressionOwner.resolve(
                WorldAwakenedProgressionMode.PER_PLAYER, B, Optional.of("beta"));
        assertEquals(alphaA, alphaB);
        assertEquals(WorldAwakenedProgressionOwner.Kind.FTB_TEAM, alphaA.kind());
        org.junit.jupiter.api.Assertions.assertNotEquals(alphaA, beta);
    }

    @Test
    void leavingTeamReturnsToPreservedSoloOwner() {
        WorldAwakenedProgressionOwner solo = WorldAwakenedProgressionOwner.resolve(
                WorldAwakenedProgressionMode.PER_PLAYER, A, Optional.empty());
        WorldAwakenedProgressionOwner team = WorldAwakenedProgressionOwner.resolve(
                WorldAwakenedProgressionMode.PER_PLAYER, A, Optional.of("alpha"));
        org.junit.jupiter.api.Assertions.assertNotEquals(solo, team);
        assertEquals(solo, WorldAwakenedProgressionOwner.resolve(
                WorldAwakenedProgressionMode.PER_PLAYER, A, Optional.empty()));
    }
}
