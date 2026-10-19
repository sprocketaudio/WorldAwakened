package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

class WorldAwakenedEditorSettingsTest {
    @Test
    void builtInDefaultsRoundTripThroughNetworkJson() {
        WorldAwakenedEditorSettings defaults = WorldAwakenedEditorSettings.builtInDefaults();

        WorldAwakenedEditorSettings decoded = WorldAwakenedEditorSettings.fromJson(defaults.toJson());

        assertEquals(defaults, decoded);
        assertEquals(2, decoded.effectiveMutationCap());
        assertEquals(WorldAwakenedProgressionMode.PER_PLAYER, decoded.progressionMode());
    }

    @Test
    void disablingMultipleMutationsForcesEffectiveCapToOne() {
        WorldAwakenedEditorSettings settings = new WorldAwakenedEditorSettings(
                true, 1.0D, true, 1.0D, false, 8, true, 1.0D, true);

        assertEquals(1, settings.effectiveMutationCap());
        assertTrue(settings.validationErrors(0.75D, 1.5D).isEmpty());
    }

    @Test
    void rejectsOutOfRangeServerDraftValues() {
        WorldAwakenedEditorSettings invalid = new WorldAwakenedEditorSettings(
                true, 2.0D, true, 5.0D, true, 9, true, 0.1D, true);

        var errors = invalid.validationErrors(0.75D, 1.5D);

        assertFalse(errors.isEmpty());
        assertEquals(4, errors.size());
    }
}
