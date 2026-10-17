package net.sprocketgames.worldawakened.mutator;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.network.WorldAwakenedNetwork;

public final class WorldAwakenedMutatorEventHandlers {
    private static final ThreadLocal<Boolean> DEATH_EXPLOSION_ACTIVE = ThreadLocal.withInitial(() -> false);
    private final WorldAwakenedMutatorService mutatorService;
    private final Map<LivingEntity, Float> preDamageHealth = Collections.synchronizedMap(new WeakHashMap<>());

    public WorldAwakenedMutatorEventHandlers(WorldAwakenedMutatorService mutatorService) {
        this.mutatorService = mutatorService;
    }

    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedFeatureGates.mutatorsEnabled()) {
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
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedFeatureGates.mutatorsEnabled()) {
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
        mutatorService.reconcileMutationName(mob);
        // Always re-sync visual state when a mob enters a tracked level, including disk-loaded entities.
        WorldAwakenedNetwork.sendGlowStyleStateToTracking(mob);
    }

    public void onEntityTickPost(EntityTickEvent.Post event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedFeatureGates.mutatorsEnabled()) {
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

    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedFeatureGates.mutatorsEnabled()) {
            return;
        }
        Mob attacker = attackingMob(event.getSource());
        if (attacker == null || !WorldAwakenedMutationCombatState.hasDamageDealtAbility(attacker.getPersistentData())) {
            return;
        }
        preDamageHealth.put(event.getEntity(), Math.max(0.0F, event.getEntity().getHealth()));
    }

    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Float healthBeforeDamage = preDamageHealth.remove(target);
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedFeatureGates.mutatorsEnabled()) {
            return;
        }
        Mob attacker = attackingMob(event.getSource());
        if (attacker == null || !attacker.isAlive()) {
            return;
        }
        if (target.level().isClientSide() || event.getNewDamage() <= 0.0F) {
            return;
        }
        mutatorService.onMobDamageDealt(attacker, target, creditedDamage(event.getNewDamage(), healthBeforeDamage));
    }

    static float creditedDamage(float finalDamage, Float healthBeforeDamage) {
        float nonNegativeDamage = Math.max(0.0F, finalDamage);
        return healthBeforeDamage == null
                ? nonNegativeDamage
                : Math.min(nonNegativeDamage, Math.max(0.0F, healthBeforeDamage));
    }

    static Mob attackingMob(DamageSource source) {
        return source.getEntity() instanceof Mob mob ? mob : null;
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (!WorldAwakenedFeatureGates.modEnabled()
                || !WorldAwakenedFeatureGates.mutatorsEnabled()
                || event.isCanceled()
                || DEATH_EXPLOSION_ACTIVE.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        var power = mutatorService.consumeDeathExplosionPower(mob);
        if (power.isEmpty()) {
            return;
        }
        runWithDeathExplosionGuard(
                () -> level.explode(mob, mob.getX(), mob.getY(), mob.getZ(), (float) power.getAsDouble(), Level.ExplosionInteraction.NONE));
    }

    static boolean runWithDeathExplosionGuard(Runnable explosion) {
        if (DEATH_EXPLOSION_ACTIVE.get()) {
            return false;
        }
        DEATH_EXPLOSION_ACTIVE.set(true);
        try {
            explosion.run();
            return true;
        } finally {
            DEATH_EXPLOSION_ACTIVE.remove();
        }
    }

    public void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedFeatureGates.mutatorsEnabled()) {
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
