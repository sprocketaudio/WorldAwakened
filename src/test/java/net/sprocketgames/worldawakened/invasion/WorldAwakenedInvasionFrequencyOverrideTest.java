package net.sprocketgames.worldawakened.invasion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.sprocketgames.worldawakened.config.WorldAwakenedConfigService;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorSettings;

class WorldAwakenedInvasionFrequencyOverrideTest {
    @Test
    void eventFrequencyScalesFutureCooldowns() {
        WorldAwakenedEditorSettings defaults = WorldAwakenedEditorSettings.builtInDefaults();
        WorldAwakenedEditorSettings twiceAsFrequent = new WorldAwakenedEditorSettings(
                defaults.defaultGameplayEnabled(),
                defaults.difficultyIntensity(),
                defaults.mutationsEnabled(),
                defaults.mutationFrequencyMultiplier(),
                defaults.allowMultipleMutations(),
                defaults.maximumMutationsPerMob(),
                defaults.pressureEventsEnabled(),
                2.0D,
                defaults.ascensionEnabled());

        long scaled = WorldAwakenedConfigService.withPreviewSettings(
                twiceAsFrequent,
                () -> WorldAwakenedInvasionService.scaleCooldownMillis(90_000L));

        assertEquals(45_000L, scaled);
    }
}
