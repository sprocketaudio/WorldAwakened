package net.sprocketgames.worldawakened;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedConstants {
    public static final String MOD_ID = "worldawakened";
    public static final String MOD_NAME = "World Awakened";

    private WorldAwakenedConstants() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

