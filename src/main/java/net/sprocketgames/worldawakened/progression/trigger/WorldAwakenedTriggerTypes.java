package net.sprocketgames.worldawakened.progression.trigger;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedTriggerTypes {
    public static final ResourceLocation PLAYER_ENTER_DIMENSION = id("player_enter_dimension");
    public static final ResourceLocation ADVANCEMENT_COMPLETED = id("advancement_completed");
    public static final ResourceLocation ENTITY_KILLED = id("entity_killed");
    public static final ResourceLocation BOSS_KILLED = id("boss_killed");
    public static final ResourceLocation ITEM_CRAFTED = id("item_crafted");
    public static final ResourceLocation BLOCK_PLACED = id("block_placed");
    public static final ResourceLocation BLOCK_BROKEN = id("block_broken");
    public static final ResourceLocation MANUAL_DEBUG = id("manual_debug");

    private WorldAwakenedTriggerTypes() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("worldawakened", path);
    }
}
