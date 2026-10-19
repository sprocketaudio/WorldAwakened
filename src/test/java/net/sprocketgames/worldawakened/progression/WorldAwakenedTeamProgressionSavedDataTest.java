package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class WorldAwakenedTeamProgressionSavedDataTest {
    @Test
    void teamPhaseStatePersistsIndependentlyOfPlayerAscensionData() {
        WorldAwakenedTeamProgressionSavedData data = new WorldAwakenedTeamProgressionSavedData();
        data.getOrCreate("ftb:alpha").unlockedStages().add(ResourceLocation.withDefaultNamespace("the_nether"));
        data.getOrCreate("ftb:alpha").markDirty();

        WorldAwakenedTeamProgressionSavedData restored = WorldAwakenedTeamProgressionSavedData.load(
                data.save(new CompoundTag(), null), null);
        assertTrue(restored.hasHistory());
        assertTrue(restored.getOrCreate("ftb:alpha").unlockedStages()
                .contains(ResourceLocation.withDefaultNamespace("the_nether")));
    }
}
