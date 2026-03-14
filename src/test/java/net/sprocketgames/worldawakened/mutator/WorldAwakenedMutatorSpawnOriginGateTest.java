package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class WorldAwakenedMutatorSpawnOriginGateTest {
    @Test
    void rejectsSpawnerOriginsUnlessExplicitlyAllowed() {
        Optional<String> spawnerRejection =
                WorldAwakenedMutatorService.spawnOriginRejection(false, false, WorldAwakenedMobSpawnOrigin.SPAWNER);
        Optional<String> trialSpawnerRejection =
                WorldAwakenedMutatorService.spawnOriginRejection(false, false, WorldAwakenedMobSpawnOrigin.TRIAL_SPAWNER);

        assertTrue(spawnerRejection.isPresent());
        assertEquals("spawn origin is spawner but allow_from_spawner is false", spawnerRejection.get());
        assertTrue(trialSpawnerRejection.isPresent());
        assertEquals(
                "spawn origin is trial_spawner but allow_from_trial_spawner is false",
                trialSpawnerRejection.get());
    }

    @Test
    void allowsSpawnerOriginsWhenMatchingFlagIsEnabled() {
        assertTrue(WorldAwakenedMutatorService.spawnOriginRejection(true, false, WorldAwakenedMobSpawnOrigin.SPAWNER).isEmpty());
        assertTrue(
                WorldAwakenedMutatorService.spawnOriginRejection(false, true, WorldAwakenedMobSpawnOrigin.TRIAL_SPAWNER).isEmpty());
        assertTrue(WorldAwakenedMutatorService.spawnOriginRejection(false, false, WorldAwakenedMobSpawnOrigin.OTHER).isEmpty());
    }
}
