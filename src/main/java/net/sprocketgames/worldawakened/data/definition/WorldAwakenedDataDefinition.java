package net.sprocketgames.worldawakened.data.definition;

import net.minecraft.resources.ResourceLocation;

public interface WorldAwakenedDataDefinition {
    int schemaVersion();

    ResourceLocation id();

    boolean enabled();
}

