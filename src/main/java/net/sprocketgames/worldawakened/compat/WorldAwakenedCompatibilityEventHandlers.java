package net.sprocketgames.worldawakened.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;

public final class WorldAwakenedCompatibilityEventHandlers {
    private final WorldAwakenedCompatibilityService compatibilityService;
    private final WorldAwakenedStageService stageService;

    public WorldAwakenedCompatibilityEventHandlers(
            WorldAwakenedCompatibilityService compatibilityService,
            WorldAwakenedStageService stageService) {
        this.compatibilityService = compatibilityService;
        this.stageService = stageService;
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            compatibilityService.reconcileApotheosisDerivedStages(
                    player.serverLevel(),
                    player,
                    stageService,
                    "player_login");
        }
    }

    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            compatibilityService.reconcileApotheosisDerivedStages(
                    player.serverLevel(),
                    player,
                    stageService,
                    "player_respawn");
        }
    }

    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            compatibilityService.onLevelTick(level, stageService);
        }
    }
}
