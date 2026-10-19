package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

class WorldAwakenedConfigSavedDataTest {
    @Test
    void persistsSettingsAndMonotonicRevision() {
        WorldAwakenedConfigSavedData data = new WorldAwakenedConfigSavedData();
        WorldAwakenedEditorSettings defaults = WorldAwakenedEditorSettings.builtInDefaults();
        data.initialize(defaults);
        WorldAwakenedEditorSettings changed = new WorldAwakenedEditorSettings(
                false, 1.25D, false, 0.5D, false, 4, false, 2.0D, false);
        data.apply(changed);

        CompoundTag saved = data.save(new CompoundTag(), null);
        WorldAwakenedConfigSavedData restored = WorldAwakenedConfigSavedData.fromTag(saved);

        assertTrue(restored.initialized());
        assertEquals(2L, restored.revision());
        assertEquals(changed, restored.settings());
    }

    @Test
    void legacySavedConfigKeepsGlobalModeUntilOperatorChangesIt() {
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("initialized", true);
        WorldAwakenedConfigSavedData restored = WorldAwakenedConfigSavedData.fromTag(legacy);
        assertEquals(WorldAwakenedProgressionMode.PER_PLAYER, restored.settings().progressionMode());
    }
}
