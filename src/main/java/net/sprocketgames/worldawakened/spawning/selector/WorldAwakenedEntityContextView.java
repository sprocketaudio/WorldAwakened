package net.sprocketgames.worldawakened.spawning.selector;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public interface WorldAwakenedEntityContextView {
    ResourceLocation entityId();

    Set<ResourceLocation> entityTags();

    String mobCategory();
}

