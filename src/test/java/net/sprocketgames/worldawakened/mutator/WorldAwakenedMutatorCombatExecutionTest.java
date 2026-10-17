package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.sprocketgames.worldawakened.compat.WorldAwakenedCompatibilityService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.difficulty.WorldAwakenedEffectiveDifficultyScalarService;
import net.sprocketgames.worldawakened.invasion.WorldAwakenedInvasionService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;

class WorldAwakenedMutatorCombatExecutionTest {
    @Test
    void attributesOnlyMobOwnedDamageSources() {
        DamageSource mobDamage = mock(DamageSource.class);
        Mob mob = mock(Mob.class);
        when(mobDamage.getEntity()).thenReturn(mob);
        assertSame(mob, WorldAwakenedMutatorEventHandlers.attackingMob(mobDamage));

        DamageSource playerDamage = mock(DamageSource.class);
        when(playerDamage.getEntity()).thenReturn(mock(Player.class));
        assertNull(WorldAwakenedMutatorEventHandlers.attackingMob(playerDamage));
    }

    @Test
    void deathExplosionGuardRejectsNestedChainsAndClearsAfterFailure() {
        AtomicInteger explosions = new AtomicInteger();
        assertTrue(WorldAwakenedMutatorEventHandlers.runWithDeathExplosionGuard(() -> {
            explosions.incrementAndGet();
            assertFalse(WorldAwakenedMutatorEventHandlers.runWithDeathExplosionGuard(explosions::incrementAndGet));
        }));
        assertEquals(1, explosions.get());

        assertThrows(IllegalStateException.class,
                () -> WorldAwakenedMutatorEventHandlers.runWithDeathExplosionGuard(
                        () -> { throw new IllegalStateException("test"); }));
        assertTrue(WorldAwakenedMutatorEventHandlers.runWithDeathExplosionGuard(explosions::incrementAndGet));
        assertEquals(2, explosions.get());
    }

    @Test
    void overkillCreditsOnlyVictimHealthBeforeDamage() {
        assertEquals(4.0F, WorldAwakenedMutatorEventHandlers.creditedDamage(30.0F, 4.0F));
        assertEquals(3.0F, WorldAwakenedMutatorEventHandlers.creditedDamage(3.0F, 20.0F));
        assertEquals(3.0F, WorldAwakenedMutatorEventHandlers.creditedDamage(3.0F, null));
        assertEquals(0.0F, WorldAwakenedMutatorEventHandlers.creditedDamage(-2.0F, 20.0F));
    }

    @Test
    void successfulDamageAppliesEffectAndHealsFromFinalDamage() {
        CompoundTag state = new CompoundTag();
        JsonObject effect = new JsonObject();
        effect.addProperty("effect", "minecraft:poison");
        effect.addProperty("duration_ticks", 60);
        effect.addProperty("amplifier", 1);
        assertEquals(java.util.Optional.empty(), WorldAwakenedMutationCombatState.appendOnHitEffect(state, effect));
        JsonObject lifeSteal = new JsonObject();
        lifeSteal.addProperty("amount", 0.2D);
        assertEquals(java.util.Optional.empty(), WorldAwakenedMutationCombatState.appendLifeSteal(state, lifeSteal));

        Mob attacker = mock(Mob.class);
        LivingEntity target = mock(LivingEntity.class);
        AtomicReference<Float> health = new AtomicReference<>(10.0F);
        when(attacker.getPersistentData()).thenReturn(state);
        when(attacker.isAlive()).thenReturn(true);
        when(attacker.getHealth()).thenAnswer(ignored -> health.get());
        when(attacker.getMaxHealth()).thenReturn(20.0F);
        doAnswer(invocation -> {
            health.set(Math.min(20.0F, health.get() + invocation.getArgument(0, Float.class)));
            return null;
        }).when(attacker).heal(anyFloat());
        when(target.addEffect(any(MobEffectInstance.class), eq(attacker))).thenReturn(true);

        WorldAwakenedMutatorService.CombatHitResult result = service().onMobDamageDealt(attacker, target, 10.0F);

        assertEquals(2.0F, result.healedAmount());
        assertEquals(ResourceLocation.parse("minecraft:poison"), result.appliedEffects().getFirst());
        ArgumentCaptor<MobEffectInstance> effectCaptor = ArgumentCaptor.forClass(MobEffectInstance.class);
        verify(target).addEffect(effectCaptor.capture(), eq(attacker));
        assertEquals(60, effectCaptor.getValue().getDuration());
        assertEquals(1, effectCaptor.getValue().getAmplifier());
        verify(attacker).heal(2.0F);
    }

    private static WorldAwakenedMutatorService service() {
        return new WorldAwakenedMutatorService(
                mock(WorldAwakenedDatapackService.class),
                mock(WorldAwakenedStageService.class),
                mock(WorldAwakenedEffectiveDifficultyScalarService.class),
                mock(WorldAwakenedInvasionService.class),
                mock(WorldAwakenedCompatibilityService.class));
    }
}
