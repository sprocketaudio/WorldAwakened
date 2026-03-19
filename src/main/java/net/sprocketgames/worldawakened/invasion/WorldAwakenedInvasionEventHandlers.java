package net.sprocketgames.worldawakened.invasion;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class WorldAwakenedInvasionEventHandlers {
    private final WorldAwakenedInvasionService invasionService;

    public WorldAwakenedInvasionEventHandlers(WorldAwakenedInvasionService invasionService) {
        this.invasionService = invasionService;
    }

    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        invasionService.onLevelTick(level);
    }
}
