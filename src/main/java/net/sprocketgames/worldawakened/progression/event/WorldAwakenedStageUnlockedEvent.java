package net.sprocketgames.worldawakened.progression.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

public final class WorldAwakenedStageUnlockedEvent extends Event {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final ResourceLocation stageId;
    private final String source;
    private final WorldAwakenedProgressionMode configuredMode;
    private final WorldAwakenedProgressionMode effectiveMode;
    private final boolean usedWorldFallback;

    public WorldAwakenedStageUnlockedEvent(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation stageId,
            String source,
            WorldAwakenedProgressionMode configuredMode,
            WorldAwakenedProgressionMode effectiveMode,
            boolean usedWorldFallback) {
        this.level = level;
        this.player = player;
        this.stageId = stageId;
        this.source = source;
        this.configuredMode = configuredMode;
        this.effectiveMode = effectiveMode;
        this.usedWorldFallback = usedWorldFallback;
    }

    public ServerLevel level() {
        return level;
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceLocation stageId() {
        return stageId;
    }

    public String source() {
        return source;
    }

    public WorldAwakenedProgressionMode configuredMode() {
        return configuredMode;
    }

    public WorldAwakenedProgressionMode effectiveMode() {
        return effectiveMode;
    }

    public boolean usedWorldFallback() {
        return usedWorldFallback;
    }
}

