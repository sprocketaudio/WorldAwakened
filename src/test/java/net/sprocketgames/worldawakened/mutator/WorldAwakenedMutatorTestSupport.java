package net.sprocketgames.worldawakened.mutator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import net.sprocketgames.worldawakened.config.WorldAwakenedConfigService;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorSettings;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

/**
 * Spawn-focused unit tests do not create an attributable player. They exercise
 * the server-wide mutation pipeline deliberately, rather than the separate
 * per-player spawn-attribution policy.
 */
abstract class WorldAwakenedMutatorTestSupport {
    private WorldAwakenedConfigService.PreviewSettingsScope settingsScope;

    @BeforeEach
    void useServerWideMutationContext() {
        WorldAwakenedEditorSettings defaults = WorldAwakenedEditorSettings.builtInDefaults();
        settingsScope = WorldAwakenedConfigService.pushPreviewSettings(new WorldAwakenedEditorSettings(
                defaults.defaultGameplayEnabled(),
                defaults.difficultyIntensity(),
                defaults.mutationsEnabled(),
                defaults.mutationFrequencyMultiplier(),
                defaults.allowMultipleMutations(),
                defaults.maximumMutationsPerMob(),
                defaults.pressureEventsEnabled(),
                defaults.eventFrequencyMultiplier(),
                defaults.ascensionEnabled(),
                WorldAwakenedProgressionMode.PER_SERVER));
    }

    @AfterEach
    void clearMutationTestSettings() {
        if (settingsScope != null) {
            settingsScope.close();
            settingsScope = null;
        }
    }
}
