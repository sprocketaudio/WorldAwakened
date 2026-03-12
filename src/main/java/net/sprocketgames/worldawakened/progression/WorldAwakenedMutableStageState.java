package net.sprocketgames.worldawakened.progression;

import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public interface WorldAwakenedMutableStageState {
    Set<ResourceLocation> unlockedStages();

    Map<ResourceLocation, Long> unlockTimestamps();

    Map<ResourceLocation, String> unlockSources();

    Map<String, ResourceLocation> activeGroupStages();

    void markDirty();
}

