package net.sprocketgames.worldawakened.ascension;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.sprocketgames.worldawakened.progression.event.WorldAwakenedStageUnlockedEvent;

public final class WorldAwakenedAscensionEventHandlers {
    private final WorldAwakenedAscensionService ascensionService;

    public WorldAwakenedAscensionEventHandlers(WorldAwakenedAscensionService ascensionService) {
        this.ascensionService = ascensionService;
    }

    public void onStageUnlocked(WorldAwakenedStageUnlockedEvent event) {
        ascensionService.grantEligibleOffersForStageUnlock(event.level(), event.player(), event.stageId());
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ascensionService.reconcilePlayerRewards(player.serverLevel(), player, "player_login");
        }
    }

    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ascensionService.reconcilePlayerRewards(player.serverLevel(), player, "player_respawn");
        }
    }
}
