package net.sprocketgames.worldawakened.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutatorService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

class WorldAwakenedCommandsMutatorDebugFormattingTest {
    @Test
    void formatMutatorSpawnContextIncludesAttributedPlayerWhenPresent() {
        String formatted = WorldAwakenedCommands.formatMutatorSpawnContext(
                "spawner",
                WorldAwakenedProgressionMode.PER_PLAYER,
                Optional.of(new WorldAwakenedMutatorService.AttributedPlayerView(
                        "BetaTester",
                        "12345678-1234-1234-1234-123456789012")));

        assertEquals(
                "origin=spawner progression_mode=per_player attributed_player=BetaTester(12345678-1234-1234-1234-123456789012)",
                formatted);
    }

    @Test
    void formatMutatorSpawnContextShowsNoAttributionWhenAbsent() {
        String formatted = WorldAwakenedCommands.formatMutatorSpawnContext(
                "other",
                WorldAwakenedProgressionMode.GLOBAL,
                Optional.empty());

        assertEquals(
                "origin=other progression_mode=global attributed_player=<none>",
                formatted);
    }

    @Test
    void formatMutationChanceResultShowsRolledOutcome() {
        String formatted = WorldAwakenedCommands.formatMutationChanceResult(
                Optional.of(new WorldAwakenedMutatorService.MutationChanceResult(
                        0.25D,
                        WorldAwakenedMutatorService.MutationChanceRollMode.ROLLED,
                        OptionalDouble.of(0.61D),
                        false)));

        assertEquals("mutation_chance=0.250 rolled=0.610 passed=false", formatted);
    }

    @Test
    void formatMutationChanceResultShowsBypassedOutcome() {
        String formatted = WorldAwakenedCommands.formatMutationChanceResult(
                Optional.of(new WorldAwakenedMutatorService.MutationChanceResult(
                        0.35D,
                        WorldAwakenedMutatorService.MutationChanceRollMode.BYPASSED,
                        OptionalDouble.empty(),
                        true)));

        assertEquals("mutation_chance=0.350 rolled=<bypassed> passed=true", formatted);
    }
}
