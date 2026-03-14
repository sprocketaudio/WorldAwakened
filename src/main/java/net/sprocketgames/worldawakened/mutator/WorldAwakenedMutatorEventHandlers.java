package net.sprocketgames.worldawakened.mutator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.network.WorldAwakenedNetwork;

public final class WorldAwakenedMutatorEventHandlers {
    private final WorldAwakenedMutatorService mutatorService;

    public WorldAwakenedMutatorEventHandlers(WorldAwakenedMutatorService mutatorService) {
        this.mutatorService = mutatorService;
    }

    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_MUTATORS.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        WorldAwakenedMutationProvenance.markPendingSpawnOrigin(
                mob.getPersistentData(),
                WorldAwakenedMobSpawnOrigin.fromMobSpawnType(event.getSpawnType()));
    }

    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_MUTATORS.get()) {
            return;
        }
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !mob.isAlive()) {
            return;
        }
        if (!event.loadedFromDisk()) {
            WorldAwakenedMobSpawnOrigin spawnOrigin =
                    WorldAwakenedMutationProvenance.consumePendingSpawnOrigin(mob.getPersistentData());
            mutatorService.onMobSpawn(level, mob, spawnOrigin);
        }
        // Always re-sync visual state when a mob enters a tracked level, including disk-loaded entities.
        WorldAwakenedNetwork.sendGlowStyleStateToTracking(mob);
    }

    public void onEntityTickPost(EntityTickEvent.Post event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_MUTATORS.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !mob.isAlive()) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        mutatorService.onMobTick(level, mob);
    }

    public void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_MUTATORS.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof Mob mob) || !mob.isAlive()) {
            return;
        }
        WorldAwakenedNetwork.sendGlowStyleState(player, mob);
    }
}
